package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryPolicyTest {
    @Test
    fun `keeps recent user-led turns within message limit`() {
        val history = (1..15).flatMap { turn ->
            listOf(
                RuntimeMessage(true, "user-$turn"),
                RuntimeMessage(false, "assistant-$turn"),
            )
        }

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals(20, selected.size)
        assertEquals("user-6", selected.first().text)
        assertEquals("assistant-15", selected.last().text)
        assertTrue(selected.first().fromUser)
    }

    @Test
    fun `preserves image path on completed user turns`() {
        val history = listOf(
            RuntimeMessage(true, "describe this", imagePath = "/tmp/vision.jpg"),
            RuntimeMessage(false, "a test image"),
        )

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals("/tmp/vision.jpg", selected.first().imagePath)
    }

    @Test
    fun `drops an older turn atomically when byte boundary would split it`() {
        val largeUser = "u".repeat(ConversationHistoryPolicy.MAX_RESTORED_UTF8_BYTES - 20)
        val history = listOf(
            RuntimeMessage(true, largeUser),
            RuntimeMessage(false, "old assistant"),
            RuntimeMessage(true, "recent user"),
            RuntimeMessage(false, "recent answer"),
        )

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals(listOf("recent user", "recent answer"), selected.map { it.text })
        assertTrue(selected.first().fromUser)
    }

    @Test
    fun `never restores more than byte or character budget`() {
        val chunk = "x".repeat(512)
        val history = (1..20).flatMap {
            listOf(RuntimeMessage(true, chunk), RuntimeMessage(false, chunk))
        }

        val selected = ConversationHistoryPolicy.select(history)

        assertTrue(selected.sumOf { it.text.length } <= ConversationHistoryPolicy.MAX_RESTORED_CHARS)
        assertTrue(
            selected.sumOf { it.text.toByteArray(Charsets.UTF_8).size } <=
                ConversationHistoryPolicy.MAX_RESTORED_UTF8_BYTES,
        )
        assertTrue(selected.size <= ConversationHistoryPolicy.MAX_RESTORED_MESSAGES)
        assertTrue(selected.isEmpty() || selected.first().fromUser)
    }

    @Test
    fun `utf8 byte budget limits token dense unicode history`() {
        val unicodeChunk = "你".repeat(700)
        val history = (1..4).flatMap {
            listOf(RuntimeMessage(true, unicodeChunk), RuntimeMessage(false, unicodeChunk))
        }

        val selected = ConversationHistoryPolicy.select(history)
        val restoredBytes = selected.sumOf { it.text.toByteArray(Charsets.UTF_8).size }

        assertTrue(restoredBytes <= ConversationHistoryPolicy.MAX_RESTORED_UTF8_BYTES)
        assertTrue(selected.size <= 2)
        assertTrue(selected.isEmpty() || selected.first().fromUser)
    }

    @Test
    fun `oversized newest turn does not erase older restorable context`() {
        val history = listOf(
            RuntimeMessage(true, "older user"),
            RuntimeMessage(false, "older answer"),
            RuntimeMessage(true, "x".repeat(ConversationHistoryPolicy.MAX_RESTORED_UTF8_BYTES + 1)),
            RuntimeMessage(false, "latest answer"),
        )

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals(listOf("older user", "older answer"), selected.map { it.text })
    }

    @Test
    fun `drops trailing user-only interrupted turn but keeps completed context`() {
        val history = listOf(
            RuntimeMessage(true, "completed user"),
            RuntimeMessage(false, "completed answer"),
            RuntimeMessage(true, "cancelled before first token"),
        )

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals(listOf("completed user", "completed answer"), selected.map { it.text })
    }

    @Test
    fun `drops partial interrupted assistant turn but keeps completed context`() {
        val history = listOf(
            RuntimeMessage(true, "completed user"),
            RuntimeMessage(false, "completed answer"),
            RuntimeMessage(true, "cancelled prompt", interrupted = true),
            RuntimeMessage(false, "partial answer", interrupted = true),
        )

        val selected = ConversationHistoryPolicy.select(history)

        assertEquals(listOf("completed user", "completed answer"), selected.map { it.text })
    }

    @Test
    fun `runtime interrupted turn never enters committed replay history`() {
        val committed = listOf(
            RuntimeMessage(true, "completed user"),
            RuntimeMessage(false, "completed answer"),
        )

        val selected = ConversationHistoryPolicy.afterInterruptedTurn(
            committedHistory = committed,
            prompt = "cancelled prompt",
            partialAnswer = "partial answer",
        )

        assertEquals(listOf("completed user", "completed answer"), selected.map { it.text })
        assertTrue(selected.none { it.text == "cancelled prompt" || it.text == "partial answer" })
    }

    @Test
    fun `runtime cancellation before first token also preserves only committed context`() {
        val committed = listOf(
            RuntimeMessage(true, "completed user"),
            RuntimeMessage(false, "completed answer"),
        )

        val selected = ConversationHistoryPolicy.afterInterruptedTurn(
            committedHistory = committed,
            prompt = "cancelled before output",
            partialAnswer = null,
        )

        assertEquals(listOf("completed user", "completed answer"), selected.map { it.text })
    }

    @Test
    fun `never restores a user-only conversation`() {
        val selected = ConversationHistoryPolicy.select(
            listOf(RuntimeMessage(true, "cancelled before first token")),
        )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `ignores blank messages and refuses a single over-budget history entry`() {
        val history = listOf(
            RuntimeMessage(false, "   "),
            RuntimeMessage(true, "x".repeat(ConversationHistoryPolicy.MAX_RESTORED_UTF8_BYTES + 1)),
        )

        assertTrue(ConversationHistoryPolicy.select(history).isEmpty())
    }
}
