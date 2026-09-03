package dev.yashasvm.mobie.core.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
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
class LiteRtLmRuntimeAdapter(context: Context) : RuntimeAdapter {
    override val format = ModelFormat.LITERT_LM
    private val appContext = context.applicationContext
    private val lifecycle = Mutex()
    private val generation = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var visionReady = false
    private var contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
    private var committedHistory: List<RuntimeMessage> = emptyList()
    private var conversationDirty = false
    @Volatile private var cancelRequested = false

    override suspend fun load(
        modelPath: String,
        vision: Boolean,
        history: List<RuntimeMessage>,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        cancel()
        generation.withLock {
            lifecycle.withLock {
                recoverableRuntimeResult {
                    // Replacement loads must be admitted after the previous native engine is gone.
                    // Keeping the old engine alive here can falsely reject the next model and strand stale RAM.
                    closeRuntime()
                    ensureLoadHeadroom(modelPath)
                    ExperimentalFlags.enableBenchmark = true
                    val configuredContextWindowTokens = runtimeContextWindowTokens(modelPath)
                    val loadedEngine = initializeEngineWithVisionFallback(modelPath, vision)
                    try {
                        contextWindowTokens = configuredContextWindowTokens
                        val restored = ConversationHistoryPolicy.select(history, contextWindowTokens)
                        engine = loadedEngine.engine
                        visionReady = loadedEngine.visionReady
                        conversation = loadedEngine.engine.createConversation(conversationConfig(restored))
                        committedHistory = restored
                        conversationDirty = false
                        cancelRequested = false
                    } catch (error: Throwable) {
                        loadedEngine.engine.close()
                        engine = null
                        visionReady = false
                        contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
                        committedHistory = emptyList()
                        conversationDirty = false
                        cancelRequested = false
                        throw error
                    }
                }
            }
        }
    }

