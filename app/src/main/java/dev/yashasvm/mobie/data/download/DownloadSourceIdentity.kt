package dev.yashasvm.mobie.data.download

import java.util.Properties

/**
 * Binds installed and resumable model bytes to the exact source request that produced them.
 *
 * Hugging Face discovery normally supplies commit-pinned /resolve/<sha>/ URLs. Persisting the
 * complete source URL prevents a later repository revision from reusing an older completed file
 * or appending new-revision bytes to an old partial download merely because the model ID, file
 * name, or size stayed the same.
 */
object DownloadSourceIdentity {
    const val SOURCE_URL_PROPERTY = "sourceUrl"
    const val RESUME_METADATA_SUFFIX = ".resume"

    fun matches(properties: Properties?, sourceUrl: String): Boolean =
        properties?.getProperty(SOURCE_URL_PROPERTY)?.takeIf(String::isNotBlank) == sourceUrl

    fun canReuseCompleted(properties: Properties?, sourceUrl: String, expectedSha256: String?): Boolean =
        !expectedSha256.isNullOrBlank() || matches(properties, sourceUrl)

    fun stamp(properties: Properties, sourceUrl: String) {
        properties.setProperty(SOURCE_URL_PROPERTY, sourceUrl)
    }
}
