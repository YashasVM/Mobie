package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class ThermalGuardRuntimeAdapter(
    private val delegate: RuntimeAdapter,
    private val thermalStatusProvider: () -> Int,
) : RuntimeAdapter {
    override val format: ModelFormat = delegate.format

    override suspend fun load(
        modelPath: String,
        vision: Boolean,
        history: List<RuntimeMessage>,
    ): Result<Unit> = delegate.load(modelPath, vision, history)

    override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> =
        delegate.resetConversation(history)

    override fun generate(
        prompt: String,
        imagePath: String?,
        config: GenerationConfig,
    ): Flow<InferenceEvent> {
        val decision = ThermalInferencePolicy.decide(
            thermalStatus = thermalStatusProvider(),
            requestedMaxNewTokens = config.maxNewTokens,
        )
        if (!decision.allowed) {
            return flowOf(InferenceEvent.Error(decision.errorMessage ?: "Thermal limit reached"))
        }
        return delegate.generate(
            prompt = prompt,
            imagePath = imagePath,
            config = config.copy(maxNewTokens = decision.maxNewTokens),
        )
    }

    override suspend fun cancel() = delegate.cancel()

    override suspend fun unload() = delegate.unload()
}