    override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> =
        withContext(Dispatchers.Default) {
            cancel()
            generation.withLock {
                lifecycle.withLock {
                    recoverableRuntimeResult {
                        val activeEngine = engine
                            ?: throw IllegalStateException("Load a model before resetting the conversation")
                        val restored = ConversationHistoryPolicy.select(history, contextWindowTokens)
                        val previous = conversation
                        conversation = null
                        previous?.close()
                        conversationDirty = true
                        val replacement = activeEngine.createConversation(conversationConfig(restored))
                        conversation = replacement
                        committedHistory = restored
                        conversationDirty = false
                        cancelRequested = false
                        Unit
                    }
                }
            }
        }

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> {
        val partialAnswer = StringBuilder()
        return flow {
            generation.withLock {
                cancelRequested = false
                ensureGenerationMemoryHeadroom()
                if (conversationDirty) rebuildConversationFromCommittedHistory()
                val activeConversation = conversation
                    ?: throw IllegalStateException("Load a model before starting a conversation")
                if (imagePath != null && !visionReady) {
                    throw IllegalStateException(
                        "Vision initialization failed on this device. The model is still available for text-only chat.",
                    )
                }
                val safeMaxOutputTokens = GenerationContextPolicy.maxOutputTokens(
                    contextWindowTokens = contextWindowTokens,
                    history = committedHistory,
                    prompt = prompt,
                    requestedMaxOutputTokens = config.maxNewTokens,
                    hasImage = imagePath != null,
                )
                val contents = if (imagePath == null) {
                    Contents.of(prompt)
                } else {
                    Contents.of(Content.Text(prompt), Content.ImageFile(imagePath))
                }
                val generationStartedAt = SystemClock.elapsedRealtime()
                var lastMemoryCheckAt = generationStartedAt
                var firstTokenAt: Long? = null
                var emittedVisibleOutput = false
                var emittedReasoning = false
                try {
                    activeConversation.sendMessageAsync(contents, maxOutputToken = safeMaxOutputTokens).collect { chunk ->
                        currentCoroutineContext().ensureActive()
                        if (cancelRequested) throw CancellationException("Generation cancelled")
                        val now = SystemClock.elapsedRealtime()
                        if (RuntimeLoadMemoryPolicy.shouldRecheckGenerationMemory(lastMemoryCheckAt, now)) {
                            lastMemoryCheckAt = now
                            try {
                                ensureGenerationMemoryHeadroom()
                            } catch (error: IllegalStateException) {
                                activeConversation.cancelProcess()
                                throw error
                            }
                        }
                        val reasoning = chunk.channels.entries
                            .filter { (name, _) -> name.lowercase() in REASONING_CHANNELS }
                            .joinToString("") { it.value }
                        val visibleChannels = chunk.channels.entries
                            .filterNot { (name, _) -> name.lowercase() in REASONING_CHANNELS }
                            .joinToString("") { it.value }
                        val answer = visibleChannels.ifEmpty { if (reasoning.isEmpty()) chunk.toString() else "" }
                        if (reasoning.isNotEmpty()) {
                            if (firstTokenAt == null) firstTokenAt = SystemClock.elapsedRealtime()
                            emittedReasoning = true
                            emit(InferenceEvent.Token(reasoning, thinking = true))
                        }
                        if (answer.isNotEmpty()) {
                            if (firstTokenAt == null) firstTokenAt = SystemClock.elapsedRealtime()
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
                    rememberCompletedTurn(prompt, partialAnswer.toString(), imagePath)
                    emit(InferenceEvent.Complete)
                } catch (error: Throwable) {
                    rethrowFatalRuntimeFailure(error)
                    activeConversation.cancelProcess()
                    rememberInterruptedTurn(prompt, partialAnswer.toString().takeIf { it.isNotBlank() }, imagePath)
                    rethrowNonRecoverableRuntimeFailure(error)
                    emit(InferenceEvent.Error(error.message ?: "Inference failed"))
                }
            }
        }.catch { error ->
            rethrowNonRecoverableRuntimeFailure(error)
            emit(InferenceEvent.Error(error.message ?: "Inference failed"))
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun cancel() = withContext(Dispatchers.Default) {
        cancelRequested = true
        conversationDirty = true
        conversation?.cancelProcess()
        Unit
    }

    override suspend fun unload() = withContext(Dispatchers.Default) {
        cancel()
        generation.withLock { lifecycle.withLock { closeRuntime() } }
    }

    private fun initializeEngineWithVisionFallback(modelPath: String, vision: Boolean): LoadedEngine {
        if (!vision) return LoadedEngine(initializeEngine(modelPath, visionBackend = null), visionReady = false)

        return try {
            LoadedEngine(initializeEngine(modelPath, visionBackend = Backend.GPU()), visionReady = true)
        } catch (gpuVisionError: Exception) {
            rethrowCancellation(gpuVisionError)
            try {
                LoadedEngine(initializeEngine(modelPath, visionBackend = Backend.CPU()), visionReady = true)
            } catch (cpuVisionError: Exception) {
                rethrowCancellation(cpuVisionError)
                try {
                    LoadedEngine(initializeEngine(modelPath, visionBackend = null), visionReady = false)
                } catch (textOnlyError: Exception) {
                    rethrowCancellation(textOnlyError)
                    textOnlyError.addSuppressed(gpuVisionError)
                    textOnlyError.addSuppressed(cpuVisionError)
                    throw textOnlyError
                }
            }
        }
    }

    private fun initializeEngine(modelPath: String, visionBackend: Backend?): Engine {
        val cacheDirectory = File(File(modelPath).absoluteFile.parentFile, LITERT_CACHE_DIRECTORY).apply {
            if (!isDirectory && !mkdirs()) {
                throw IllegalStateException("Could not create LiteRT cache directory beside the installed model.")
            }
        }
        val createdEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = visionBackend,
                maxNumTokens = runtimeContextWindowTokens(modelPath),
                maxNumImages = if (visionBackend != null) 1 else null,
                cacheDir = cacheDirectory.absolutePath,
            ),
        )
        try {
            createdEngine.initialize()
            return createdEngine
        } catch (error: Throwable) {
            if (createdEngine.isInitialized()) createdEngine.close()
            throw error
        }
    }

    private fun rebuildConversationFromCommittedHistory() {
        val activeEngine = engine
            ?: throw IllegalStateException("Load a model before rebuilding the conversation")
        val previous = conversation
        conversation = null
        previous?.close()
        conversationDirty = true
        val replacement = activeEngine.createConversation(conversationConfig(committedHistory))
        conversation = replacement
        conversationDirty = false
    }

    private fun rememberCompletedTurn(prompt: String, answer: String, imagePath: String?) {
        val replay = ConversationHistoryPolicy.afterCompletedTurn(
            committedHistory = committedHistory,
            prompt = prompt,
            answer = answer,
            imagePath = imagePath,
            contextWindowTokens = contextWindowTokens,
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

    private fun conversationConfig(history: List<RuntimeMessage>): ConversationConfig {
        val selected = ConversationHistoryPolicy.select(history, contextWindowTokens)
        val restoredImageIndex = VisionHistoryPolicy.latestUsableImageIndex(
            history = selected,
            visionReady = visionReady,
        ) { path ->
            File(path).let { it.isFile && it.canRead() }
        }
        val restored = selected.mapIndexed { index, message ->
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
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = 0.7,
                seed = 0,
            ),
            maxOutputToken = DEFAULT_MAX_OUTPUT_TOKENS,
        )
    }

    private fun ensureLoadHeadroom(modelPath: String) {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val modelFile = File(modelPath)
        val contextWindowTokens = runtimeContextWindowTokens(modelPath)
        val memoryReason = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = modelFile.length(),
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            lowMemoryThresholdBytes = memory.threshold,
            isLowMemory = memory.lowMemory,
            isLowRamDevice = manager.isLowRamDevice,
            thermalStatus = currentThermalStatus(),
            contextWindowTokens = contextWindowTokens,
        )
        if (memoryReason != null) throw IllegalStateException(memoryReason)

        val storageReason = RuntimeLoadStoragePolicy.blockReason(
            modelWeightsBytes = modelFile.length(),
            availableStorageBytes = modelFile.absoluteFile.parentFile?.usableSpace ?: -1,
        )
        if (storageReason != null) throw IllegalStateException(storageReason)
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
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        visionReady = false
        contextWindowTokens = DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS
        committedHistory = emptyList()
        conversationDirty = false
        cancelRequested = false
    }

    private fun currentAppRamBytes(): Long {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        return manager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            .firstOrNull()?.totalPss?.toLong()?.times(1024L) ?: 0L
    }

    private fun rethrowCancellation(error: Throwable) {
        if (error is CancellationException) throw error
    }

    private data class LoadedEngine(
        val engine: Engine,
        val visionReady: Boolean,
    )

    private companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 256
        const val LITERT_CACHE_DIRECTORY = ".litert-cache"
        val REASONING_CHANNELS = setOf(
            "analysis", "thinking", "reasoning", "reasoning_content", "thought", "thoughts",
            "deliberation", "scratchpad", "chain_of_thought", "chain-of-thought", "cot",
        )
    }
}

private const val DEFAULT_LITERT_CONTEXT_WINDOW_TOKENS = 4_096