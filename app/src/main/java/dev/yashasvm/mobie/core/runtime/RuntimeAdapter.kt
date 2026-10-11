package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow

data class GenerationConfig(
    val maxNewTokens: Int = 1_024,
    val sampler: SamplerSettings = SamplerSettings(),
)

data class RuntimeMessage(
    val fromUser: Boolean,
    val text: String,
    val interrupted: Boolean = false,
    val imagePath: String? = null,
)

data class InferenceStats(
    val tokensPerSecond: Double,
    val ramBytes: Long,
    val timeToFirstTokenMs: Long = 0,
    val totalGenerationMs: Long = 0,
    val prefillTokensPerSecond: Double = 0.0,
    val prefillTokenCount: Int = 0,
    val decodeTokenCount: Int = 0,
    val timeToFirstVisibleOutputMs: Long? = null,
    /** SDK streaming callbacks may contain more than one token. This measures the second nonempty chunk. */
    val timeToSecondChunkMs: Long? = null,
)

sealed interface InferenceEvent {
    data class Token(val text: String, val thinking: Boolean = false) : InferenceEvent
    /** Live callbacks, not tokenizer tokens. */
    data class Progress(val sdkChunks: Int, val elapsedMs: Long, val firstChunkMs: Long) : InferenceEvent
    data class Stats(val value: InferenceStats) : InferenceEvent
    data class Error(
        val message: String,
        /** Native/runtime ownership is uncertain; do not accept another prompt until reload. */
        val requiresReload: Boolean = false,
    ) : InferenceEvent
    data object Complete : InferenceEvent
}

interface RuntimeAdapter {
    val format: ModelFormat
    /** True only while the loaded runtime has an initialized image encoder. */
    val supportsImageInput: Boolean get() = false
    suspend fun load(
        modelPath: String,
        vision: Boolean = false,
        history: List<RuntimeMessage> = emptyList(),
    ): Result<Unit>
    suspend fun resetConversation(history: List<RuntimeMessage> = emptyList()): Result<Unit>
    fun generate(
        prompt: String,
        imagePath: String? = null,
        config: GenerationConfig = GenerationConfig(),
    ): Flow<InferenceEvent>
    suspend fun cancel()
    suspend fun unload()
}
