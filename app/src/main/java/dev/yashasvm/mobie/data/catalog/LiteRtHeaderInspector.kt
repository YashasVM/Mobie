package dev.yashasvm.mobie.data.catalog

import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.LiteRtArtifactHeader
import dev.yashasvm.mobie.core.model.inferArtifactExecutionTarget
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Reads only the header of a LiteRT-LM artifact (a few kilobytes) with HTTP range requests, so
 * the catalog can tell what a file contains before the user downloads gigabytes. Results are
 * cached for revision-pinned URLs, whose bytes cannot change.
 *
 * A failed probe returns null. Callers keep their filename-based behavior in that case.
 */
internal class LiteRtHeaderInspector(
    private val client: OkHttpClient,
    private val token: () -> String?,
) {
    private val cache = ConcurrentHashMap<String, LiteRtArtifactHeader>()

    fun clear() = cache.clear()

    suspend fun inspect(url: String): LiteRtArtifactHeader? {
        cache[url]?.let { return it }
        return try {
            val first = fetchPrefix(url, FIRST_REQUEST_BYTES) ?: return null
            val end = LiteRtArtifactHeader.headerEndOffset(first)
            val complete = if (first.size >= end) first else fetchPrefix(url, end) ?: return null
            LiteRtArtifactHeader.parse(complete.copyOf(end)).also { header ->
                if (isRevisionPinned(url)) cache[url] = header
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            null // Not a LiteRT-LM container or a malformed header: nothing trustworthy to report.
        } catch (_: IOException) {
            null // Offline or interrupted; the catalog falls back to filename rules.
        }
    }

    /** First [length] bytes, or null when the server refuses access. Never reads past [length]. */
    private suspend fun fetchPrefix(url: String, length: Int): ByteArray? {
        fun request(withToken: String?) = Request.Builder().url(url)
            .header("Range", "bytes=0-${length - 1}")
            // Compressed transfer would make the byte range meaningless.
            .header("Accept-Encoding", "identity")
            .apply { withToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        val saved = token()
        var response = client.newCall(request(saved)).awaitResponse()
        if (response.code == 401 && !saved.isNullOrBlank()) {
            response.close()
            response = client.newCall(request(null)).awaitResponse()
        }
        return response.use { readPrefix(it, length) }
    }

    private fun readPrefix(response: Response, length: Int): ByteArray? {
        // 206 is the expected answer. A server that ignores Range sends 200 with the whole file,
        // so read only the prefix and close the stream instead of downloading the artifact.
        if (response.code != 206 && response.code != 200) return null
        val stream = response.body?.byteStream() ?: return null
        val buffer = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = stream.read(buffer, filled, length - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private fun isRevisionPinned(url: String): Boolean =
        Regex("/resolve/[0-9a-f]{40}/").containsMatchIn(url)

    companion object {
        /** Published headers are 0.4 to 2 KiB, so one request almost always suffices. */
        const val FIRST_REQUEST_BYTES = 4_096

        /**
         * Probe only where the filename cannot be trusted: a repository with no task tag (Gemma 4
         * publishes none), or a file named for a generic backend. Names such as `-gpu` can be a
         * portable build or a GPU-only artisan build; only the header can tell them apart.
         * Files already named for a vendor or platform stay excluded without a request.
         */
        fun shouldProbe(fileName: String, repositoryTypeUnknown: Boolean): Boolean {
            if (!fileName.endsWith(".litertlm", ignoreCase = true)) return false
            if (inferArtifactExecutionTarget(fileName) != ArtifactExecutionTarget.GENERIC) return false
            return repositoryTypeUnknown || GENERIC_BACKEND_HINT.containsMatchIn(fileName.lowercase())
        }

        private val GENERIC_BACKEND_HINT = Regex("(?:^|[._-])(?:gpu|opencl)(?:[._-]|$)")
    }
}
