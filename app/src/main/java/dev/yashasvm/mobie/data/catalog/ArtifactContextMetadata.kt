package dev.yashasvm.mobie.data.catalog

internal fun parseArtifactContextWindows(modelCard: String): Map<String, Int> {
    val contexts = linkedMapOf<String, Int>()
    var fileColumn = -1
    var contextColumn = -1

    modelCard.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (!line.startsWith('|')) return@forEach
        val cells = line.trim('|').split('|').map(String::trim)
        if (cells.size < 2) return@forEach

        val normalizedHeaders = cells.map { it.lowercase().replace("`", "").trim() }
        val possibleFileColumn = normalizedHeaders.indexOfFirst {
            it == "file" || it == "artifact" || it == "filename" || it == "model file"
        }
        val possibleContextColumn = normalizedHeaders.indexOfFirst {
            it == "context" || it == "context length" || it == "context window" ||
                it == "context tokens" || it == "max context"
        }
        if (possibleFileColumn >= 0 && possibleContextColumn >= 0) {
            fileColumn = possibleFileColumn
            contextColumn = possibleContextColumn
            return@forEach
        }

        if (fileColumn < 0 || contextColumn < 0 ||
            fileColumn >= cells.size || contextColumn >= cells.size
        ) return@forEach

        val fileName = extractLiteRtFileName(cells[fileColumn]) ?: return@forEach
        val contextTokens = parseContextTokenCount(cells[contextColumn]) ?: return@forEach
        contexts[fileName] = contextTokens
    }

    return contexts
}

private fun extractLiteRtFileName(cell: String): String? {
    val backtick = Regex("`([^`]+\\.litertlm)`", RegexOption.IGNORE_CASE)
        .find(cell)?.groupValues?.getOrNull(1)
    if (backtick != null) return backtick

    val markdownLink = Regex("\\[([^]]+\\.litertlm)]\\([^)]*\\)", RegexOption.IGNORE_CASE)
        .find(cell)?.groupValues?.getOrNull(1)
    if (markdownLink != null) return markdownLink

    return Regex("([^\\s|]+\\.litertlm)", RegexOption.IGNORE_CASE)
        .find(cell)?.groupValues?.getOrNull(1)
}

private fun parseContextTokenCount(cell: String): Int? {
    val normalized = cell.replace(",", "").trim()
    val match = Regex("(?i)(\\d{1,7})\\s*([km]?)").find(normalized) ?: return null
    val value = match.groupValues[1].toLongOrNull() ?: return null
    val multiplier = when (match.groupValues[2].lowercase()) {
        "k" -> 1024L
        "m" -> 1024L * 1024L
        else -> 1L
    }
    val tokens = value * multiplier
    return tokens.takeIf { it in 128L..1_048_576L }?.toInt()
}
