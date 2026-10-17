package dev.yashasvm.mobie.core.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPolicyTest {
    private val generousBudget = 8 * 1024

    @Test
    fun `classifies common text documents and code by extension`() {
        listOf("notes.txt", "README.md", "data.csv", "x.json", "pom.xml", "ci.yaml", "ci.yml", "app.log",
            "Main.kt", "script.py", "index.TSX", "style.css", "build.gradle.kts").forEach { name ->
            assertEquals(name, AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify(name, null))
        }
    }

    @Test
    fun `classifies text by MIME when the extension is missing or unknown`() {
        assertEquals(AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify("LICENSE", "text/plain"))
        assertEquals(AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify("export.dat", "text/csv; charset=utf-8"))
        assertEquals(AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify("feed", "application/ld+json"))
        assertEquals(AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify("cfg", "application/x-yaml"))
    }

    @Test
    fun `extension wins over an octet-stream MIME for known text files`() {
        assertEquals(AttachmentPolicy.Kind.TEXT, AttachmentPolicy.classify("notes.md", "application/octet-stream"))
    }

    @Test
    fun `classifies PDFs by extension or MIME as PDF`() {
        assertEquals(AttachmentPolicy.Kind.PDF, AttachmentPolicy.classify("paper.pdf", null))
        assertEquals(AttachmentPolicy.Kind.PDF, AttachmentPolicy.classify("download", "application/pdf"))
    }

    @Test
    fun `classifies images office files and archives as unsupported`() {
        listOf("photo.jpg" to "image/jpeg", "deck.pptx" to null, "book.docx" to "application/vnd.openxmlformats",
            "bundle.zip" to "application/zip", "model.gguf" to null).forEach { (name, mime) ->
            assertEquals(name, AttachmentPolicy.Kind.UNSUPPORTED, AttachmentPolicy.classify(name, mime))
        }
    }

    @Test
    fun `PDF is rejected with a plain explanation`() {
        val result = AttachmentPolicy.evaluate("paper.pdf", "application/pdf", "%PDF-1.4".toByteArray(), generousBudget)
        val reason = rejected(result)
        assertEquals(AttachmentRejection.PdfNotSupported, reason)
        assertTrue(reason.message.contains(".txt"))
    }

    @Test
    fun `unsupported type is rejected with the file name`() {
        val reason = rejected(AttachmentPolicy.evaluate("photo.jpg", "image/jpeg", byteArrayOf(1, 2), generousBudget))
        assertEquals(AttachmentRejection.UnsupportedType("photo.jpg"), reason)
        assertTrue(reason.message.startsWith("photo.jpg"))
    }

    @Test
    fun `accepts a file exactly at the size limit and rejects one byte more`() {
        val atLimit = ByteArray(AttachmentPolicy.MAX_FILE_BYTES.toInt()) { 'a'.code.toByte() }
        val accepted = accepted(AttachmentPolicy.evaluate("big.txt", null, atLimit, AttachmentPolicy.MAX_STORED_TEXT_BYTES))
        assertTrue(accepted.truncated)

        val overLimit = ByteArray(AttachmentPolicy.MAX_FILE_BYTES.toInt() + 1) { 'a'.code.toByte() }
        val reason = rejected(AttachmentPolicy.evaluate("big.txt", null, overLimit, generousBudget))
        assertEquals(AttachmentRejection.TooLarge(AttachmentPolicy.MAX_FILE_BYTES), reason)
    }

    @Test
    fun `empty and BOM-only files are rejected as empty`() {
        assertEquals(AttachmentRejection.Empty, rejected(AttachmentPolicy.evaluate("a.txt", null, ByteArray(0), generousBudget)))
        val bomOnly = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(AttachmentRejection.Empty, rejected(AttachmentPolicy.evaluate("a.txt", null, bomOnly, generousBudget)))
    }

    @Test
    fun `NUL bytes mean binary even when the extension says text`() {
        val bytes = "PK\u0003\u0004".toByteArray() + byteArrayOf(0, 0, 0, 1) + "tail".toByteArray()
        assertEquals(AttachmentRejection.Binary, rejected(AttachmentPolicy.evaluate("data.txt", null, bytes, generousBudget)))
    }

    @Test
    fun `mostly invalid UTF-8 is rejected as binary`() {
        // Latin-1 bytes with no valid UTF-8 sequences decode to mostly replacement characters.
        val latin1 = ByteArray(200) { (0x80 + (it % 0x40)).toByte() }
        assertEquals(AttachmentRejection.Binary, rejected(AttachmentPolicy.evaluate("legacy.txt", null, latin1, generousBudget)))
    }

    @Test
    fun `a few stray invalid bytes in otherwise valid text are accepted`() {
        val text = "hello world, this is a readable note. ".repeat(20) + "\u00FF"
        val bytes = text.toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte())
        val accepted = accepted(AttachmentPolicy.evaluate("note.txt", null, bytes, generousBudget))
        assertTrue(accepted.text.contains("readable note"))
        assertFalse(accepted.truncated)
    }

    @Test
    fun `control characters above threshold mean binary`() {
        val bytes = ("\u0001\u0002\u0003\u0004".repeat(10) + "text").toByteArray()
        assertEquals(AttachmentRejection.Binary, rejected(AttachmentPolicy.evaluate("x.log", null, bytes, generousBudget)))
    }

    @Test
    fun `terminal escape codes in logs are allowed`() {
        val bytes = "\u001B[31mERROR\u001B[0m something failed\n".repeat(5).toByteArray()
        assertTrue(AttachmentPolicy.evaluate("run.log", null, bytes, generousBudget) is AttachmentResult.Accepted)
    }

    @Test
    fun `UTF-8 BOM is stripped and multibyte text is preserved`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "naïve café 日本語 🙂".toByteArray(Charsets.UTF_8)
        val accepted = accepted(AttachmentPolicy.evaluate("unicode.md", null, bytes, generousBudget))
        assertEquals("naïve café 日本語 🙂", accepted.text)
        assertEquals(bytes.size.toLong(), accepted.sizeBytes)
    }

    @Test
    fun `text within budget is kept whole and not marked truncated`() {
        val accepted = accepted(AttachmentPolicy.evaluate("a.md", null, "# Title\nbody".toByteArray(), 200))
        assertEquals("# Title\nbody", accepted.text)
        assertFalse(accepted.truncated)
        assertEquals("text/markdown", accepted.mimeType)
    }

    @Test
    fun `text over budget is truncated with the marker and never exceeds the budget`() {
        val source = "abcdefghij".repeat(100)
        val budget = 200
        val accepted = accepted(AttachmentPolicy.evaluate("long.txt", null, source.toByteArray(), budget))

        assertTrue(accepted.truncated)
        assertTrue(accepted.text.endsWith(AttachmentPolicy.TRUNCATION_MARKER))
        assertTrue(accepted.text.toByteArray().size <= budget)
        assertTrue(accepted.text.startsWith("abcdefghij"))
        assertEquals(source.length.toLong(), accepted.sizeBytes)
    }

    @Test
    fun `truncation never splits a multibyte character or surrogate pair`() {
        val source = "🙂".repeat(50) // 4 bytes each, a surrogate pair in UTF-16
        for (budget in 64..120) {
            val accepted = accepted(AttachmentPolicy.evaluate("emoji.txt", null, source.toByteArray(), budget))
            val body = accepted.text.removeSuffix(AttachmentPolicy.TRUNCATION_MARKER)
            assertEquals("budget $budget", 0, body.length % 2)
            assertTrue("budget $budget", accepted.text.toByteArray().size <= budget)
            assertTrue(body.codePoints().allMatch { it == SMILEY })
        }
    }

    @Test
    fun `budget too small for a useful file is refused with a context message`() {
        val reason = rejected(AttachmentPolicy.evaluate("a.txt", null, "hello".toByteArray(), AttachmentPolicy.MIN_TEXT_BUDGET_BYTES - 1))
        assertEquals(AttachmentRejection.NoContextRoom, reason)
        assertTrue(reason.message.contains("context window"))
    }

    @Test
    fun `text budget scales with context window and respects the storage cap`() {
        val small = AttachmentPolicy.textBudgetBytes(4_096, hasImage = false)
        val withImage = AttachmentPolicy.textBudgetBytes(4_096, hasImage = true)
        assertEquals((4_096 - 512 - 128 - 16) / 2, small)
        assertTrue(withImage < small)

        val large = AttachmentPolicy.textBudgetBytes(131_072, hasImage = false)
        assertEquals(AttachmentPolicy.MAX_STORED_TEXT_BYTES, large)

        assertEquals(0, AttachmentPolicy.textBudgetBytes(512, hasImage = false))
    }

    @Test
    fun `file names are stripped of paths and control characters with a fallback`() {
        assertEquals("notes.md", AttachmentPolicy.sanitizeName("/storage/emulated/0/Download/notes.md"))
        assertEquals("notes.md", AttachmentPolicy.sanitizeName("C:\\Users\\me\\notes.md"))
        assertEquals("ab.txt", AttachmentPolicy.sanitizeName("a\u0007b.txt"))
        assertEquals("document", AttachmentPolicy.sanitizeName("   "))
        assertEquals(120, AttachmentPolicy.sanitizeName("x".repeat(300) + ".txt").length)
    }

    @Test
    fun `unknown MIME falls back to a text type matching the extension`() {
        assertEquals("application/json", accepted(AttachmentPolicy.evaluate("a.json", null, "{}".toByteArray(), 200)).mimeType)
        assertEquals("text/plain", accepted(AttachmentPolicy.evaluate("README", "text/plain", "x".toByteArray(), 200)).mimeType)
    }

    @Test
    fun `meta projection exposes name size and truncation only`() {
        val attachment = DocumentAttachment("a.txt", "text/plain", 12, "body", truncated = true)
        assertEquals(AttachmentMeta("a.txt", 12, true), attachment.meta)
    }

    private fun accepted(result: AttachmentResult): DocumentAttachment =
        (result as? AttachmentResult.Accepted)?.attachment
            ?: throw AssertionError("expected accepted but was $result")

    private fun rejected(result: AttachmentResult): AttachmentRejection =
        (result as? AttachmentResult.Rejected)?.reason
            ?: throw AssertionError("expected rejected but was $result")

    private companion object {
        const val SMILEY = 0x1F642
    }
}
