package dev.yashasvm.mobie.data.download

import java.io.File
import java.util.Properties

/**
 * Records the exact file fingerprint that was SHA-256 verified at download completion.
 *
 * Multi-gigabyte model files should not be read end-to-end every time the catalog checks whether a
 * download is installed. If size or mtime changes we fall back to a real SHA-256 check; unchanged
 * app-private files can reuse the verification performed by the download worker.
 */
internal object ModelFileVerification {
    const val KEY_INSTALLED_LENGTH = "installedLength"
    const val KEY_VERIFIED_LENGTH = "verifiedLength"
    const val KEY_VERIFIED_LAST_MODIFIED = "verifiedLastModified"

    fun stampInstalledLength(properties: Properties, file: File) {
        properties.setProperty(KEY_INSTALLED_LENGTH, file.length().toString())
    }

    /**
     * Rejects silent truncation/replacement for newly installed artifacts even when Hugging Face did
     * not publish a checksum. Legacy metadata without an installedLength remains readable.
     */
    fun matchesInstalledLength(properties: Properties?, file: File): Boolean {
        if (!file.isFile) return false
        val installedLength = properties?.getProperty(KEY_INSTALLED_LENGTH)?.toLongOrNull()
            ?: return true
        if (properties.getProperty("fileName")?.let { it != file.name } == true) return false
        return installedLength >= 0 && file.length() == installedLength
    }

    fun stamp(properties: Properties, file: File) {
        properties.setProperty(KEY_VERIFIED_LENGTH, file.length().toString())
        properties.setProperty(KEY_VERIFIED_LAST_MODIFIED, file.lastModified().toString())
    }

    fun canReuseShaVerification(properties: Properties, file: File, expectedSha: String): Boolean {
        if (!file.isFile || expectedSha.isBlank()) return false
        if (!properties.getProperty("sha256").orEmpty().equals(expectedSha, ignoreCase = true)) return false
        if (properties.getProperty("fileName") != file.name) return false
        val verifiedLength = properties.getProperty(KEY_VERIFIED_LENGTH)?.toLongOrNull() ?: return false
        val verifiedLastModified = properties.getProperty(KEY_VERIFIED_LAST_MODIFIED)?.toLongOrNull() ?: return false
        return file.length() == verifiedLength && file.lastModified() == verifiedLastModified
    }
}
