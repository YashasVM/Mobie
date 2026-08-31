package dev.yashasvm.mobie.data.catalog

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpCoroutinesTest {
    @Test
    fun `cancelling coroutine cancels active http call`() = runBlocking {
        val call = HangingCall()
        val request = async { call.awaitResponse() }

        yield()
        request.cancelAndJoin()

        assertTrue(call.isCanceled())
    }

    private class HangingCall : Call {
        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)
        private var callback: Callback? = null
        private val request = Request.Builder().url("https://example.invalid/model").build()

        override fun request(): Request = request

        override fun execute(): Response = error("Synchronous execute should not be used")

        override fun enqueue(responseCallback: Callback) {
            executed.set(true)
            callback = responseCallback
        }

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) {
                callback?.onFailure(this, IOException("Canceled"))
            }
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = HangingCall()
    }
}
