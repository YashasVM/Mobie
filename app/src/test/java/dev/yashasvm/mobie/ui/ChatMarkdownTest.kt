package dev.yashasvm.mobie.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownTest {
    @Test
    fun `plain text becomes one paragraph keeping single line breaks`() {
        val blocks = parseChatMarkdown("Hello there.\nSecond line.")

        assertEquals(listOf(MdBlock.Paragraph(listOf(MdSpan("Hello there.\nSecond line.")))), blocks)
    }

    @Test
    fun `blank lines split paragraphs`() {
        val blocks = parseChatMarkdown("One.\n\n\nTwo.")

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Paragraph })
    }

    @Test
    fun `bold and inline code are styled`() {
        val spans = parseInlineMarkdown("Use **care** with `rm -rf` here")

        assertEquals(
            listOf(
                MdSpan("Use "),
                MdSpan("care", bold = true),
                MdSpan(" with "),
                MdSpan("rm -rf", code = true),
                MdSpan(" here"),
            ),
            spans,
        )
    }

    @Test
    fun `unclosed bold and code stay literal while streaming`() {
        assertEquals(listOf(MdSpan("This is **impor")), parseInlineMarkdown("This is **impor"))
        assertEquals(listOf(MdSpan("Run `npm ins")), parseInlineMarkdown("Run `npm ins"))
        assertEquals(listOf(MdSpan("``")), parseInlineMarkdown("``"))
        assertEquals(listOf(MdSpan("****")), parseInlineMarkdown("****"))
    }

    @Test
    fun `unclosed fence is an open code block`() {
        val blocks = parseChatMarkdown("Here:\n```kotlin\nval x = 1\nval y")

        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Here:"))), blocks[0])
        assertEquals(MdBlock.Code("kotlin", "val x = 1\nval y", closed = false), blocks[1])
    }

    @Test
    fun `closed fence keeps content verbatim and text continues after it`() {
        val blocks = parseChatMarkdown("```\n**not bold**\n- not a list\n```\nAfter")

        assertEquals(MdBlock.Code("", "**not bold**\n- not a list", closed = true), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("After"))), blocks[1])
    }

    @Test
    fun `a lone opening fence does not crash`() {
        listOf("`", "``", "```", "```py", "```\n", "#", "# ", "- ", "1.", "**").forEach { partial ->
            parseChatMarkdown(partial)
        }
        val fence = parseChatMarkdown("```")
        assertEquals(listOf(MdBlock.Code("", "", closed = false)), fence)
    }

    @Test
    fun `every prefix of a reply parses`() {
        val reply = "# Plan\n\n1. **Pack** the `bag`\n2. Go\n   - nested\n\n```sh\necho hi\n```\n---\nDone"
        for (end in 0..reply.length) parseChatMarkdown(reply.substring(0, end))
    }

    @Test
    fun `lists and headings are recognised`() {
        val blocks = parseChatMarkdown("## Steps\n- first\n* second\n  - nested\n1. one\n10) ten")

        assertEquals(MdBlock.Heading(2, listOf(MdSpan("Steps"))), blocks[0])
        assertEquals(MdBlock.ListItem("•", ordered = false, depth = 0, spans = listOf(MdSpan("first"))), blocks[1])
        assertEquals(MdBlock.ListItem("•", ordered = false, depth = 0, spans = listOf(MdSpan("second"))), blocks[2])
        assertEquals(MdBlock.ListItem("•", ordered = false, depth = 1, spans = listOf(MdSpan("nested"))), blocks[3])
        assertEquals(MdBlock.ListItem("1.", ordered = true, depth = 0, spans = listOf(MdSpan("one"))), blocks[4])
        assertEquals(MdBlock.ListItem("10.", ordered = true, depth = 0, spans = listOf(MdSpan("ten"))), blocks[5])
    }

    @Test
    fun `bold at line start is not a bullet and rules are separate`() {
        val blocks = parseChatMarkdown("**Note:** read this\n***\n2*3*4 = 24")

        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Note:", bold = true), MdSpan(" read this"))), blocks[0])
        assertEquals(MdBlock.Rule, blocks[1])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("2*3*4 = 24"))), blocks[2])
    }

    @Test
    fun `hash without space is plain text`() {
        val blocks = parseChatMarkdown("#hashtag")

        assertFalse(blocks.single() is MdBlock.Heading)
    }
}
