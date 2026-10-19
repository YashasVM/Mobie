package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtChannelTextTest {
    private val reasoning = setOf("analysis", "thinking", "cot")

    @Test
    fun emptyChannelsProduceEmptyText() {
        assertEquals(LiteRtChannelText.Split("", ""), LiteRtChannelText.split(emptyMap(), reasoning))
    }

    @Test
    fun visibleOnlyChannelsStayVisible() {
        val split = LiteRtChannelText.split(mapOf("text" to "Hello", "other" to " world"), reasoning)
        assertEquals(LiteRtChannelText.Split("", "Hello world"), split)
    }

    @Test
    fun reasoningChannelsAreSeparatedCaseInsensitively() {
        val split = LiteRtChannelText.split(
            mapOf("Analysis" to "think ", "text" to "answer", "THINKING" to "more"),
            reasoning,
        )
        assertEquals(LiteRtChannelText.Split("think more", "answer"), split)
    }

    @Test
    fun reasoningOnlyChunkHasNoVisibleText() {
        val split = LiteRtChannelText.split(mapOf("cot" to "step"), reasoning)
        assertEquals(LiteRtChannelText.Split("step", ""), split)
    }

    @Test
    fun singleVisibleChannelPreservesWhitespaceAndUnicode() {
        assertEquals(
            LiteRtChannelText.Split("", "  Hello 🌍\n"),
            LiteRtChannelText.split(mapOf("text" to "  Hello 🌍\n"), reasoning),
        )
    }

    @Test
    fun singleMixedCaseReasoningChannelStaysSeparate() {
        assertEquals(
            LiteRtChannelText.Split("step", ""),
            LiteRtChannelText.split(mapOf("ThInKiNg" to "step"), reasoning),
        )
    }

    @Test
    fun emptySingleChannelHasNoText() {
        for (channel in listOf("text", "Analysis")) {
            assertEquals(LiteRtChannelText.Split("", ""), LiteRtChannelText.split(mapOf(channel to ""), reasoning))
        }
    }
    @Test
    fun singleVisibleChannelReusesImmutableInput() {
        val text = String(charArrayOf('h', 'i'))
        val split = LiteRtChannelText.split(mapOf("text" to text), reasoning)
        assertEquals("", split.reasoning)
        assertSame(text, split.visible)
    }

    @Test
    fun singleReasoningChannelReusesInputAndKeepsCaseInsensitiveClassification() {
        val text = String(charArrayOf('t', 'h', 'i', 'n', 'k'))
        val split = LiteRtChannelText.split(mapOf("ANALYSIS" to text), reasoning)
        assertEquals("", split.visible)
        assertSame(text, split.reasoning)
    }

    @Test
    fun singleEmptyChannelProducesEmptyText() {
        assertEquals(LiteRtChannelText.Split("", ""), LiteRtChannelText.split(mapOf("text" to ""), reasoning))
        assertEquals(LiteRtChannelText.Split("", ""), LiteRtChannelText.split(mapOf("analysis" to ""), reasoning))
    }

}
