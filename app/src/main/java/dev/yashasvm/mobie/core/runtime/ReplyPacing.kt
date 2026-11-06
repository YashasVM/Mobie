package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Caps visible answer text using an estimate of four Unicode code points per token.
 * This controls presentation, not the engine's generation speed or tokenizer. Thinking and
 * nontext events retain their order. Backpressure and cancellation propagate to the source.
 */
fun Flow<InferenceEvent>.paceReplies(tokensPerSecond: Int?): Flow<InferenceEvent> =
    paceReplies(tokensPerSecond, System::nanoTime) { delay(it) }

internal fun Flow<InferenceEvent>.paceReplies(
    tokensPerSecond: Int?,
    nowNanos: () -> Long,
    waitMillis: suspend (Long) -> Unit,
): Flow<InferenceEvent> {
    if (tokensPerSecond == null) return this
    require(tokensPerSecond in 5..100)
    return flow {
        var nextAnswerNanos: Long? = null
        collect { event ->
            if (event !is InferenceEvent.Token || event.thinking || event.text.isEmpty()) {
                emit(event)
            } else {
                var start = 0
                while (start < event.text.length) {
                    var end = start
                    var codePoints = 0
                    while (end < event.text.length && codePoints < 4) {
                        end += Character.charCount(event.text.codePointAt(end))
                        codePoints++
                    }
                    val remaining = nextAnswerNanos?.minus(nowNanos()) ?: 0L
                    if (remaining > 0) waitMillis((remaining + 999_999L) / 1_000_000L)
                    emit(InferenceEvent.Token(event.text.substring(start, end)))
                    // Reset after each emission so a slow model never earns a catch-up burst.
                    nextAnswerNanos = nowNanos() + codePoints * 1_000_000_000L / (4L * tokensPerSecond)
                    start = end
                }
            }
        }
    }
}
