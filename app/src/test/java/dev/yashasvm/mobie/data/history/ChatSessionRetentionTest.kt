package dev.yashasvm.mobie.data.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionRetentionTest {
    private fun saved(id: String) = ChatHistorySession(id, id, 0, listOf(HistoryMessage(true, id)))
    private fun draft(id: String) = ChatHistorySession(id, "New local chat", 0)

    @Test
    fun repeatedEmptyChatsDoNotEvictSavedConversations() {
        val original = (1..20).map { saved("saved-$it") }
        var sessions = original
        repeat(100) { sessions = retainedChatSessions(listOf(draft("draft-$it")) + sessions, 20) }
        assertEquals(21, sessions.size)
        assertEquals("draft-99", sessions.first().id)
        assertEquals(original, sessions.drop(1))
    }

    @Test
    fun savingFirstMessageUsesSavedSlotAndEvictsOnlyOldestConversation() {
        val original = (1..20).map { saved("saved-$it") }
        val sessions = retainedChatSessions(listOf(saved("new-chat")) + original, 20)
        assertEquals(listOf("new-chat") + (1..19).map { "saved-$it" }, sessions.map { it.id })
    }

    @Test
    fun writingAnEmptyDraftStillPreservesAllSavedChats() {
        val original = (1..20).map { saved("saved-$it") }
        val sessions = retainedChatSessions(listOf(draft("active")) + original + draft("old"), 20)
        assertEquals(listOf(draft("active")) + original, sessions)
    }

    @Test
    fun newestFirstOrderIsPreservedWhenDraftIsNotFirst() {
        val sessions = listOf(saved("newest"), draft("draft"), saved("older"), draft("stale"), saved("oldest"))
        assertEquals(sessions.take(3), retainedChatSessions(sessions, 2))
    }
}
