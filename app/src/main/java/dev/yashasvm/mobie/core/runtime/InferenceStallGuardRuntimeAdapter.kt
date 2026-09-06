package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Prevents a broken native streaming callback from holding Mobie's generation path forever.
 *
 * LiteRT-LM has had Android failure modes where tokens stop arriving without a terminal callback.
 * The native collector intentionally runs outside the caller's structured scope: if LiteRT ignores
 * coroutine cancellation while blocked in native code, the watchdog must still be able to return
 * an error to the UI instead of waiting for that collector forever. The abandoned collector is
 * cancelled best-effort after the runtime cancellation request; native resources may still require
 * an app restart if the underlying call itself never returns.
 */
class InferenceStallGuardRuntimeAdapter(
    private val delegate: RuntimeAdapter,
    private val firstEventTimeoutMs: Long = FIRST_EVENT_TIMEOUT_MS,
    private val activeIdleTimeoutMs: Long = ACTIVE_IDLE_TIMEOUT_MS,
    private val cancellationTimeoutMs: Long = CANCELLATION_TIMEOUT_MS,
) : RuntimeAdapter {
    override val format: ModelFormat = delegate.format

    init {
        require(firstEventTimeoutMs > 0L)
        require(activeIdleTimeoutMs > 0L)
        require(cancellationTimeoutMs > 0L)
    }

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
    ): Flow<InferenceEvent> = flow {
        val events = Channel<InferenceEvent>(Channel.BUFFERED)
        val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val producer = producerScope.launch {
            try {
                delegate.generate(prompt, imagePath, config).collect { event ->
                    events.send(event)
                }
            } finally {
                events.close()
            }
        }
        var sawProgress = false
        var sawTerminalEvent = false
        try {
            while (true) {
                val timeoutMs = if (sawProgress) activeIdleTimeoutMs else firstEventTimeoutMs
                val result = withTimeoutOrNull(timeoutMs) { events.receiveCatching() }
                if (result == null) {
                    withTimeoutOrNull(cancellationTimeoutMs) { delegate.cancel() }
                    producer.cancel()
                    producerScope.cancel()
                    emit(
                        InferenceEvent.Error(
                            if (sawProgress) {
                                "Local inference stopped making progress and was cancelled. Retry the prompt; restart Mobie if the model will not reload."
                            } else {
                                "Local inference did not start within the safety timeout and was cancelled. Retry with a smaller compatible model; restart Mobie if the model will not reload."
                            },
                        ),
                    )
                    return@flow
                }

                val event = result.getOrNull()
                if (event == null) {
                    if (!sawTerminalEvent) {
                        emit(InferenceEvent.Error("Local inference ended without a completion signal. Retry the prompt."))
                    }
                    return@flow
                }

                sawProgress = true
                if (event is InferenceEvent.Complete || event is InferenceEvent.Error) {
                    sawTerminalEvent = true
                }
                emit(event)
                if (sawTerminalEvent) {
                    producer.cancel()
                    return@flow
                }
            }
        } finally {
            producer.cancel()
            producerScope.cancel()
            events.cancel()
        }
    }

    override suspend fun cancel() = delegate.cancel()

    override suspend fun unload() = delegate.unload()

    private companion object {
        const val FIRST_EVENT_TIMEOUT_MS = 120_000L
        const val ACTIVE_IDLE_TIMEOUT_MS = 30_000L
        const val CANCELLATION_TIMEOUT_MS = 2_000L
    }
}
