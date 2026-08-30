package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingParserTest {
    @Test
    fun `split think tags stay out of the visible answer`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))

        listOf("<th", "ink>private reasoning", "</think>Visible answer").forEach { chunk ->
            messages = messages.updateLastAssistant(chunk)
        }

        assertEquals("private reasoning", messages.single().thinking)
        assertEquals("Visible answer", messages.single().text)
    }

    @Test
    fun `reasoning channel stays separate from the answer`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))

        messages = messages.updateLastAssistant("private reasoning", thinkingChunk = true)
        messages = messages.updateLastAssistant("Visible answer")

        assertEquals("private reasoning", messages.single().thinking)
        assertEquals("Visible answer", messages.single().text)
    }

    @Test
    fun `common reasoning tags stay out of the visible answer`() {
        listOf("thinking", "analysis", "reasoning").forEach { tag ->
            var messages = listOf(ChatMessage(fromUser = false, text = ""))

            messages = messages.updateLastAssistant("<$tag>private</$tag>Visible answer")

            assertEquals("private", messages.single().thinking)
            assertEquals("Visible answer", messages.single().text)
        }
    }
}
