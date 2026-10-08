package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the fast-path optimization in updateLastAssistant.
 *
 * The optimization: when rawText is null (no tag context) and the incoming chunk contains no '<',
 * skip the 4×indexOf tag scan and append directly to text. After a close tag is found, rawText is
 * set to null so subsequent answer tokens also take this fast path.
 */
class UpdateLastAssistantFastPathTest {

    // ── No-tag fast path ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `plain tokens accumulate correctly without growing rawText`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("Hello")
        messages = messages.updateLastAssistant(" world")
        messages = messages.updateLastAssistant("!")

        assertEquals("Hello world!", messages.single().text)
        assertNull("no-tag response must not accumulate rawText", messages.single().rawText)
        assertEquals("", messages.single().thinking)
    }

    @Test
    fun `many plain tokens accumulate correctly via fast path`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        val tokens = (1..20).map { "token$it " }
        tokens.forEach { messages = messages.updateLastAssistant(it) }

        assertEquals(tokens.joinToString(""), messages.single().text)
        assertNull(messages.single().rawText)
    }

    @Test
    fun `angle bracket in non-tag context falls through to full parse then clears rawText`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("Use ")
        messages = messages.updateLastAssistant("<b>bold</b>")
        messages = messages.updateLastAssistant(" text")

        assertEquals("Use <b>bold</b> text", messages.single().text)
        assertNull("rawText cleared when no recognized tag found", messages.single().rawText)
        assertEquals("", messages.single().thinking)
    }

    // ── Post-close fast path ──────────────────────────────────────────────────────────────────────

    @Test
    fun `rawText is cleared after close tag is found`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("<think>plan</think>Answer")

        assertNull("rawText must be null after close tag found", messages.single().rawText)
        assertEquals("Answer", messages.single().text)
        assertEquals("plan", messages.single().thinking)
    }

    @Test
    fun `answer tokens after close tag accumulate correctly`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("<think>reasoning</think>Start")
        messages = messages.updateLastAssistant(" of")
        messages = messages.updateLastAssistant(" answer")

        assertEquals("Start of answer", messages.single().text)
        assertEquals("reasoning", messages.single().thinking)
        assertNull(messages.single().rawText)
    }

    @Test
    fun `answer grows correctly when close tag arrives with first answer token`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        // Stream the close tag and initial answer in separate tokens
        listOf("<think>", "deep thought", "</think>", "Answer here").forEach { chunk ->
            messages = messages.updateLastAssistant(chunk)
        }

        assertEquals("Answer here", messages.single().text)
        assertEquals("deep thought", messages.single().thinking)
        assertNull(messages.single().rawText)
    }

    @Test
    fun `thinking is unchanged after close tag when answer tokens arrive`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("<think>fixed plan</think>")
        val thinkingAfterClose = messages.single().thinking
        // Several more answer tokens
        repeat(5) { i -> messages = messages.updateLastAssistant("word$i ") }

        assertEquals("fixed plan", thinkingAfterClose)
        assertEquals("fixed plan", messages.single().thinking)
    }

    // ── Partial-tag handling preserved ───────────────────────────────────────────────────────────

    @Test
    fun `partial think tag withholds text until tag resolves`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("<th") // partial <think>

        assertEquals("", messages.single().text)
        assertNotNull("rawText must be kept for partial tag tracking", messages.single().rawText)
    }

    @Test
    fun `cross-chunk think tag is parsed correctly`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        listOf("<th", "ink>hidden</think>Visible").forEach { chunk ->
            messages = messages.updateLastAssistant(chunk)
        }

        assertEquals("Visible", messages.single().text)
        assertEquals("hidden", messages.single().thinking)
        assertNull(messages.single().rawText)
    }

    @Test
    fun `partial tag that never completes as a reasoning tag eventually becomes answer text`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        // "<th" looks like a partial <think>, but resolves to an unrelated HTML tag
        messages = messages.updateLastAssistant("<th")
        messages = messages.updateLastAssistant(" class='row'>cell</th>")

        // No recognized reasoning tag found — accumulated text should be visible
        val text = messages.single().text
        assertEquals("", messages.single().thinking)
        assert(text.contains("<th")) { "Answer should contain the angle-bracket text, got: $text" }
    }

    // ── Multi-message list invariant ─────────────────────────────────────────────────────────────

    @Test
    fun `only the last assistant message is updated in a multi-turn conversation`() {
        val messages = listOf(
            ChatMessage(fromUser = true, text = "Q1"),
            ChatMessage(fromUser = false, text = "A1"),
            ChatMessage(fromUser = true, text = "Q2"),
            ChatMessage(fromUser = false, text = ""),
        )

        val updated = messages.updateLastAssistant("A2 token")

        assertEquals("A1", updated[1].text)  // first assistant untouched
        assertEquals("A2 token", updated[3].text)
    }

    // ── Dedicated thinking-channel fast path ─────────────────────────────────────────────────────

    @Test
    fun `thinkingChunk flag routes to thinking field without touching rawText`() {
        var messages = listOf(ChatMessage(fromUser = false, text = ""))
        messages = messages.updateLastAssistant("chain-of-thought", thinkingChunk = true)
        messages = messages.updateLastAssistant(" continues", thinkingChunk = true)
        messages = messages.updateLastAssistant("Final answer")

        assertEquals("chain-of-thought continues", messages.single().thinking)
        assertEquals("Final answer", messages.single().text)
        assertNull(messages.single().rawText)
    }
}
