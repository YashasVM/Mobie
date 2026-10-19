package dev.yashasvm.mobie.core.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real pinned SDK classes after Android's DEX conversion, not a host-JVM substitute. */
@RunWith(AndroidJUnit4::class)
class LiteRtMessageTextTest {
    private val reasoning = setOf("analysis", "thinking", "cot")

    @Test fun singleContentTextReusesOriginalStringWithoutTrimmingOrCopying() {
        val text = String("  Hello 🌍\n\t${"x".repeat(8_192)}  ".toCharArray())
        val chunk = Message.model(Contents.of(Content.Text(text)))
        val answer = visibleAnswer(chunk)
        assertEquals(chunk.toString(), answer)
        assertSame(text, answer)
    }

    @Test fun emptySingleContentTextStaysEmpty() {
        val text = String(charArrayOf())
        val chunk = Message.model(Contents.of(Content.Text(text)))
        assertEquals("", visibleAnswer(chunk))
        assertSame(text, visibleAnswer(chunk))
    }

    @Test fun emptyContentsKeepSdkRendering() {
        val chunk = Message.model(Contents.of(emptyList<Content>()))
        assertEquals(chunk.toString(), visibleAnswer(chunk))
    }

    @Test fun multipleTextContentsKeepSdkConcatenationOrder() {
        val chunk = Message.model(Contents.of(Content.Text(" first "), Content.Text("🌍\n"), Content.Text("last")))
        assertEquals(" first 🌍\nlast", visibleAnswer(chunk))
        assertEquals(chunk.toString(), visibleAnswer(chunk))
    }

    @Test fun singleNonTextAndMixedContentsKeepSdkRendering() {
        val image = Content.ImageFile("/fixture/image.jpg")
        val tool = Content.ToolResponse("example", mapOf("value" to "result"))
        for (contents in listOf(
            Contents.of(image),
            Contents.of(tool),
            Contents.of(Content.Text("before"), image, Content.Text("after")),
            Contents.of(tool, Content.Text("after")),
        )) {
            val chunk = Message.model(contents)
            assertEquals(chunk.toString(), visibleAnswer(chunk))
        }
    }

    @Test fun visibleChannelTakesPrecedenceOverContentAndReasoning() {
        val visible = String("visible answer".toCharArray())
        val chunk = Message.model(
            contents = Contents.of("fallback must not appear"),
            channels = linkedMapOf("analysis" to "private reasoning", "text" to visible),
        )
        val split = LiteRtChannelText.split(chunk.channels, reasoning)
        assertSame(split.visible, LiteRtMessageText.visibleAnswer(chunk, split))
        assertEquals(visible, visibleAnswer(chunk))
    }

    @Test fun reasoningOnlyChannelsSuppressContentFallback() {
        for (name in listOf("analysis", "ThInKiNg", "COT")) {
            val chunk = Message.model(
                contents = Contents.of("must not leak into visible output"),
                channels = mapOf(name to "reasoning"),
            )
            assertEquals("", visibleAnswer(chunk))
        }
    }

    @Test fun emptyChannelTextStillFallsBackToContentWithoutCopying() {
        val text = String("fallback".toCharArray())
        val chunk = Message.model(Contents.of(text), channels = mapOf("analysis" to "", "text" to ""))
        assertSame(text, visibleAnswer(chunk))
    }

    @Test fun allRepresentativeShapesMatchPreviousRenderingExpression() {
        val contents = listOf(
            Contents.of(emptyList<Content>()), Contents.of(""), Contents.of(" text 🌍\n"),
            Contents.of(Content.Text("one"), Content.Text("two")),
            Contents.of(Content.ImageFile("/fixture/image.jpg")),
            Contents.of(Content.Text("before"), Content.ImageFile("/fixture/image.jpg"), Content.Text("after")),
        )
        val channels = listOf(
            emptyMap(), mapOf("text" to ""), mapOf("analysis" to ""),
            mapOf("text" to "answer"), mapOf("analysis" to "reasoning"),
            linkedMapOf("analysis" to "reasoning", "text" to "answer", "other" to "more"),
        )
        for (content in contents) for (channel in channels) {
            val chunk = Message.model(content, channels = channel)
            val split = LiteRtChannelText.split(chunk.channels, reasoning)
            val previous = split.visible.ifEmpty { if (split.reasoning.isEmpty()) chunk.toString() else "" }
            assertEquals(previous, LiteRtMessageText.visibleAnswer(chunk, split))
        }
    }

    private fun visibleAnswer(chunk: Message): String =
        LiteRtMessageText.visibleAnswer(chunk, LiteRtChannelText.split(chunk.channels, reasoning))
}
