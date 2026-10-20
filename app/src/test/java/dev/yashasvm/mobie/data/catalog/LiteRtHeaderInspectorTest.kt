package dev.yashasvm.mobie.data.catalog

import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtHeaderInspectorTest {
    private val pinned = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
        "41c5569623a7c189d6eec3e5225260c58cedbce7/gemma-4-E2B-it.litertlm"
    private val header = checkNotNull(javaClass.getResourceAsStream("/litertlm-headers/gemma-4-E2B-it.hdr")).use { it.readBytes() }

    private class Recorded(val request: Request)

    private fun client(requests: MutableList<Recorded>, respond: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests += Recorded(chain.request())
            respond(chain.request())
        }).build()

    private fun response(request: Request, code: Int, body: ResponseBody) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body).build()

    /** Serves the fixture followed by fake weights, honoring the requested byte range. */
    private fun ranged(request: Request): Response {
        val range = Regex("bytes=0-(\\d+)").find(request.header("Range").orEmpty())
        val last = checkNotNull(range).groupValues[1].toInt()
        val file = header + ByteArray(8192)
        val bytes = file.copyOf(minOf(file.size, last + 1))
        return response(request, 206, ResponseBodyOf(bytes))
    }

    private class ResponseBodyOf(private val bytes: ByteArray) : ResponseBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = bytes.size.toLong()
        override fun source(): BufferedSource = Buffer().write(bytes)
    }

    @Test
    fun `reads only the header with an identity-encoded range request`() = runBlocking {
        val requests = CopyOnWriteArrayList<Recorded>()
        val inspector = LiteRtHeaderInspector(client(requests, ::ranged)) { null }
        assertNotNull(inspector.inspect(pinned))
        val cached = checkNotNull(inspector.inspect(pinned))
        assertTrue(cached.hasSpeculativeDrafter)
        // Second call is served from the cache of revision-pinned headers.
        assertEquals(1, requests.size)
        assertEquals("bytes=0-4095", requests.single().request.header("Range"))
        assertEquals("identity", requests.single().request.header("Accept-Encoding"))
        assertNull(requests.single().request.header("Authorization"))
    }

    @Test
    fun `fetches the exact remaining header when the first range is too short`() = runBlocking {
        val requests = CopyOnWriteArrayList<Recorded>()
        // Simulate a server that only returns the first 1 KiB, as a short read would.
        val inspector = LiteRtHeaderInspector(client(requests) { request ->
            val ranged = ranged(request)
            if (requests.size == 1) response(request, 206, ResponseBodyOf(header.copyOf(1024))) else ranged
        }) { null }
        assertNotNull(inspector.inspect(pinned))
        assertEquals(listOf("bytes=0-4095", "bytes=0-1871"), requests.map { it.request.header("Range") })
    }

    @Test
    fun `a server that ignores Range is read only up to the prefix`() = runBlocking {
        val served = AtomicLong()
        val huge = object : ResponseBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = 2_000_000_000L
            override fun source(): BufferedSource = object : InputStream() {
                private var position = 0
                override fun read(): Int {
                    served.incrementAndGet()
                    return if (position < header.size) header[position++].toInt() and 0xff else 0
                }
            }.source().buffer()
        }
        val inspector = LiteRtHeaderInspector(client(CopyOnWriteArrayList()) { response(it, 200, huge) }) { null }
        assertNotNull(inspector.inspect(pinned))
        assertTrue("read ${served.get()} bytes", served.get() < 64 * 1024)
    }

    @Test
    fun `retries anonymously after an unauthorized token and never puts the token in the URL`() = runBlocking {
        val requests = CopyOnWriteArrayList<Recorded>()
        val inspector = LiteRtHeaderInspector(client(requests) { request ->
            if (request.header("Authorization") != null) response(request, 401, ResponseBodyOf(ByteArray(0))) else ranged(request)
        }) { "hf_secret" }
        assertNotNull(inspector.inspect(pinned))
        assertEquals(listOf("Bearer hf_secret", null), requests.map { it.request.header("Authorization") })
        assertTrue(requests.none { it.request.url.toString().contains("hf_secret") })
    }

    @Test
    fun `refusals, non LiteRT files and network failures return null`() = runBlocking {
        val forbidden = LiteRtHeaderInspector(client(CopyOnWriteArrayList()) { response(it, 403, ResponseBodyOf(ByteArray(0))) }) { null }
        assertNull(forbidden.inspect(pinned))
        val notLiteRt = LiteRtHeaderInspector(client(CopyOnWriteArrayList()) {
            response(it, 206, ResponseBodyOf("<html>not a model</html>".toByteArray() + ByteArray(64)))
        }) { null }
        assertNull(notLiteRt.inspect(pinned))
        val offline = LiteRtHeaderInspector(client(CopyOnWriteArrayList()) { throw java.io.IOException("offline") }) { null }
        assertNull(offline.inspect(pinned))
    }

    private class StalledBody : ResponseBody() {
        val started = CompletableDeferred<Unit>()
        val interrupted = AtomicBoolean()
        val closed = AtomicBoolean()
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = -1L
        private val input = object : InputStream() {
            override fun read(): Int {
                started.complete(Unit)
                try {
                    CountDownLatch(1).await(60, TimeUnit.SECONDS)
                } catch (error: InterruptedException) {
                    interrupted.set(true)
                    throw IOException("Interrupted stalled body", error)
                }
                return -1
            }
            override fun close() { closed.set(true) }
        }.source().buffer()
        override fun source(): BufferedSource = input
    }

    @Test(timeout = 5_000)
    fun `stalled body returns null within probe timeout and releases the read`() = runBlocking {
        val body = StalledBody()
        val inspector = LiteRtHeaderInspector(
            client(CopyOnWriteArrayList()) { response(it, 206, body) }, probeTimeoutMillis = 250,
        ) { null }
        val start = System.nanoTime()
        assertNull(inspector.inspect(pinned))
        assertTrue("Probe exceeded bounded wait", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
        assertTrue(body.started.isCompleted)
        assertTrue(body.interrupted.get())
        assertTrue(body.closed.get())
    }

    @Test(timeout = 5_000)
    fun `caller cancellation interrupts a stalled body and cancels the call`() = runBlocking {
        val body = StalledBody()
        val call = AtomicReference<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call())
            response(chain.request(), 206, body)
        }.build()
        val inspector = LiteRtHeaderInspector(client, probeTimeoutMillis = 10_000) { null }
        val inspection = async { inspector.inspect(pinned) }
        withTimeout(2_000) { body.started.await() }
        inspection.cancelAndJoin()
        assertTrue(body.interrupted.get())
        assertTrue(body.closed.get())
        assertTrue(call.get().isCanceled())
        assertEquals(TimeUnit.MILLISECONDS.toNanos(10_000), call.get().timeout().timeoutNanos())
    }

    @Test(timeout = 5_000)
    fun `catalog phase deadline keeps completed headers and cancels unfinished reads`() = runBlocking {
        val stalled = StalledBody()
        val slow = pinned.replace("gemma-4-E2B-it.litertlm", "slow.litertlm")
        val inspector = LiteRtHeaderInspector(client(CopyOnWriteArrayList()) { request ->
            if (request.url.toString() == slow) response(request, 206, stalled) else ranged(request)
        }, probeTimeoutMillis = 10_000) { null }
        val start = System.nanoTime()
        val headers = probeCatalogHeaders(listOf(listOf(pinned, slow)), inspector, timeoutMillis = 500)
        assertEquals(setOf(pinned), headers.keys)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
        assertTrue(stalled.interrupted.get())
        assertTrue(stalled.closed.get())
    }

    @Test
    fun `catalog bounds probes per repository and per fetch`() = runBlocking {
        val requests = CopyOnWriteArrayList<Recorded>()
        val inspector = LiteRtHeaderInspector(client(requests, ::ranged)) { null }
        val targets = List(10) { repo ->
            List(10) { file -> pinned.replace("gemma-4-E2B-it.litertlm", "repo$repo-file$file.litertlm") }
        }
        val headers = probeCatalogHeaders(targets, inspector)
        assertEquals(MAX_HEADER_PROBES_PER_FETCH, headers.size)
        assertEquals(MAX_HEADER_PROBES_PER_FETCH, requests.size)
        targets.forEach { repository ->
            assertTrue(repository.count { it in headers } <= MAX_HEADER_PROBES_PER_REPO)
            assertTrue(repository.drop(MAX_HEADER_PROBES_PER_REPO).none { it in headers })
        }
    }

    @Test
    fun `unpinned URLs are not cached`() = runBlocking {
        val requests = CopyOnWriteArrayList<Recorded>()
        val inspector = LiteRtHeaderInspector(client(requests, ::ranged)) { null }
        val main = pinned.replace(Regex("/resolve/[0-9a-f]{40}/"), "/resolve/main/")
        inspector.inspect(main)
        inspector.inspect(main)
        assertEquals(2, requests.size)
    }
}
