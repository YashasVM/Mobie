package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.runtime.InferenceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTokensTest {
    @Test
    fun `first two chunks and first answer are immediate`() {
        val buffer = StreamingTokenBuffer()
        assertEquals(listOf(token("one", true)), buffer.append(token("one", true)))
        assertEquals(listOf(token("two", true)), buffer.append(token("two", true)))
        assertTrue(buffer.append(token("three", true)).isEmpty())
        assertEquals(listOf(token("three", true), token("answer")), buffer.append(token("answer")))
        assertFalse(buffer.hasPending)
    }

    @Test
    fun `batch preserves all text and channel order`() {
        val buffer = StreamingTokenBuffer()
        buffer.append(token("a"))
        buffer.append(token("b"))
        listOf(token("c"), token("d"), token("thought", true), token("more", true), token("e"))
            .forEach { assertTrue(buffer.append(it).isEmpty()) }
        assertEquals(listOf(token("cd"), token("thoughtmore", true), token("e")), buffer.flush())
        assertTrue(buffer.flush().isEmpty())
    }

    @Test
    fun `buffer flushes before a burst can grow without a bound`() {
        val buffer = StreamingTokenBuffer()
        buffer.append(token("a"))
        buffer.append(token("b"))
        assertEquals(listOf(token("x".repeat(2_048))), buffer.append(token("x".repeat(2_048))))
        assertFalse(buffer.hasPending)
    }

    @Test
    fun `terminal event follows the complete buffered answer`() = runBlocking {
        val seen = mutableListOf<InferenceEvent>()
        flow {
            listOf("a", "b", "c", "d").forEach { emit(token(it)) }
            emit(InferenceEvent.Complete)
        }.collectBatchedTokens(seen::add)
        assertEquals(listOf(token("a"), token("b"), token("cd"), InferenceEvent.Complete), seen)
    }

    @Test
    fun `pending output appears while upstream is idle`() = runBlocking {
        val seen = mutableListOf<InferenceEvent>()
        flow {
            listOf("a", "b", "c").forEach { emit(token(it)) }
            delay(100)
            assertEquals(listOf(token("a"), token("b"), token("c")), seen)
            emit(InferenceEvent.Complete)
        }.collectBatchedTokens(seen::add)
        assertEquals(InferenceEvent.Complete, seen.last())
    }

    @Test
    fun `cancellation preserves text already received without a false completion`() = runBlocking {
        val seen = mutableListOf<InferenceEvent>()
        try {
            flow {
                listOf("a", "b", "c", "d").forEach { emit(token(it)) }
                throw CancellationException("stopped")
            }.collectBatchedTokens(seen::add)
        } catch (_: CancellationException) {
            // Cancellation remains visible to the caller after the received text is flushed.
        }
        assertEquals(listOf(token("a"), token("b"), token("cd")), seen)
    }

    @Test
    fun `stopping during a split reasoning tag preserves the previous reply`() = runBlocking {
        var messages = listOf(
            ChatMessage(true, "old prompt"), ChatMessage(false, "old reply"),
            ChatMessage(true, "new prompt"), ChatMessage(false, ""),
        )
        try {
            flow {
                listOf("<", "t", "hink>new reasoning").forEach { emit(token(it)) }
                throw CancellationException("stopped")
            }.collectBatchedTokens { event ->
                if (event is InferenceEvent.Token) messages = messages.updateLastAssistant(event.text, event.thinking)
            }
        } catch (_: CancellationException) {
            messages = messages.finishInterruptedReply()
        }
        assertEquals("old reply", messages[1].text)
        assertFalse(messages[1].interrupted)
        assertEquals("new reasoning", messages.last().thinking)
        assertTrue(messages.last().interrupted)
    }

    private fun token(text: String, thinking: Boolean = false) = InferenceEvent.Token(text, thinking)
}
