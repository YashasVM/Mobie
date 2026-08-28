package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deliberately explicit placeholders for the native bridges. Shipping a fake inference path would
 * make compatibility claims unsafe. Wire the audited llama.cpp JNI library here before enabling it.
 */
class GgufRuntimeAdapter : RuntimeAdapter {
    override val format = ModelFormat.GGUF
    override suspend fun load(modelPath: String) = Result.failure<Unit>(
        IllegalStateException("llama.cpp native library is not bundled in this MVP build"),
    )
    override fun generate(prompt: String, config: GenerationConfig): Flow<InferenceEvent> =
        flowOf(InferenceEvent.Error("GGUF runtime is not installed"))
    override suspend fun unload() = Unit
}

class LiteRtLmRuntimeAdapter : RuntimeAdapter {
    override val format = ModelFormat.LITERT_LM
    override suspend fun load(modelPath: String) = Result.failure<Unit>(
        IllegalStateException("LiteRT-LM native library is not bundled in this MVP build"),
    )
    override fun generate(prompt: String, config: GenerationConfig): Flow<InferenceEvent> =
        flowOf(InferenceEvent.Error("LiteRT-LM runtime is not installed"))
    override suspend fun unload() = Unit
}
