package dev.yashasvm.mobie.data.catalog

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Executes an OkHttp call without pinning an obsolete request after its coroutine is cancelled. */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }

            override fun onFailure(call: Call, error: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(error)
            }
        },
    )
}
