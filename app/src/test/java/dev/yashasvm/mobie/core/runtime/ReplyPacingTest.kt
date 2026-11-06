package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPacingTest {
    @Test
    fun `unlimited preserves original chunks and performs no waits`() = runBlocking {
        val events = listOf(InferenceEvent.Token("A long reply that should remain one chunk."), InferenceEvent.Complete)
        val actual = flowOf(*events.toTypedArray()).paceReplies(null, { error("No clock needed") }) {
            error("No wait allowed")
        }.toList()
        assertEquals(events, actual)
    }

    @Test
    fun `large reply retains exact unicode text before completion`() = runBlocking {
        val text = "abc😀def👨‍👩‍👧‍👦 hello 你好 é 🚀 end"
        var nanos = 0L
        val actual = flowOf(InferenceEvent.Token(text), InferenceEvent.Complete)
            .paceReplies(10, { nanos }) { nanos += it * 1_000_000 }.toList()
        val chunks = actual.filterIsInstance<InferenceEvent.Token>()
        assertEquals(text, chunks.joinToString("") { it.text })
        chunks.forEach { chunk ->
            assertFalse(Character.isLowSurrogate(chunk.text.first()))
            assertFalse(Character.isHighSurrogate(chunk.text.last()))
        }
        assertTrue(chunks.size > 1)
        assertEquals(InferenceEvent.Complete, actual.last())
    }

    @Test
    fun `reply pacing spreads a burst over time without slowing thinking or changing errors`() = runBlocking {
        var nanos = 0L
        val seen = mutableListOf<Pair<Long, InferenceEvent>>()
        val failure = InferenceEvent.Error("Disconnected", requiresReload = true)
        flowOf(InferenceEvent.Token("thinking", true), InferenceEvent.Token("abcdefghijkl"), failure)
            .paceReplies(10, { nanos }) { nanos += it * 1_000_000 }
            .collect { seen += nanos / 1_000_000 to it }
        assertEquals(listOf(0L, 0L, 100L, 200L, 200L), seen.map { it.first })
        assertEquals(InferenceEvent.Token("thinking", true), seen.first().second)
        assertSame(failure, seen.last().second)
    }

    @Test
    fun `time spent waiting for the model never earns a catch up burst`() = runBlocking {
        var nanos = 0L
        val times = mutableListOf<Long>()
        flow {
            emit(InferenceEvent.Token("abcd"))
            nanos += 5_000_000_000L
            emit(InferenceEvent.Token("efghijkl"))
        }.paceReplies(10, { nanos }) { nanos += it * 1_000_000 }
            .collect { times += nanos / 1_000_000 }
        assertEquals(listOf(0L, 5_000L, 5_100L), times)
    }

    @Test
    fun `cancellation stops a paced burst and never emits false completion`() = runBlocking {
        val seen = mutableListOf<InferenceEvent>()
        try {
            flowOf(InferenceEvent.Token("abcdefghijkl"), InferenceEvent.Complete)
                .paceReplies(5, { 0L }) { throw CancellationException("Stopped") }
                .collect { seen += it }
            error("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(InferenceEvent.Token("abcd")), seen)
        }
    }
}
