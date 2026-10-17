package dev.yashasvm.mobie.data.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryKeysTest {
    @Test
    fun `model ids come from session keys and legacy bare keys`() {
        val keys = listOf(
            "sessions:litert-community/A",
            "current:litert-community/A",
            "current:litert-community/Orphan",
            "litert-community/Legacy",
            "meta:last_model",
            "sessions:",
        )
        assertEquals(
            setOf("litert-community/A", "litert-community/Legacy"),
            ChatHistoryStore.historyModelIds(keys),
        )
    }

    @Test
    fun `automatic title uses the first non-blank prompt on one line`() {
        val messages = listOf(
            HistoryMessage(false, "Hello"),
            HistoryMessage(true, "  "),
            HistoryMessage(true, "Plan a trip\nto Lisbon"),
        )
        assertEquals("Plan a trip to Lisbon", ChatHistoryStore.titleFor(messages))
        assertEquals("New chat", ChatHistoryStore.titleFor(listOf(HistoryMessage(false, "Hi"))))
        assertEquals(48, ChatHistoryStore.titleFor(listOf(HistoryMessage(true, "x".repeat(200)))).length)
    }
}
