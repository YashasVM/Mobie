package dev.yashasvm.mobie.core.runtime

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.Dispatchers
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
    override suspend fun load(modelPath: String, vision: Boolean) = Result.failure<Unit>(
        IllegalStateException("llama.cpp native library is not bundled in this MVP build"),
    )
    override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> =
        flowOf(InferenceEvent.Error("GGUF runtime is not installed"))
    override suspend fun unload() = Unit
}

@OptIn(ExperimentalApi::class)
class LiteRtLmRuntimeAdapter(context: Context) : RuntimeAdapter {
    override val format = ModelFormat.LITERT_LM
    private val appContext = context.applicationContext
    private val lifecycle = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun load(modelPath: String, vision: Boolean): Result<Unit> = withContext(Dispatchers.Default) {
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
                    conversation = createdEngine.createConversation()
                } catch (error: Throwable) {
                    createdEngine.close()
                    throw error
                }
            }
        }
    }

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> = flow {
        val activeConversation = conversation
            ?: throw IllegalStateException("Load a model before starting a conversation")
        val contents = if (imagePath == null) {
            Contents.of(prompt)
        } else {
            Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))
        }
        activeConversation.sendMessageAsync(contents, maxOutputToken = config.maxNewTokens).collect { chunk ->
            val text = chunk.toString()
            if (text.isNotEmpty()) emit(InferenceEvent.Token(text))
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
    }.catch { error ->
        emit(InferenceEvent.Error(error.message ?: "Inference failed"))
    }.flowOn(Dispatchers.Default)

    override suspend fun unload() = withContext(Dispatchers.Default) {
        lifecycle.withLock { closeRuntime() }
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
}
