package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ThermalGuardRuntimeAdapter(
    private val delegate: RuntimeAdapter,
    private val activePollIntervalMs: Long = 500L,
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
    ): Flow<InferenceEvent> = channelFlow {
        // A Flow is cold: sample thermal state when this collection actually starts, not when the
        // request object is created. Delayed or repeated collection must never reuse a stale safe
        // decision after the phone has become critically hot.
        val decision = ThermalInferencePolicy.decide(
            thermalStatus = thermalStatusProvider(),
            requestedMaxNewTokens = config.maxNewTokens,
        )
        if (!decision.allowed) {
            send(InferenceEvent.Error(decision.errorMessage ?: "Thermal limit reached"))
            return@channelFlow
        }

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
                    val cancellationFailure = try {
                        delegate.cancel()
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        error
                    } finally {
                        // A failed native cancel must not leave the collector alive. Cancelling the
                        // collection also drives outer runtime guards through their cleanup path.
                        generationJob.cancel()
                    }
                    val thermalMessage = currentDecision.errorMessage ?: "Thermal limit reached"
                    send(
                        InferenceEvent.Error(
                            if (cancellationFailure == null) {
                                thermalMessage
                            } else {
                                "$thermalMessage Runtime cancellation also failed; reload the model or restart Mobie before retrying."
                            },
                        ),
                    )
                    break
                }
            }
        }

        generationJob.join()
        monitorJob.cancel()
    }

    override suspend fun cancel() = delegate.cancel()

    override suspend fun unload() = delegate.unload()
}
