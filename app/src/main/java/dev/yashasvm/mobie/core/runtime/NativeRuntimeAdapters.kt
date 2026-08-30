package dev.yashasvm.mobie.core.runtime

import android.app.ActivityManager
import android.content.Context
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

    override suspend fun load(
        modelPath: String,
        vision: Boolean,
        history: List<RuntimeMessage>,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        cancel()
        generation.withLock {
            lifecycle.withLock {
                runCatching {
                closeRuntime()
                ExperimentalFlags.enableBenchmark = true
                val createdEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = if (vision) Backend.CPU() else null,
                        cacheDir = appContext.cacheDir.absolutePath,
                    ),
                )
                try {
                    createdEngine.initialize()
                    engine = createdEngine
                    // ponytail: cap restored context; add token-aware trimming when LiteRT exposes cheap counts pre-load.
                    val restored = history.takeLast(20).filter { it.text.isNotBlank() }.map {
                        if (it.fromUser) Message.user(it.text) else Message.model(it.text)
                    }
                    conversation = createdEngine.createConversation(
                        ConversationConfig(
                            initialMessages = restored,
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.95,
                                temperature = 0.7,
                                seed = 0,
                            ),
                            maxOutputToken = DEFAULT_MAX_OUTPUT_TOKENS,
                        ),
                    )
                } catch (error: Throwable) {
                    createdEngine.close()
                    throw error
                }
                }
            }
        }
    }

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> = flow {
        generation.withLock {
            val activeConversation = conversation
                ?: throw IllegalStateException("Load a model before starting a conversation")
            val contents = if (imagePath == null) {
                Contents.of(prompt)
            } else {
                Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))
            }
            activeConversation.sendMessageAsync(contents, maxOutputToken = config.maxNewTokens).collect { chunk ->
                currentCoroutineContext().ensureActive()
                val reasoning = chunk.channels.entries
                    .filter { (name, _) -> name.lowercase() in REASONING_CHANNELS }
                    .joinToString("") { it.value }
                val visibleChannels = chunk.channels.entries
                    .filterNot { (name, _) -> name.lowercase() in REASONING_CHANNELS }
                    .joinToString("") { it.value }
                val answer = visibleChannels.ifEmpty { if (reasoning.isEmpty()) chunk.toString() else "" }
                if (reasoning.isNotEmpty()) emit(InferenceEvent.Token(reasoning, thinking = true))
                if (answer.isNotEmpty()) emit(InferenceEvent.Token(answer))
            }
            val benchmark = activeConversation.getBenchmarkInfo()
            emit(
                InferenceEvent.Stats(
                    InferenceStats(
                        tokensPerSecond = benchmark.lastDecodeTokensPerSecond,
                        ramBytes = currentAppRamBytes(),
                    ),
                ),
            )
            emit(InferenceEvent.Complete)
        }
    }.catch { error ->
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

    private fun closeRuntime() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }

    private fun currentAppRamBytes(): Long {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        return manager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            .firstOrNull()?.totalPss?.toLong()?.times(1024L) ?: 0L
    }

    private companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 256
        val REASONING_CHANNELS = setOf(
            "analysis", "thinking", "reasoning", "reasoning_content", "thought", "thoughts",
            "deliberation", "scratchpad", "chain_of_thought", "chain-of-thought", "cot",
        )
    }
}
