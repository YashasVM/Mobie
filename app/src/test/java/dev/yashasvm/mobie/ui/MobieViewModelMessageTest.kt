package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobieViewModelMessageTest {
    @Test
    fun `marks latest assistant partial as interrupted`() {
        val messages = listOf(
            ChatMessage(fromUser = true, text = "first prompt"),
            ChatMessage(fromUser = false, text = "first answer"),
            ChatMessage(fromUser = true, text = "second prompt"),
            ChatMessage(fromUser = false, text = "partial answer"),
        )

        val updated = messages.markLastAssistantInterrupted()

        assertFalse(updated[1].interrupted)
        assertTrue(updated[3].interrupted)
        assertEquals("partial answer", updated[3].text)
    }

    @Test
    fun `user only history is unchanged when no assistant exists`() {
        val messages = listOf(ChatMessage(fromUser = true, text = "cancelled before output"))

        val updated = messages.markLastAssistantInterrupted()

        assertEquals(messages, updated)
    }
}
