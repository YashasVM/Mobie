package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow

data class GenerationConfig(
    val maxNewTokens: Int = 256,
)

data class RuntimeMessage(val fromUser: Boolean, val text: String)

data class InferenceStats(
    val tokensPerSecond: Double,
    val ramBytes: Long,
)

sealed interface InferenceEvent {
    data class Token(val text: String) : InferenceEvent
    data class Stats(val value: InferenceStats) : InferenceEvent
    data class Error(val message: String) : InferenceEvent
    data object Complete : InferenceEvent
}

interface RuntimeAdapter {
    val format: ModelFormat
    suspend fun load(
        modelPath: String,
        vision: Boolean = false,
        history: List<RuntimeMessage> = emptyList(),
    ): Result<Unit>
    fun generate(
        prompt: String,
        imagePath: String? = null,
        config: GenerationConfig = GenerationConfig(),
    ): Flow<InferenceEvent>
    suspend fun cancel()
    suspend fun unload()
}
