package dev.yashasvm.mobie.data.download

import java.io.File
import java.security.MessageDigest

internal object DownloadFilePolicy {
    fun storageKey(modelId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(modelId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(20)

    fun safeFileName(fileName: String): String = File(fileName).name
        .takeIf { it.isNotBlank() && it !in setOf(".", "..") }
        ?: "model.bin"

    fun remainingBytes(expectedSize: Long, partialSize: Long): Long =
        (expectedSize - partialSize).coerceAtLeast(0)
}
