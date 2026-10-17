package dev.yashasvm.mobie.ui

// ---------------------------------------------------------------------------------------------
// Markdown. A deliberately small subset (bold, inline code, fenced code, lists, headings, rules)
// parsed per line so half-streamed input never throws: an unclosed ``` becomes an open code
// block and an unclosed ** or ` stays literal text until its closing marker arrives.

internal data class MdSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

internal sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class ListItem(val marker: String, val ordered: Boolean, val depth: Int, val spans: List<MdSpan>) : MdBlock
    data class Code(val language: String, val code: String, val closed: Boolean) : MdBlock
    data object Rule : MdBlock
}

private val HeadingLine = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val BulletLine = Regex("^(\\s*)[-*•]\\s+(.*)$")
private val OrderedLine = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val RuleLine = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

internal fun parseChatMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(parseInlineMarkdown(paragraph.toString()))
            paragraph.clear()
        }
    }
    val lines = text.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().takeWhile { !it.isWhitespace() && it != '`' }
            val code = mutableListOf<String>()
            var closed = false
            i++
            while (i < lines.size) {
                if (lines[i].trimStart().startsWith("```")) {
                    closed = true
                    break
                }
                code += lines[i]
                i++
            }
            blocks += MdBlock.Code(language, code.joinToString("\n").trimEnd('\n'), closed)
            i++ // Skip the closing fence.
            continue
        }
        val heading = HeadingLine.matchEntire(line)
        val bullet = BulletLine.matchEntire(line)
        val ordered = OrderedLine.matchEntire(line)
        when {
            line.isBlank() -> flushParagraph()
            RuleLine.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            heading != null -> {
                flushParagraph()
                val (hashes, content) = heading.destructured
                blocks += MdBlock.Heading(hashes.length, parseInlineMarkdown(content.trimEnd().trimEnd('#').trimEnd()))
            }
            bullet != null -> {
                flushParagraph()
                val (indent, content) = bullet.destructured
                blocks += MdBlock.ListItem("•", ordered = false, depth = listDepth(indent), spans = parseInlineMarkdown(content.trimEnd()))
            }
            ordered != null -> {
                flushParagraph()
                val (indent, number, content) = ordered.destructured
                blocks += MdBlock.ListItem("$number.", ordered = true, depth = listDepth(indent), spans = parseInlineMarkdown(content.trimEnd()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trimEnd())
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun listDepth(indent: String): Int = (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

internal fun parseInlineMarkdown(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            spans += MdSpan(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flushPlain()
                spans += MdSpan(text.substring(i + 1, end), code = true)
                i = end + 1
                continue
            }
        } else if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flushPlain()
                spans += parseInlineMarkdown(text.substring(i + 2, end)).map { it.copy(bold = true) }
                i = end + 2
                continue
            }
        }
        plain.append(text[i])
        i++
    }
    flushPlain()
    // Merge neighbours with identical styling so the annotated string stays small.
    return spans.fold(mutableListOf()) { merged, span ->
        val last = merged.lastOrNull()
        if (last != null && last.bold == span.bold && last.code == span.code) {
            merged[merged.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            merged += span
        }
        merged
    }
}
