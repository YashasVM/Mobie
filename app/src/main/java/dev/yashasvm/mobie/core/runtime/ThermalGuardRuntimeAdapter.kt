package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ThermalGuardRuntimeAdapter(
    private val delegate: RuntimeAdapter,
    private val thermalStatusProvider: () -> Int,
    private val activePollIntervalMs: Long = 500L,
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
        return channelFlow {
            val thermalAbort = AtomicBoolean(false)
            val generationJob = launch {
                delegate.generate(
                    prompt = prompt,
                    imagePath = imagePath,
                    config = config.copy(maxNewTokens = decision.maxNewTokens),
                ).collect { event ->
                    if (!thermalAbort.get()) send(event)
                }
            }
            val monitorJob = launch {
                while (isActive && generationJob.isActive) {
                    delay(activePollIntervalMs)
                    val currentDecision = ThermalInferencePolicy.decide(
                        thermalStatus = thermalStatusProvider(),
                        requestedMaxNewTokens = decision.maxNewTokens,
                    )
                    if (!currentDecision.allowed && thermalAbort.compareAndSet(false, true)) {
                        delegate.cancel()
                        send(InferenceEvent.Error(currentDecision.errorMessage ?: "Thermal limit reached"))
                        generationJob.cancel()
                        break
                    }
                }
            }

            generationJob.join()
            monitorJob.cancel()
        }
    }

    override suspend fun cancel() = delegate.cancel()

    override suspend fun unload() = delegate.unload()
}
