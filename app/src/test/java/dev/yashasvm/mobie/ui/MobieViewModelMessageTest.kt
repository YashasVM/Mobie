package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MobieViewModelMessageTest {
    @Test
    fun `stop saves partial reply before waiting for native cancellation`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val neverReturns = CompletableDeferred<Unit>()
        val job = launch {
            entered.complete(Unit)
            try { neverReturns.await() } finally { neverReturns.await() }
        }
        entered.await()
        var saved: List<ChatMessage>? = null
        val stopper = launch {
            saveBeforeCancellation(
                listOf(ChatMessage(false, "", rawText = "if a <th")), job,
            ) { saved = it }
        }
        kotlinx.coroutines.yield()
        assertEquals("if a <th", saved?.single()?.text)
        assertTrue(saved?.single()?.interrupted == true)
        neverReturns.complete(Unit)
        stopper.join()
    }

    @Test
    fun `cancelling before output preserves the previous completed answer`() {
        val messages = listOf(
            ChatMessage(fromUser = true, text = "first prompt"),
            ChatMessage(fromUser = false, text = "completed answer"),
            ChatMessage(fromUser = true, text = "cancelled before output"),
        )

        assertEquals(messages, messages.markLastAssistantInterrupted())
    }

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
