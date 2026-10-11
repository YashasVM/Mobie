package dev.yashasvm.mobie.core.runtime

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
}
