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
    const val KEY_VERIFIED_LENGTH = "verifiedLength"
    const val KEY_VERIFIED_LAST_MODIFIED = "verifiedLastModified"

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
