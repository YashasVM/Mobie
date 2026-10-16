package dev.yashasvm.mobie.data.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistorySessionsTest {
    private fun session(id: String, vararg texts: String) =
        ChatHistorySession(id, id, updatedAt = 0L, messages = texts.map { HistoryMessage(true, it) })

    @Test
    fun `starting a chat drops earlier sessions that never got a message`() {
        val existing = listOf(session("empty-launch"), session("real", "Hello"), session("older-empty"))

        val result = ChatHistoryStore.sessionsAfterStart(session("new"), existing)

        assertEquals(listOf("new", "real"), result.map { it.id })
    }

    @Test
    fun `repeated launches without chatting never evict saved chats`() {
        val saved = (1..ChatHistoryStore.MAX_SESSIONS - 1).map { session("chat-$it", "question $it") }
        var sessions = saved
        repeat(ChatHistoryStore.MAX_SESSIONS * 2) { launch ->
            sessions = ChatHistoryStore.sessionsAfterStart(session("launch-$launch"), sessions)
        }

        assertEquals(ChatHistoryStore.MAX_SESSIONS, sessions.size)
        assertEquals(saved.map { it.id }, sessions.drop(1).map { it.id })
    }

    @Test
    fun `session list is still capped`() {
        val existing = (1..ChatHistoryStore.MAX_SESSIONS + 5).map { session("chat-$it", "q") }

        val result = ChatHistoryStore.sessionsAfterStart(session("new"), existing)

        assertEquals(ChatHistoryStore.MAX_SESSIONS, result.size)
        assertEquals("new", result.first().id)
    }

    @Test
    fun `title is the first question on one line`() {
        val title = ChatHistoryStore.sessionTitle(
            listOf(HistoryMessage(false, "ignored"), HistoryMessage(true, "Plan\na trip"), HistoryMessage(true, "later")),
        )

        assertEquals("Plan a trip", title)
    }

    @Test
    fun `title falls back to the attached file name when only files were sent`() {
        val attachment = HistoryAttachment("notes.md", "text/markdown", 10, "# Notes")

        val title = ChatHistoryStore.sessionTitle(listOf(HistoryMessage(true, "", attachments = listOf(attachment))))

        assertEquals("notes.md", title)
    }

    @Test
    fun `title is the placeholder before any user message`() {
        assertEquals(ChatHistoryStore.NEW_CHAT_TITLE, ChatHistoryStore.sessionTitle(emptyList()))
        assertEquals(ChatHistoryStore.NEW_CHAT_TITLE, ChatHistoryStore.sessionTitle(listOf(HistoryMessage(true, "  "))))
    }

    @Test
    fun `long titles are cut to the title length`() {
        val title = ChatHistoryStore.sessionTitle(listOf(HistoryMessage(true, "x".repeat(200))))

        assertEquals(ChatHistoryStore.TITLE_LENGTH, title.length)
    }
}
