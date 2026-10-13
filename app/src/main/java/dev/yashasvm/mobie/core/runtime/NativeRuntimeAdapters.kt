package dev.yashasvm.mobie.core.runtime

import android.app.ActivityManager
import dev.yashasvm.mobie.core.device.MemorySnapshotReader
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dev.yashasvm.mobie.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.inferArtifactContextWindow
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun runtimeContextWindowTokens(modelPath: String): Int =
    inferArtifactContextWindow(File(modelPath).name) ?: DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS

/**
 * Deliberately explicit placeholders for the native bridges. Shipping a fake inference path would
 * make compatibility claims unsafe. Wire the audited llama.cpp JNI library here before enabling it.
 */
class GgufRuntimeAdapter : RuntimeAdapter {
    override val format = ModelFormat.GGUF
    override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>) = Result.failure<Unit>(
        IllegalStateException("llama.cpp native library is not bundled in this MVP build"),
    )
    override suspend fun resetConversation(history: List<RuntimeMessage>) = Result.failure<Unit>(
        IllegalStateException("GGUF runtime is not installed"),
    )
    override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> =
        flowOf(InferenceEvent.Error("GGUF runtime is not installed"))
    override suspend fun cancel() = Unit
    override suspend fun unload() = Unit
}

@OptIn(ExperimentalApi::class)
class LiteRtLmRuntimeAdapter(
    context: Context,
    private val computeSettings: RuntimeComputeSettings = RuntimeComputeSettings(context),
) : RuntimeAdapter {
    override val format = ModelFormat.LITERT_LM
    override val supportsImageInput: Boolean get() = visionReady
    override val activeCompute: ActiveCompute? get() = selectedCompute
    override val computeCapabilities: ComputeCapabilities? get() = selectedCapabilities
    override fun configureCompute(options: ComputeOptions) {
        computeOptions = options.forCoreCount(Runtime.getRuntime().availableProcessors())
    }
    override fun configureSampler(sampler: SamplerSettings) { configuredSampler = sampler }
    override fun configureThinking(enabled: Boolean?) { configuredThinking = enabled }
    private val appContext = context.applicationContext
    private val backendFingerprint = "${Build.FINGERPRINT}:${BuildConfig.LITERT_LM_VERSION}"
    private val lifecycle = Mutex()
    private val generation = Mutex()
    private val cancellationState = RuntimeCancellationState()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var visionReady = false
    @Volatile private var computeOptions = ComputeOptions()
    @Volatile private var selectedCompute: ActiveCompute? = null
    @Volatile private var selectedCapabilities: ComputeCapabilities? = null
    private var activeOptions = ComputeOptions()
    private var activeHintTracker: InferenceHintTracker? = null
    private val hintFactory = AndroidInferenceHintFactory(appContext)
    private var contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
    private var committedHistory: List<RuntimeMessage> = emptyList()
    private var conversationDirty = false
    private var nativeConversationHasImage = false
    private var activeSampler = SamplerSettings()
    /** The user's stored sampler, so the first conversation after load does not need a rebuild. */
    @Volatile private var configuredSampler = SamplerSettings()
    @Volatile private var configuredThinking: Boolean? = null
    @Volatile private var cancelRequested = false

    override suspend fun load(
        modelPath: String,
        vision: Boolean,
        history: List<RuntimeMessage>,
    ): Result<Unit> = loadInternal(modelPath, vision, history, allowReclaim = false)

    override suspend fun loadAllowingMemoryReclaim(
        modelPath: String, vision: Boolean, history: List<RuntimeMessage>,
    ): Result<Unit> = loadInternal(modelPath, vision, history, allowReclaim = true)

    private suspend fun loadInternal(
        modelPath: String, vision: Boolean, history: List<RuntimeMessage>, allowReclaim: Boolean,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val transition = cancellationState.beginLifecycleTransition()
        try {
            val cancellationFailure = requestCancellationForLifecycleTransition()
            generation.withLock {
                lifecycle.withLock {
                    recoverableRuntimeResult {
                        if (cancellationFailure != null) {
                            rethrowAfterRuntimeCleanup(cancellationFailure) { closeRuntime() }
                        }
                        closeRuntime()
                        val options = computeOptions
                        val capabilities = ModelInfo.from(modelPath).use { info ->
                            require(info is ModelInfo.Llm) { "This artifact is not a chat model." }
                            ComputeCapabilities(info.maxContextTokens(), info.isDynamicContext(),
                                info.supportsThinking(), info.hasSpeculativeDecodingSupport())
                        }
                        capabilities.validate(options)
                        val configuredContextWindowTokens = capabilities.resolveContext(
                            options.contextTokens, selectRuntimeContextWindowTokens(modelPath),
                        )
                        // Auto always ends with CPU, the cheapest backend, so gate on that; GPU is
                        // admitted separately per attempt in initializeTextBackend.
                        val gateBackend = if (options.backend == ComputeBackend.AUTO) ComputeBackend.CPU else options.backend
                        ensureLoadHeadroom(modelPath, configuredContextWindowTokens, allowReclaim, vision, gateBackend)
                        ExperimentalFlags.enableBenchmark = true
                        val loadedEngine = initializeTextBackend(
                            modelPath = modelPath,
                            vision = vision,
                            contextWindowTokens = configuredContextWindowTokens,
                            options = options,
                            allowReclaim = allowReclaim,
                        )
                        try {
                            contextWindowTokens = configuredContextWindowTokens
                            val restored = ConversationHistoryPolicy.select(history, contextWindowTokens)
                            val sampler = configuredSampler
                            engine = loadedEngine.engine
                            visionReady = loadedEngine.visionReady
                            selectedCompute = loadedEngine.compute
                            selectedCapabilities = capabilities
                            activeOptions = options
                            conversation = loadedEngine.engine.createConversation(
                                conversationConfig(restored, sampler = sampler),
                            )
                            activeSampler = sampler
                            committedHistory = restored
                            nativeConversationHasImage = hasRestorableHistoryImage(restored)
                            conversationDirty = false
                            cancelRequested = false
                            val modelFile = File(modelPath)
                            LiteRtCacheState.markReady(
                                liteRtCacheDirectory(modelFile),
                                modelFile,
                                configuredContextWindowTokens,
                            )
                            if (options.artifactIdentity.isNotBlank() && loadedEngine.rememberPreferred) {
                                computeSettings.memory.remember(options.artifactIdentity, backendFingerprint, loadedEngine.compute.backend)
                            }
                            Unit
                        } catch (error: Throwable) {
                            if (options.artifactIdentity.isNotBlank()) {
                                computeSettings.memory.invalidate(options.artifactIdentity, backendFingerprint)
                            }
                            runRuntimeCleanupUnlessFatal(error) { loadedEngine.engine.close() }
                            engine = null
                            visionReady = false
                            selectedCompute = null
                            selectedCapabilities = null
                            contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
                            committedHistory = emptyList()
                            conversationDirty = false
                            nativeConversationHasImage = false
                            cancelRequested = false
                            throw error
                        }
                    }
                }
            }
        } finally {
            cancellationState.endLifecycleTransition(transition)
        }
    }

    override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> =
        withContext(Dispatchers.Default) {
            val transition = cancellationState.beginLifecycleTransition()
            try {
                val cancellationFailure = requestCancellationForLifecycleTransition()
                generation.withLock {
                    lifecycle.withLock {
                        recoverableRuntimeResult {
                            val activeEngine = engine
                                ?: throw IllegalStateException("Load a model before resetting the conversation")
                            val restored = ConversationHistoryPolicy.select(history, contextWindowTokens)
                            val previous = conversation
                            conversationDirty = true
                            val replaceConversation = {
                                replaceRuntimeResourceBeforeClosingPrevious(
                                    previous = previous,
                                    createReplacement = { activeEngine.createConversation(conversationConfig(restored)) },
                                    installReplacement = { replacement ->
                                        conversation = replacement
                                        committedHistory = restored
                                        nativeConversationHasImage = hasRestorableHistoryImage(restored)
                                        conversationDirty = false
                                        cancelRequested = false
                                    },
                                    closePrevious = { stale -> stale.close() },
                                )
                            }
                            if (cancellationFailure != null) {
                                rethrowAfterRuntimeCleanup(cancellationFailure, replaceConversation)
                            }
                            replaceConversation()
                            Unit
                        }
                    }
                }
            } finally {
                cancellationState.endLifecycleTransition(transition)
            }
        }

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> {
        return flow {
            val generationStartedAt = SystemClock.elapsedRealtime()
            val partialAnswer = StringBuilder()
            val generationPermit = cancellationState.captureGenerationPermit()
            generation.withLock {
                if (!cancellationState.isGenerationPermitValid(generationPermit) || cancelRequested) {
                    cancelRequested = false
                    throw CancellationException("Generation cancelled by a pending lifecycle transition")
                }
                cancellationState.beginGeneration()
                try {
                    cancelRequested = false
                    val requestedSampler = config.sampler.validated()
                    ensureGenerationMemoryHeadroom()
                    if (imagePath != null && !visionReady) {
                        throw IllegalStateException(
                            "Vision initialization failed on this device. The model is still available for text-only chat.",
                        )
                    }
                    if (conversationDirty || requestedSampler != activeSampler || (imagePath != null && nativeConversationHasImage)) {
                        rebuildConversationFromCommittedHistory(
                            restoreHistoryImage = imagePath == null,
                            sampler = requestedSampler,
                        )
                    }
                    var activeConversation = conversation
                        ?: throw IllegalStateException("Load a model before starting a conversation")
                    fun admitOutput(): Int {
                        val occupiedTokens = activeConversation.getTokenCount()
                        return GenerationContextPolicy.maxOutputTokens(
                            contextWindowTokens = contextWindowTokens,
                            history = committedHistory,
                            prompt = prompt,
                            requestedMaxOutputTokens = minOf(config.maxNewTokens, activeOptions.maxOutputTokens),
                            hasImage = imagePath != null,
                            historyHasImage = nativeConversationHasImage,
                            // initialMessages are lazily prefetched by the pinned SDK. Zero occupancy
                            // with nonempty replay history must still budget that pending preface.
                            cachedTokenCount = occupiedTokens.takeIf { it > 0 || committedHistory.isEmpty() },
                        )
                    }
                    val safeMaxOutputTokens = try {
                        admitOutput()
                    } catch (error: IllegalStateException) {
                        // A long next prompt can exhaust a retained cache even below its usual
                        // eviction threshold. Drop cached reasoning/old turns once before rejecting
                        // the prompt, rather than leaving every retry stuck against the same cache.
                        if (activeConversation.getTokenCount() <= 0 || committedHistory.isEmpty()) throw error
                        rebuildConversationFromCommittedHistory(restoreHistoryImage = imagePath == null)
                        activeConversation = checkNotNull(conversation)
                        admitOutput()
                    }
                    val contents = if (imagePath == null) {
                        Contents.of(prompt)
                    } else {
                        Contents.of(Content.Text(prompt), Content.ImageFile(imagePath))
                    }
                    var lastMemoryCheckAt = generationStartedAt
                    var firstTokenAt: Long? = null
                    var firstVisibleOutputAt: Long? = null
                    var secondChunkAt: Long? = null
                    var nonemptyChunkCount = 0
                    var lastProgressAt = 0L
                    var emittedVisibleOutput = false
                    var emittedReasoning = false
                    val hints = InferenceHintTracker(hintFactory.create())
                    activeHintTracker = hints
                    try {
                        currentCoroutineContext().ensureActive()
                        if (cancelRequested) throw CancellationException("Generation cancelled")
                        activeConversation.sendMessageAsync(contents, maxOutputToken = safeMaxOutputTokens).collect { chunk ->
                            currentCoroutineContext().ensureActive()
                            if (cancelRequested) throw CancellationException("Generation cancelled")
                            val now = SystemClock.elapsedRealtime()
                            if (RuntimeLoadMemoryPolicy.shouldRecheckGenerationMemory(lastMemoryCheckAt, now)) {
                                lastMemoryCheckAt = now
                                ensureGenerationMemoryHeadroom()
                            }
                            val split = LiteRtChannelText.split(chunk.channels, REASONING_CHANNELS)
                            val reasoning = split.reasoning
                            val answer = split.visible.ifEmpty { if (reasoning.isEmpty()) chunk.toString() else "" }
                            if (reasoning.isNotEmpty() || answer.isNotEmpty()) {
                                hints.onOutput()
                                nonemptyChunkCount += 1
                                if (firstTokenAt == null) firstTokenAt = now
                                if (nonemptyChunkCount == 2) secondChunkAt = now
                                if (nonemptyChunkCount == 1 || now - lastProgressAt >= 250) {
                                    emit(InferenceEvent.Progress(
                                        sdkChunks = nonemptyChunkCount,
                                        elapsedMs = now - generationStartedAt,
                                        firstChunkMs = checkNotNull(firstTokenAt) - generationStartedAt,
                                    ))
                                    lastProgressAt = now
                                }
                            }
                            if (reasoning.isNotEmpty()) {
                                emittedReasoning = true
                                emit(InferenceEvent.Token(reasoning, thinking = true))
                            }
                            if (answer.isNotEmpty()) {
                                if (firstVisibleOutputAt == null) firstVisibleOutputAt = now
                                emittedVisibleOutput = true
                                partialAnswer.append(answer)
                                emit(InferenceEvent.Token(answer))
                            }
                        }
                        if (cancelRequested) throw CancellationException("Generation cancelled")

                        val generationFinishedAt = SystemClock.elapsedRealtime()
                        val benchmark = activeConversation.getBenchmarkInfo()
                        emit(
                            InferenceEvent.Stats(
                                InferenceStats(
                                    tokensPerSecond = benchmark.lastDecodeTokensPerSecond,
                                    ramBytes = currentAppRamBytes(),
                                    timeToFirstTokenMs = firstTokenAt?.minus(generationStartedAt) ?: 0,
                                    totalGenerationMs = generationFinishedAt - generationStartedAt,
                                    prefillTokensPerSecond = benchmark.lastPrefillTokensPerSecond,
                                    prefillTokenCount = benchmark.lastPrefillTokenCount,
                                    decodeTokenCount = benchmark.lastDecodeTokenCount,
                                    timeToFirstVisibleOutputMs = firstVisibleOutputAt?.minus(generationStartedAt),
                                    timeToSecondChunkMs = secondChunkAt?.minus(generationStartedAt),
                                    compute = selectedCompute,
                                ),
                            ),
                        )
                        if (!emittedVisibleOutput) {
                            rememberInterruptedTurn(prompt, null, imagePath)
                            val message = if (emittedReasoning) {
                                "The model used its output budget for reasoning before producing a final answer. Retry with a larger output limit or disable thinking for this prompt."
                            } else {
                                "The model completed without producing a response."
                            }
                            emit(InferenceEvent.Error(message))
                            return@withLock
                        }
                        if (cancelRequested) throw CancellationException("Generation cancelled")
                        if (imagePath != null) nativeConversationHasImage = true
                        rememberCompletedTurn(prompt, partialAnswer.toString(), imagePath)
                        emit(InferenceEvent.Complete)
                    } catch (error: Throwable) {
                        rethrowFatalRuntimeFailure(error)
                        if (cancellationState.shouldAttemptCleanup()) {
                            cancellationState.attempt { activeConversation.cancelProcess() }
                                ?.let(error::addSuppressed)
                        }
                        rememberInterruptedTurn(prompt, partialAnswer.toString().takeIf { it.isNotBlank() }, imagePath)
                        rethrowNonRecoverableRuntimeFailure(error)
                        emit(InferenceEvent.Error(error.message ?: "Inference failed"))
                    } finally {
                        activeHintTracker = null
                        hints.close()
                    }
                } finally {
                    cancellationState.endGeneration()
                    cancelRequested = false
                }
            }
        }.catch { error ->
            rethrowNonRecoverableRuntimeFailure(error)
            emit(InferenceEvent.Error(error.message ?: "Inference failed"))
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun cancel() = withContext(Dispatchers.Default) {
        val cancellationFailure = requestExplicitCancellation()
        if (cancellationFailure != null) throw cancellationFailure
        Unit
    }

    override suspend fun unload() = withContext(Dispatchers.Default) {
        val transition = cancellationState.beginLifecycleTransition()
        try {
            val cancellationFailure = requestCancellationForLifecycleTransition()
            generation.withLock {
                lifecycle.withLock {
                    if (cancellationFailure != null) {
                        rethrowAfterRuntimeCleanup(cancellationFailure) { closeRuntime() }
                    }
                    closeRuntime()
                }
            }
        } finally {
            cancellationState.endLifecycleTransition(transition)
        }
    }

    private fun requestExplicitCancellation(): Exception? = cancellationState.attempt(
        onActive = {
            cancelRequested = true
            conversationDirty = true
        },
    ) { conversation?.cancelProcess() }

    private fun requestCancellationForLifecycleTransition(): Exception? = cancellationState.attempt(
        onActive = {
            cancelRequested = true
            conversationDirty = true
        },
    ) { conversation?.cancelProcess() }

    private fun initializeEngineWithVisionFallback(
        modelPath: String,
        vision: Boolean,
        contextWindowTokens: Int,
        textBackend: Backend,
        options: ComputeOptions,
    ): LoadedEngine {
        if (!vision) {
            return LoadedEngine(
                initializeEngine(modelPath, textBackend, visionBackend = null, contextWindowTokens = contextWindowTokens, options = options),
                visionReady = false,
            )
        }

        var gpuVisionError: Exception? = null
        if (
            LiteRtVisionBackendPolicy.shouldAttemptGpu(
                supportedAbis = Build.SUPPORTED_ABIS,
                fingerprint = Build.FINGERPRINT,
                model = Build.MODEL,
                hardware = Build.HARDWARE,
                product = Build.PRODUCT,
            )
        ) {
            try {
                return LoadedEngine(
                    initializeEngine(modelPath, textBackend, visionBackend = Backend.GPU(), contextWindowTokens = contextWindowTokens, options = options),
                    visionReady = true,
                )
            } catch (error: Exception) {
                rethrowCancellation(error)
                gpuVisionError = error
            }
        }

        return try {
            LoadedEngine(
                initializeEngine(modelPath, textBackend, visionBackend = Backend.CPU(), contextWindowTokens = contextWindowTokens, options = options),
                visionReady = true,
            )
        } catch (cpuVisionError: Exception) {
            rethrowCancellation(cpuVisionError)
            try {
                LoadedEngine(
                    initializeEngine(modelPath, textBackend, visionBackend = null, contextWindowTokens = contextWindowTokens, options = options),
                    visionReady = false,
                )
            } catch (textOnlyError: Exception) {
                rethrowCancellation(textOnlyError)
                gpuVisionError?.let(textOnlyError::addSuppressed)
                textOnlyError.addSuppressed(cpuVisionError)
                throw textOnlyError
            }
        }
    }

    private fun initializeTextBackend(
        modelPath: String,
        vision: Boolean,
        contextWindowTokens: Int,
        options: ComputeOptions,
        allowReclaim: Boolean,
    ): LoadedEngine {
        val gpuPlausible = LiteRtVisionBackendPolicy.shouldAttemptGpu(
            Build.SUPPORTED_ABIS, Build.FINGERPRINT, Build.MODEL, Build.HARDWARE, Build.PRODUCT,
        )
        val remembered = options.artifactIdentity.takeIf { it.isNotBlank() }
            ?.let { computeSettings.memory.get(it, backendFingerprint) }
        val attempts = ComputeBackendPolicy.attempts(options.backend, gpuPlausible, remembered)
        val admitted = admitAttemptsForMemory(options.backend, attempts) { backend ->
            memoryBlockReason(modelPath, contextWindowTokens, allowReclaim, vision, backend)
        }
        admitted.skippedReason?.let { Log.w("MobieCompute", it) }
        val result = loadWithBackendFallback(
            attempts = admitted.attempts,
            load = { backend ->
                Log.i("MobieCompute", "Loading ${backend.name}; LiteRT-LM ${BuildConfig.LITERT_LM_VERSION}; context=$contextWindowTokens")
                val threads = cpuThreadsFor(backend, options)
                val nativeBackend = when (backend) {
                    ComputeBackend.CPU -> Backend.CPU(threadCount = threads)
                    ComputeBackend.GPU -> Backend.GPU()
                    ComputeBackend.AUTO, ComputeBackend.NPU -> error("Unsupported text backend: $backend")
                }
                // Each failed attempt closes its own engine (see initializeEngine) before the next backend starts.
                try {
                    initializeEngineWithVisionFallback(modelPath, vision, contextWindowTokens, nativeBackend, options)
                } catch (failure: Exception) {
                    rethrowCancellation(failure)
                    val description = describeBackendLoadFailure(backend, failure)
                    if (description == failure.message) throw failure
                    throw IllegalStateException(description, failure)
                }
            },
            onFailure = { backend, failure ->
                Log.w("MobieCompute", "${backend.name} initialization failed", failure)
                if (remembered == backend && options.artifactIdentity.isNotBlank()) {
                    computeSettings.memory.invalidate(options.artifactIdentity, backendFingerprint)
                }
            },
        )
        Log.i("MobieCompute", "Selected ${result.backend.name}; fallback=${result.fallbackReason ?: admitted.skippedReason ?: "none"}")
        return result.value.copy(
            compute = ActiveCompute(
                backend = result.backend,
                cpuThreads = cpuThreadsFor(result.backend, options),
                contextTokens = contextWindowTokens,
                fallbackReason = listOfNotNull(admitted.skippedReason, result.fallbackReason)
                    .joinToString(" ").ifEmpty { null },
            ),
            rememberPreferred = ComputeBackendPolicy.shouldRememberSuccess(options.backend, admitted.attempts, result.backend),
        )
    }

    private fun cpuThreadsFor(backend: ComputeBackend, options: ComputeOptions): Int? =
        if (backend == ComputeBackend.CPU) LiteRtCpuThreadPolicy.threadCount(override = options.cpuThreads) else null

    private fun initializeEngine(modelPath: String, textBackend: Backend, visionBackend: Backend?, contextWindowTokens: Int, options: ComputeOptions): Engine {
        val modelFile = File(modelPath)
        val cacheDirectory = liteRtCacheDirectory(modelFile).apply {
            if (!isDirectory && !mkdirs()) {
                throw IllegalStateException("Could not create the app-private LiteRT cache directory.")
            }
        }
        val createdEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = textBackend,
                visionBackend = visionBackend,
                maxNumTokens = contextWindowTokens,
                maxNumImages = if (visionBackend != null) 1 else null,
                cacheDir = cacheDirectory.absolutePath,
                activationDataType = when (options.activationType) {
                    ActivationType.AUTO -> null
                    ActivationType.FP16 -> ActivationDataType.FLOAT16
                    ActivationType.FP32 -> ActivationDataType.FLOAT32
                },
            ),
        )
        try {
            createdEngine.initialize()
            return createdEngine
        } catch (error: Throwable) {
            runRuntimeCleanupUnlessFatal(error) { createdEngine.close() }
            throw error
        }
    }

    /**
     * Kept beside the model in its app-private directory, not in `cacheDir`: Android may clear
     * `cacheDir` at any time, which would force slow GPU/XNNPACK rebuilds, and a per-model location
     * is deleted together with the model instead of leaking storage.
     */
    private fun liteRtCacheDirectory(modelFile: File): File =
        File(modelFile.absoluteFile.parentFile, LITERT_CACHE_DIRECTORY)

    private fun rebuildConversationFromCommittedHistory(
        restoreHistoryImage: Boolean = true,
        sampler: SamplerSettings = activeSampler,
    ) {
        val activeEngine = engine
            ?: throw IllegalStateException("Load a model before rebuilding the conversation")
        // A live cache can retain more text than the conservative replay byte allowance. A new
        // conversation loses its measured occupancy, so bound the actual pending preface again.
        val restored = ConversationHistoryPolicy.select(committedHistory, contextWindowTokens)
        val previous = conversation
        replaceRuntimeResourceBeforeClosingPrevious(
            previous = previous,
            createReplacement = {
                activeEngine.createConversation(
                    conversationConfig(restored, restoreHistoryImage = restoreHistoryImage, sampler = sampler),
                )
            },
            installReplacement = { replacement ->
                conversation = replacement
                committedHistory = restored
                nativeConversationHasImage = restoreHistoryImage && hasRestorableHistoryImage(restored)
                conversationDirty = false
                activeSampler = sampler
            },
            closePrevious = { stale -> stale.close() },
        )
    }

    private fun rememberCompletedTurn(prompt: String, answer: String, imagePath: String?) {
        val replay = ConversationHistoryPolicy.afterCompletedTurn(
            committedHistory = committedHistory,
            prompt = prompt,
            answer = answer,
            imagePath = imagePath,
            contextWindowTokens = contextWindowTokens,
            cachedTokenCount = conversation?.getTokenCount(),
        )
        committedHistory = replay.history
        conversationDirty = replay.nativeConversationMustRebuild
    }

    private fun rememberInterruptedTurn(prompt: String, partialAnswer: String?, imagePath: String?) {
        committedHistory = ConversationHistoryPolicy.afterInterruptedTurn(
            committedHistory = committedHistory,
            prompt = prompt,
            partialAnswer = partialAnswer,
            imagePath = imagePath,
            contextWindowTokens = contextWindowTokens,
        )
        conversationDirty = true
    }

    private fun hasRestorableHistoryImage(history: List<RuntimeMessage>): Boolean =
        VisionHistoryPolicy.latestUsableImageIndex(
            history = history,
            visionReady = visionReady,
        ) { path -> File(path).let { it.isFile && it.canRead() } } >= 0

    private fun conversationConfig(
        history: List<RuntimeMessage>,
        restoreHistoryImage: Boolean = true,
        sampler: SamplerSettings = activeSampler,
    ): ConversationConfig {
        val restoredImageIndex = if (restoreHistoryImage) {
            VisionHistoryPolicy.latestUsableImageIndex(
                history = history,
                visionReady = visionReady,
            ) { path -> File(path).let { it.isFile && it.canRead() } }
        } else {
            -1
        }
        val restored = history.mapIndexed { index, message ->
            if (!message.fromUser) {
                Message.model(message.text)
            } else if (index == restoredImageIndex && message.imagePath != null) {
                Message.user(
                    Contents.of(
                        Content.Text(message.text),
                        Content.ImageFile(message.imagePath),
                    ),
                )
            } else {
                Message.user(message.text)
            }
        }
        return ConversationConfig(
            initialMessages = restored,
            thinkingConfig = (activeOptions.thinkingEnabled ?: configuredThinking)?.let {
                ThinkingConfig(enableThinking = it, thinkingTokenBudget = activeOptions.thinkingTokenBudget ?: -1)
            } ?: activeOptions.thinkingTokenBudget?.let { ThinkingConfig(thinkingTokenBudget = it) },
            samplerConfig = SamplerConfig(
                topK = sampler.topK,
                topP = sampler.topP,
                temperature = sampler.temperature,
                seed = sampler.seed,
            ),
            maxOutputToken = activeOptions.maxOutputTokens,
            enableSpeculativeDecoding = activeOptions.speculativeDecoding,
            prefillPrefaceOnInit = activeOptions.prefillPrefaceOnInit && restored.isNotEmpty(),
        )
    }

    private fun selectRuntimeContextWindowTokens(modelPath: String): Int {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val memory = MemorySnapshotReader(manager).read()
        val modelFile = File(modelPath)
        return LiteRtContextWindowPolicy.select(
            advertisedContextWindowTokens = runtimeContextWindowTokens(modelPath),
            modelWeightsBytes = modelFile.length(),
            totalRamBytes = memory.totalBytes,
            availableRamBytes = memory.availableNowBytes,
            lowMemoryThresholdBytes = memory.thresholdBytes,
            isLowRamDevice = manager.isLowRamDevice,
        )
    }

    private fun ensureLoadHeadroom(
        modelPath: String,
        contextWindowTokens: Int,
        allowReclaim: Boolean,
        vision: Boolean,
        backend: ComputeBackend,
    ) {
        val memoryReason = memoryBlockReason(modelPath, contextWindowTokens, allowReclaim, vision, backend)
        if (memoryReason != null) throw IllegalStateException(memoryReason)

        val modelFile = File(modelPath)
        val reusableCache = LiteRtCacheState.canReuse(
            liteRtCacheDirectory(modelFile),
            modelFile,
            contextWindowTokens,
        )
        val storageReason = RuntimeLoadStoragePolicy.blockReason(
            modelWeightsBytes = modelFile.length(),
            availableStorageBytes = modelFile.absoluteFile.parentFile?.usableSpace ?: -1,
            reusableCache = reusableCache,
        )
        if (storageReason != null) throw IllegalStateException(storageReason)
    }

    private fun memoryBlockReason(
        modelPath: String,
        contextWindowTokens: Int,
        allowReclaim: Boolean,
        vision: Boolean,
        backend: ComputeBackend,
    ): String? {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val memory = MemorySnapshotReader(manager).read()
        return RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = File(modelPath).length(),
            totalRamBytes = memory.totalBytes,
            availableRamBytes = memory.availableNowBytes,
            availableAfterReclaimBytes = memory.availableAfterReclaimBytes,
            lowMemoryThresholdBytes = memory.thresholdBytes,
            isLowMemory = memory.isLowMemory,
            isLowRamDevice = manager.isLowRamDevice,
            thermalStatus = currentThermalStatus(),
            contextWindowTokens = contextWindowTokens,
            allowReclaim = allowReclaim,
            visionEncoderLoaded = vision,
            backend = backend,
        )
    }


    private fun ensureGenerationMemoryHeadroom() {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val reason = RuntimeLoadMemoryPolicy.generationBlockReason(
            isLowMemory = memory.lowMemory,
            thermalStatus = currentThermalStatus(),
            availableRamBytes = memory.availMem,
            lowMemoryThresholdBytes = memory.threshold,
            totalRamBytes = memory.totalMem,
        )
        if (reason != null) throw IllegalStateException(reason)
    }

    private fun currentThermalStatus(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appContext.getSystemService(PowerManager::class.java).currentThermalStatus
    } else {
        PowerManager.THERMAL_STATUS_NONE
    }

    private fun closeRuntime() {
        val previousConversation = conversation
        val previousEngine = engine
        conversation = null
        engine = null
        visionReady = false
        selectedCompute = null
        selectedCapabilities = null
        activeOptions = ComputeOptions()
        contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
        committedHistory = emptyList()
        conversationDirty = false
        nativeConversationHasImage = false
        cancelRequested = false
        cancellationState.reset()
        runAllRuntimeCleanup(
            { activeHintTracker?.close(); activeHintTracker = null },
            { previousConversation?.close() },
            { previousEngine?.close() },
        )
    }

    /** Debug.getPss() reads this process directly; ActivityManager's per-process PSS is rate-limited and can be stale. */
    private fun currentAppRamBytes(): Long = Debug.getPss() * 1024L

    private fun rethrowCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }

    private data class LoadedEngine(
        val engine: Engine,
        val visionReady: Boolean,
        val compute: ActiveCompute = ActiveCompute(ComputeBackend.CPU),
        /** Only a first-choice GPU success under Auto is remembered; a fallback is not a preference. */
        val rememberPreferred: Boolean = false,
    )

    private companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1_024
        const val LITERT_CACHE_DIRECTORY = ".litert-cache"
        val REASONING_CHANNELS = setOf(
            "analysis", "thinking", "reasoning", "reasoning_content", "thought", "thoughts",
            "deliberation", "scratchpad", "chain_of_thought", "chain-of-thought", "cot",
        )
    }
}

private const val DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS = 4_096
