package dev.yashasvm.mobie.core.attachment

import dev.yashasvm.mobie.core.runtime.GenerationContextPolicy
import dev.yashasvm.mobie.core.runtime.utf8ByteCount

/**
 * Decides whether a local file may become prompt text, and how much of it fits.
 *
 * Pure Kotlin: the Android reader supplies the file name, MIME type and a bounded byte array.
 * Only UTF-8 text is accepted. PDFs are refused until a small, well-maintained text extractor is
 * reviewed. Budgets are UTF-8 byte counts, matching [GenerationContextPolicy]'s conservative
 * token bound, so an attachment cannot push a prompt past the model's context window.
 */
object AttachmentPolicy {
    /** Hard cap on the size of a file the app will read at all. */
    const val MAX_FILE_BYTES: Long = 2L * 1024 * 1024

    /** Upper bound on text kept per message, so persisted chat history stays small. */
    const val MAX_STORED_TEXT_BYTES: Int = 16 * 1024

    /** Below this, a file would be only a marker, so it is refused instead. */
    const val MIN_TEXT_BUDGET_BYTES: Int = 64

    const val TRUNCATION_MARKER: String = "\n[truncated]"

    private const val MAX_NAME_CHARS = 120
    private const val FALLBACK_NAME = "document"
    private const val DEFAULT_MIME = "text/plain"

    /** Replacement characters above this share of the decoded text mean the file is not text. */
    private const val MAX_REPLACEMENT_PERCENT = 5
    /** Stray control characters above this share mean the file is binary. */
    private const val MAX_CONTROL_PERCENT = 1

    private val TEXT_EXTENSIONS = setOf(
        "txt", "text", "md", "markdown", "rst", "csv", "tsv", "json", "jsonl", "ndjson", "xml", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "properties", "log", "gradle", "kts", "kt", "java", "scala", "groovy",
        "py", "js", "jsx", "mjs", "ts", "tsx", "c", "h", "cc", "cpp", "cxx", "hpp", "cs", "go", "rs", "rb",
        "php", "swift", "m", "dart", "lua", "pl", "r", "sh", "bash", "zsh", "ps1", "sql", "html", "htm",
        "css", "scss", "vue", "svelte", "tex", "diff", "patch",
    )

    private val TEXT_MIME_EXACT = setOf(
        "application/json", "application/xml", "application/x-yaml", "application/yaml", "application/toml",
        "application/javascript", "application/x-javascript", "application/x-sh", "application/sql",
        "application/x-sql", "application/x-ndjson", "application/x-subrip",
    )

    private val EXTENSION_MIME = mapOf(
        "md" to "text/markdown",
        "markdown" to "text/markdown",
        "csv" to "text/csv",
        "tsv" to "text/tab-separated-values",
        "json" to "application/json",
        "xml" to "application/xml",
        "yaml" to "application/yaml",
        "yml" to "application/yaml",
    )

    /**
     * MIME filters for the document picker. Text-like types only: PDFs are not offered, and
     * octet-stream is included because many source files are reported with that type.
     */
    val PICKER_MIME_TYPES: Array<String> = arrayOf(
        "text/*",
        "application/json",
        "application/xml",
        "application/yaml",
        "application/x-yaml",
        "application/toml",
        "application/javascript",
        "application/sql",
        "application/x-sh",
        "application/x-ndjson",
        "application/octet-stream",
    )

    enum class Kind { TEXT, PDF, UNSUPPORTED }

    /** Classifies by extension first, then MIME. Some providers report octet-stream for text files. */
    fun classify(name: String, mimeType: String?): Kind {
        val extension = extensionOf(name)
        val mime = normalizedMime(mimeType)
        if (extension == "pdf" || mime == "application/pdf") return Kind.PDF
        if (extension in TEXT_EXTENSIONS) return Kind.TEXT
        if (mime != null && isTextMime(mime)) return Kind.TEXT
        return Kind.UNSUPPORTED
    }

