package dev.yashasvm.mobie.data.catalog

import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.LiteRtArtifactHeader
import dev.yashasvm.mobie.core.model.inferArtifactExecutionTarget
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Reads only the header of a LiteRT-LM artifact (a few kilobytes) with HTTP range requests, so
 * the catalog can tell what a file contains before the user downloads gigabytes. Results are
 * cached for revision-pinned URLs, whose bytes cannot change.
 *
 * A failed probe returns null. Callers must exclude files that required verification.
 */
internal class LiteRtHeaderInspector(
    private val client: OkHttpClient,
    private val probeTimeoutMillis: Long = PROBE_TIMEOUT_MILLIS,
    private val token: () -> String?,
) {
    private val cache = ConcurrentHashMap<String, LiteRtArtifactHeader>()

    fun clear() = cache.clear()

    suspend fun inspect(url: String): LiteRtArtifactHeader? {
        cache[url]?.let { return it }
        return withTimeoutOrNull(probeTimeoutMillis) {
            inspectWithinDeadline(url)
        }
    }

    private suspend fun inspectWithinDeadline(url: String): LiteRtArtifactHeader? {
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
            null // Offline or interrupted; this file remains unverified.
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
        suspend fun fetch(withToken: String?): Pair<Int, ByteArray?> = coroutineScope {
            val call = client.newCall(request(withToken))
            call.timeout().timeout(probeTimeoutMillis, TimeUnit.MILLISECONDS)
            // Call cancellation closes real sockets; interruption also releases blocking body
            // sources supplied by interceptors, which may not be attached to an OkHttp socket.
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                runInterruptible(Dispatchers.IO) {
                    call.execute().use { it.code to readPrefix(it, length) }
                }
            } finally {
                cancellation.cancel()
            }
        }
        val first = fetch(saved)
        return if (first.first == 401 && !saved.isNullOrBlank()) fetch(null).second else first.second
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
        const val PROBE_TIMEOUT_MILLIS = 3_000L

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
