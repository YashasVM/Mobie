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

    override suspend fun load(
        modelPath: String,
        vision: Boolean,
        history: List<RuntimeMessage>,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        cancel()
        generation.withLock {
            lifecycle.withLock {
                runCatching {
                    ensureLoadMemoryHeadroom(modelPath)
                    closeRuntime()
                    ExperimentalFlags.enableBenchmark = true
                    val loadedEngine = initializeEngineWithVisionFallback(modelPath, vision)
                    try {
                        engine = loadedEngine.engine
                        visionReady = loadedEngine.visionReady
                        conversation = loadedEngine.engine.createConversation(conversationConfig(history))
                    } catch (error: Throwable) {
                        loadedEngine.engine.close()
                        engine = null
                        visionReady = false
                        throw error
                    }
                }.onFailure(::rethrowCancellation)
            }
        }
    }

    override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> =
        withContext(Dispatchers.Default) {
            cancel()
            generation.withLock {
                lifecycle.withLock {
                    runCatching {
                        val activeEngine = engine
                            ?: throw IllegalStateException("Load a model before resetting the conversation")
                        val replacement = activeEngine.createConversation(conversationConfig(history))
                        val previous = conversation
                        conversation = replacement
                        previous?.close()
                        Unit
                    }.onFailure(::rethrowCancellation)
                }
            }
        }

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> = flow {
        generation.withLock {
            ensureGenerationMemoryHeadroom()
            val activeConversation = conversation
                ?: throw IllegalStateException("Load a model before starting a conversation")
            if (imagePath != null && !visionReady) {
                throw IllegalStateException(
                    "Vision initialization failed on this device. The model is still available for text-only chat.",
                )
            }
            val contents = if (imagePath == null) {
                Contents.of(prompt)
            } else {
                // LiteRT-LM's multimodal guidance expects text before media content.
                Contents.of(Content.Text(prompt), Content.ImageFile(imagePath))
            }
            val generationStartedAt = SystemClock.elapsedRealtime()
            var lastMemoryCheckAt = generationStartedAt
            var firstTokenAt: Long? = null
            var emittedVisibleOutput = false
            var emittedReasoning = false
            activeConversation.sendMessageAsync(contents, maxOutputToken = config.maxNewTokens).collect { chunk ->
                currentCoroutineContext().ensureActive()
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
                    emit(InferenceEvent.Token(answer))
                }
            }
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
                val message = if (emittedReasoning) {
                    "The model used its output budget for reasoning before producing a final answer. Retry with a larger output limit or disable thinking for this prompt."
                } else {
                    "The model completed without producing a response."
                }
                emit(InferenceEvent.Error(message))
                return@withLock
            }
            emit(InferenceEvent.Complete)
        }
    }.catch { error ->
        rethrowCancellation(error)
        emit(InferenceEvent.Error(error.message ?: "Inference failed"))
    }.flowOn(Dispatchers.Default)

    override suspend fun cancel() = withContext(Dispatchers.Default) {
        conversation?.cancelProcess()
        Unit
    }

    override suspend fun unload() = withContext(Dispatchers.Default) {
        cancel()
        generation.withLock { lifecycle.withLock { closeRuntime() } }
    }

    private fun initializeEngineWithVisionFallback(modelPath: String, vision: Boolean): LoadedEngine {
        if (!vision) return LoadedEngine(initializeEngine(modelPath, visionBackend = null), visionReady = false)

        // Keep text generation on the conservative CPU backend, but prefer GPU for the vision
        // encoder. On supported Android GPUs this materially improves multimodal prefill and also
        // avoids known LiteRT-LM CPU-vision instability. Devices without a working GPU delegate
        // transparently fall back to CPU vision instead of losing multimodal support entirely.
        return try {
            LoadedEngine(initializeEngine(modelPath, visionBackend = Backend.GPU()), visionReady = true)
        } catch (gpuVisionError: Throwable) {
            rethrowCancellation(gpuVisionError)
            try {
                LoadedEngine(initializeEngine(modelPath, visionBackend = Backend.CPU()), visionReady = true)
            } catch (cpuVisionError: Throwable) {
                rethrowCancellation(cpuVisionError)
                try {
                    LoadedEngine(initializeEngine(modelPath, visionBackend = null), visionReady = false)
                } catch (textOnlyError: Throwable) {
                    rethrowCancellation(textOnlyError)
                    textOnlyError.addSuppressed(gpuVisionError)
                    textOnlyError.addSuppressed(cpuVisionError)
                    throw textOnlyError
                }
            }
        }
    }

    private fun initializeEngine(modelPath: String, visionBackend: Backend?): Engine {
        val createdEngine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = visionBackend,
                maxNumImages = if (visionBackend != null) 1 else null,
                cacheDir = appContext.cacheDir.absolutePath,
            ),
        )
        try {
            createdEngine.initialize()
            return createdEngine
        } catch (error: Throwable) {
            // Engine.close() requires successful initialization, so only close initialized engines.
            if (createdEngine.isInitialized()) createdEngine.close()
            throw error
        }
    }

    private fun conversationConfig(history: List<RuntimeMessage>): ConversationConfig {
        val restored = ConversationHistoryPolicy.select(history).map {
            if (it.fromUser) Message.user(it.text) else Message.model(it.text)
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

    private fun ensureLoadMemoryHeadroom(modelPath: String) {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val reason = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = File(modelPath).length(),
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            lowMemoryThresholdBytes = memory.threshold,
            isLowMemory = memory.lowMemory,
            isLowRamDevice = manager.isLowRamDevice,
            thermalStatus = currentThermalStatus(),
        )
        if (reason != null) throw IllegalStateException(reason)
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
        val REASONING_CHANNELS = setOf(
            "analysis", "thinking", "reasoning", "reasoning_content", "thought", "thoughts",
            "deliberation", "scratchpad", "chain_of_thought", "chain-of-thought", "cot",
        )
    }
}