    /**
     * Full policy for one file. [bytes] must already be bounded by the reader; oversize input is
     * rejected here as well so the policy never trusts the caller alone.
     */
    fun evaluate(
        name: String,
        mimeType: String?,
        bytes: ByteArray,
        textBudgetBytes: Int,
    ): AttachmentResult {
        val safeName = sanitizeName(name)
        when (classify(safeName, mimeType)) {
            Kind.PDF -> return reject(AttachmentRejection.PdfNotSupported)
            Kind.UNSUPPORTED -> return reject(AttachmentRejection.UnsupportedType(safeName))
            Kind.TEXT -> Unit
        }
        if (bytes.size.toLong() > MAX_FILE_BYTES) return reject(AttachmentRejection.TooLarge(MAX_FILE_BYTES))
        if (bytes.isEmpty()) return reject(AttachmentRejection.Empty)
        if (textBudgetBytes < MIN_TEXT_BUDGET_BYTES) return reject(AttachmentRejection.NoContextRoom)
        if (bytes.indexOf(0.toByte()) >= 0) return reject(AttachmentRejection.Binary)

        val start = if (hasUtf8Bom(bytes)) UTF8_BOM_LENGTH else 0
        val decoded = String(bytes, start, bytes.size - start, Charsets.UTF_8)
        if (decoded.isEmpty()) return reject(AttachmentRejection.Empty)
        if (looksBinary(decoded)) return reject(AttachmentRejection.Binary)

        val (text, truncated) = fitText(decoded, textBudgetBytes)
        return AttachmentResult.Accepted(
            DocumentAttachment(
                name = safeName,
                mimeType = resolveMime(safeName, mimeType),
                sizeBytes = bytes.size.toLong(),
                text = text,
                truncated = truncated,
            ),
        )
    }

    /**
     * Bytes of prompt text one message may spend on attachments. Half of the remaining replay
     * budget leaves room for the question, template overhead and output, and the cap keeps history
     * small on large-context models.
     */
    fun textBudgetBytes(contextWindowTokens: Int, hasImage: Boolean): Int {
        val replay = GenerationContextPolicy.replayTextBudget(contextWindowTokens, hasImage) / 2
        return minOf(replay, MAX_STORED_TEXT_BYTES.toLong()).toInt()
    }

    /** Keeps the file name safe for display and for the `name` attribute in prompts. */
    fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.filterNot { it.isISOControl() }.trim().take(MAX_NAME_CHARS)
        return cleaned.ifEmpty { FALLBACK_NAME }
    }

    fun totalTextBytes(attachments: List<DocumentAttachment>): Int =
        attachments.sumOf { it.text.utf8ByteCount() }

    private fun reject(reason: AttachmentRejection): AttachmentResult = AttachmentResult.Rejected(reason)

    private fun fitText(decoded: String, budgetBytes: Int): Pair<String, Boolean> {
        if (decoded.utf8ByteCount() <= budgetBytes) return decoded to false
        val limit = budgetBytes - TRUNCATION_MARKER.utf8ByteCount()
        var end = 0
        var used = 0
        while (end < decoded.length) {
            val codePoint = decoded.codePointAt(end)
            val size = utf8Size(codePoint)
            if (used + size > limit) break
            used += size
            end += Character.charCount(codePoint)
        }
        return (decoded.substring(0, end) + TRUNCATION_MARKER) to true
    }

    private fun looksBinary(decoded: String): Boolean {
        var replacements = 0
        var controls = 0
        for (char in decoded) {
            when {
                char == '�' -> replacements++
                char.code < 0x20 && char != '\t' && char != '\n' && char != '\r' &&
                    char != '\u000C' && char != '\u001B' -> controls++
            }
        }
        return replacements * 100 > decoded.length * MAX_REPLACEMENT_PERCENT ||
            controls * 100 > decoded.length * MAX_CONTROL_PERCENT
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun hasUtf8Bom(bytes: ByteArray): Boolean =
        bytes.size >= UTF8_BOM_LENGTH &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()

    private fun extensionOf(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        if (!base.contains('.')) return ""
        return base.substringAfterLast('.').lowercase()
    }

    private fun normalizedMime(mimeType: String?): String? =
        mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") || mime in TEXT_MIME_EXACT || mime.endsWith("+json") || mime.endsWith("+xml")

    private fun resolveMime(name: String, mimeType: String?): String {
        val mime = normalizedMime(mimeType)
        if (mime != null && isTextMime(mime)) return mime
        return EXTENSION_MIME[extensionOf(name)] ?: DEFAULT_MIME
    }

    private const val UTF8_BOM_LENGTH = 3
}
