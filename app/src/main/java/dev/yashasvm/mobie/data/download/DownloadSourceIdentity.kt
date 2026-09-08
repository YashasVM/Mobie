package dev.yashasvm.mobie.data.download

import java.util.Properties

/**
 * Binds installed and resumable model bytes to the exact source request that produced them.
 *
 * Hugging Face discovery normally supplies commit-pinned /resolve/<sha>/ URLs. Persisting the
 * complete source URL prevents a later repository revision from reusing an older completed file
 * or appending new-revision bytes to an old partial download merely because the model ID, file
 * name, or size stayed the same.
 *
 * URL equality alone is not enough for checksum-less artifacts: a mutable URL can serve different
 * bytes between requests. Only commit-pinned Hugging Face resolve URLs are treated as immutable
 * identities for unverified completed-file reuse and partial-download recovery.
 */
object DownloadSourceIdentity {
    const val SOURCE_URL_PROPERTY = "sourceUrl"
    const val RESOLVED_LENGTH_PROPERTY = "resolvedLength"
    const val RESUME_METADATA_SUFFIX = ".resume"

    private val HUGGING_FACE_COMMIT_RESOLVE =
        Regex("^https://huggingface\\.co/[^/]+/[^/]+/resolve/[0-9a-fA-F]{40}(?:/|$)")

    fun isImmutable(sourceUrl: String): Boolean = HUGGING_FACE_COMMIT_RESOLVE.containsMatchIn(sourceUrl)

    fun matches(properties: Properties?, sourceUrl: String): Boolean =
        isImmutable(sourceUrl) &&
            properties?.getProperty(SOURCE_URL_PROPERTY)?.takeIf(String::isNotBlank) == sourceUrl

    fun canReuseCompleted(properties: Properties?, sourceUrl: String, expectedSha256: String?): Boolean =
        !expectedSha256.isNullOrBlank() || matches(properties, sourceUrl)

    /**
     * A completed file may be recovered from an interrupted finalization only when the sidecar
     * identifies the same immutable source. Mutable URLs are deliberately restarted instead of
     * risking a file assembled from bytes belonging to different server-side revisions.
     */
    fun canRecoverInstalled(resumeProperties: Properties?, sourceUrl: String): Boolean =
        matches(resumeProperties, sourceUrl)

    fun resolvedLength(properties: Properties?): Long? = properties
        ?.getProperty(RESOLVED_LENGTH_PROPERTY)
        ?.toLongOrNull()
        ?.takeIf { it > 0 }

    fun stamp(properties: Properties, sourceUrl: String, resolvedLength: Long? = null) {
        properties.setProperty(SOURCE_URL_PROPERTY, sourceUrl)
        if (resolvedLength != null && resolvedLength > 0) {
            properties.setProperty(RESOLVED_LENGTH_PROPERTY, resolvedLength.toString())
        } else {
            properties.remove(RESOLVED_LENGTH_PROPERTY)
        }
    }
}
