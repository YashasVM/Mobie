package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSpeechTest {
    @Test
    fun `dictation appends to the draft without submitting or discarding typed text`() {
        assertEquals("Please explain this", appendDictation("Please explain ", " this "))
        assertEquals("Hello", appendDictation("", " Hello "))
        assertEquals("Keep my draft  ", appendDictation("Keep my draft  ", "  "))
    }

    @Test
    fun `long replies are fully queued in chunks below Android speech limit`() {
        val reply = "A long reply. ".repeat(1000)
        val chunks = speechChunks(reply)
        assertTrue(chunks.all { it.length <= 3500 })
        assertEquals(reply, chunks.joinToString(""))
        assertEquals(listOf("Short reply"), speechChunks("Short reply"))
        val emojiReply = "a".repeat(3499) + "😀" + "b".repeat(100)
        assertEquals(emojiReply, speechChunks(emojiReply).joinToString(""))
        assertTrue(speechChunks(emojiReply).none { it.last().isHighSurrogate() })
    }
}
