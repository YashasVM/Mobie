package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.runtime.InferenceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Publishes the first two chunks and the first answer immediately. Later bursts share a UI update
 * every 32ms, with a bounded buffer. Terminal events and cancellation flush all received text.
 * These are SDK chunks, which can contain more than one model token.
 */
internal suspend fun Flow<InferenceEvent>.collectBatchedTokens(onEvent: (InferenceEvent) -> Unit) = coroutineScope {
    val buffer = StreamingTokenBuffer()
    val lock = Mutex()
    var timer: Job? = null

    fun flush() {
        timer?.cancel()
        timer = null
        buffer.flush().forEach(onEvent)
    }

    try {
        collect { event ->
            lock.withLock {
                if (event is InferenceEvent.Token) {
                    buffer.append(event).forEach(onEvent)
                    if (buffer.hasPending && timer == null) {
                        timer = launch {
                            delay(32)
                            lock.withLock { flush() }
                        }
                    } else if (!buffer.hasPending) {
                        timer?.cancel()
                        timer = null
                    }
                } else if (event is InferenceEvent.Progress) {
                    onEvent(event)
                } else {
                    flush()
                    onEvent(event)
                }
            }
        }
    } finally {
        withContext(NonCancellable) { lock.withLock { flush() } }
    }
}

internal class StreamingTokenBuffer {
    private data class Segment(val thinking: Boolean, val text: StringBuilder)

    private val pending = ArrayList<Segment>()
    private var pendingChars = 0
    private var chunks = 0
    private var sawAnswer = false
    val hasPending: Boolean get() = pending.isNotEmpty()

    fun append(event: InferenceEvent.Token): List<InferenceEvent.Token> {
        if (event.text.isEmpty()) return emptyList()
        chunks++
        val immediate = chunks <= 2 || (!event.thinking && !sawAnswer)
        if (!event.thinking) sawAnswer = true
        if (immediate) return flush() + event

        val last = pending.lastOrNull()
        if (last != null && last.thinking == event.thinking) {
            last.text.append(event.text)
        } else {
            pending += Segment(event.thinking, StringBuilder(event.text))
        }
        pendingChars += event.text.length
        return if (pendingChars >= 2_048 || pending.size >= 32) flush() else emptyList()
    }

    fun flush(): List<InferenceEvent.Token> {
        if (!hasPending) return emptyList()
        val result = pending.map { InferenceEvent.Token(it.text.toString(), it.thinking) }
        pending.clear()
        pendingChars = 0
        return result
    }
}
