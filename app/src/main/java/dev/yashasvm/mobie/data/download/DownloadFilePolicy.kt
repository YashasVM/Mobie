package dev.yashasvm.mobie.data.download

import java.io.File
import java.security.MessageDigest

internal object DownloadFilePolicy {
    const val METADATA_FILE = ".model.properties"

    fun storageKey(modelId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(modelId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(20)

    fun safeFileName(fileName: String): String = File(fileName).name
        .takeIf { it.isNotBlank() && it !in setOf(".", "..") }
        ?: "model.bin"

    fun storageFileName(fileName: String): String {
        val safe = safeFileName(fileName)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(fileName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        val extensionAt = safe.lastIndexOf('.')
        return if (extensionAt > 0) {
            "${safe.substring(0, extensionAt)}-$hash${safe.substring(extensionAt)}"
        } else {
            "$safe-$hash"
        }
    }

    fun remainingBytes(expectedSize: Long, partialSize: Long): Long =
        (expectedSize - partialSize).coerceAtLeast(0)

    fun hasSpaceForRemaining(totalBytes: Long, downloadedBytes: Long, usableSpaceBytes: Long): Boolean =
        totalBytes <= 0 || remainingBytes(totalBytes, downloadedBytes) <= usableSpaceBytes
}
