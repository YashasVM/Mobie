package dev.yashasvm.mobie.data.download

data class ContentRange(
    val start: Long,
    val endInclusive: Long,
    val totalBytes: Long?,
)

object DownloadResponsePolicy {
    private val contentRangePattern = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$")

    fun parseContentRange(value: String?): ContentRange? {
        val match = value?.trim()?.let(contentRangePattern::matchEntire) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (start < 0 || end < start) return null
        if (total != null && (total <= 0 || end >= total)) return null
        return ContentRange(start, end, total)
    }

    fun isValidResumeResponse(
        contentRangeHeader: String?,
        expectedStart: Long,
        expectedTotalBytes: Long,
    ): Boolean {
        val range = parseContentRange(contentRangeHeader) ?: return false
        if (range.start != expectedStart) return false
        if (range.totalBytes == null) return false
        if (expectedTotalBytes > 0 && range.totalBytes != expectedTotalBytes) return false
        return true
    }

    fun resolvedTotalBytes(
        expectedTotalBytes: Long,
        contentRangeHeader: String?,
        bodyLength: Long,
        startAt: Long,
    ): Long {
        if (expectedTotalBytes > 0) return expectedTotalBytes
        parseContentRange(contentRangeHeader)?.totalBytes?.let { return it }
        return bodyLength.takeIf { it >= 0 }?.plus(startAt) ?: 0
    }

    fun isRetryableHttp(code: Int): Boolean =
        code == 408 || code == 425 || code == 429 || code in 500..599
}
