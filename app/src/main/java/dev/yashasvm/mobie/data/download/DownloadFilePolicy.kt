package dev.yashasvm.mobie.data.download

import java.io.File
import java.security.MessageDigest

internal object DownloadFilePolicy {
    const val METADATA_FILE = ".model.properties"

    fun storageKey(modelId: String): String = sha256Prefix(modelId, 20)

    fun safeFileName(fileName: String): String = File(fileName).name
        .takeIf { it.isNotBlank() && it !in setOf(".", "..") }
        ?: "model.bin"

    fun storageFileName(fileName: String): String {
        val safe = safeFileName(fileName)
        val hash = fileIdentity(fileName)
        val extensionAt = safe.lastIndexOf('.')
        return if (extensionAt > 0) {
            "${safe.substring(0, extensionAt)}-$hash${safe.substring(extensionAt)}"
        } else {
            "$safe-$hash"
        }
    }

    fun artifactMetadataFile(directory: File, fileName: String): File =
        File(directory, ".artifact-${fileIdentity(fileName)}.properties")

    fun metadataPartialFile(destination: File, operationId: String): File =
        File(destination.parentFile, "${destination.name}.$operationId.part")

    fun workKey(modelId: String, fileName: String): String =
        "model-${storageKey(modelId)}-${fileIdentity(fileName)}"

    fun remainingBytes(expectedSize: Long, partialSize: Long): Long =
        (expectedSize - partialSize).coerceAtLeast(0)

    fun hasSpaceForRemaining(totalBytes: Long, downloadedBytes: Long, usableSpaceBytes: Long): Boolean =
        totalBytes <= 0 || remainingBytes(totalBytes, downloadedBytes) <= usableSpaceBytes

    private fun fileIdentity(fileName: String): String = sha256Prefix(fileName, 12)

    private fun sha256Prefix(value: String, characters: Int): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(characters)
}
