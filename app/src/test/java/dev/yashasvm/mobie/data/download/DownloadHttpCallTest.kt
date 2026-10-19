package dev.yashasvm.mobie.data.download

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadHttpCallTest {
    @Test
    fun `cancelling after headers interrupts a stalled body read`() = runBlocking {
        val call = StreamingCall(stall = true)
        val download = launch(Dispatchers.IO) {
            try {
                call.useDownloadResponse { response -> response.body!!.byteStream().read() }
            } catch (_: IOException) {
                // Closing a socket interrupts its blocking read with IOException.
            }
        }
        try {
            assertTrue("Body read never started", call.readStarted.await(5, TimeUnit.SECONDS))
            download.cancel()
            assertTrue("Cancellation did not close the active call", call.cancelled.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) { download.join() }
            assertTrue(call.bodyClosed.get())
        } finally {
            call.cancel()
            download.cancel()
        }
    }

    @Test
    fun `normal completion closes response and releases cancellation watcher`() = runBlocking {
        val call = StreamingCall(stall = false)
        withTimeout(2_000) {
            call.useDownloadResponse { response -> assertTrue(response.body!!.byteStream().read() == -1) }
        }
        assertTrue(call.bodyClosed.get())
    }

    private class StreamingCall(private val stall: Boolean) : Call {
        val readStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val bodyClosed = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)
        private val request = Request.Builder().url("https://example.invalid/model.litertlm").build()

        override fun request(): Request = request
        override fun execute(): Response = error("Expected asynchronous request")
        override fun enqueue(responseCallback: Callback) {
            executed.set(true)
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    readStarted.countDown()
                    if (!stall) return -1
                    check(cancelled.await(10, TimeUnit.SECONDS)) { "Test body was not cancelled" }
                    throw IOException("Socket closed")
                }
                override fun close() { bodyClosed.set(true) }
                override fun timeout(): Timeout = Timeout.NONE
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType() = null
                override fun contentLength() = if (stall) 1L else 0L
                override fun source() = source
            }
            responseCallback.onResponse(
                this,
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body).build(),
            )
        }
        override fun cancel() { cancelled.countDown() }
        override fun isExecuted(): Boolean = executed.get()
        override fun isCanceled(): Boolean = cancelled.count == 0L
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = StreamingCall(stall)
    }
}
