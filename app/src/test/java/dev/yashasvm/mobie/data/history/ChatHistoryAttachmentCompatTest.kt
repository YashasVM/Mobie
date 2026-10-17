package dev.yashasvm.mobie.data.history

import dev.yashasvm.mobie.core.attachment.DocumentAttachment
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryAttachmentCompatTest {
    private val sessionsSerializer = kotlinx.serialization.builtins.ListSerializer(ChatHistorySession.serializer())

    @Test
    fun `transcripts saved before attachments existed still decode`() {
        val legacyJson = """[{"id":"s1","title":"Hi","updatedAt":5,"messages":[
            {"fromUser":true,"text":"hello","imagePath":null,"thinking":"","interrupted":false},
            {"fromUser":false,"text":"hi there","thinking":"x","interrupted":true}
        ]}]"""

        val sessions = chatHistoryJson.decodeFromString(sessionsSerializer, legacyJson)

        val messages = sessions.single().messages
        assertEquals(2, messages.size)
        assertEquals(emptyList<HistoryAttachment>(), messages[0].attachments)
        assertEquals(emptyList<HistoryAttachment>(), messages[1].attachments)
        assertTrue(messages[1].interrupted)
    }

    @Test
    fun `legacy single-message list format still decodes with empty attachments`() {
        val legacy = """[{"fromUser":true,"text":"old"}]"""
        val messages = chatHistoryJson.decodeFromString<List<HistoryMessage>>(legacy)
        assertEquals("old", messages.single().text)
        assertTrue(messages.single().attachments.isEmpty())
    }

    @Test
    fun `messages without attachments do not write the new key`() {
        val encoded = chatHistoryJson.encodeToString(HistoryMessage(true, "plain"))
        assertFalse(encoded.contains("attachments"))
    }

    @Test
    fun `attachments round trip through persisted history`() {
        val attachment = DocumentAttachment("notes.md", "text/markdown", 42, "# Notes\n[truncated]", truncated = true)
        val message = HistoryMessage(true, "Summarize", attachments = listOf(attachment.toHistory()))

        val encoded = chatHistoryJson.encodeToString(message)
        val decoded = chatHistoryJson.decodeFromString<HistoryMessage>(encoded)

        assertEquals(listOf(attachment), decoded.attachments.map { it.toDocument() })
    }

    @Test
    fun `unknown future keys are ignored when reading`() {
        val future = """{"fromUser":false,"text":"ok","someFutureField":{"a":1}}"""
        assertEquals("ok", chatHistoryJson.decodeFromString<HistoryMessage>(future).text)
    }
}
