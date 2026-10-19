package dev.yashasvm.mobie.data.download

import dev.yashasvm.mobie.data.catalog.awaitResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response

/** Keeps cancellation connected to the socket until the response body has finished streaming. */
internal suspend inline fun <T> Call.useDownloadResponse(block: (Response) -> T): T {
    // awaitResponse cancels while awaiting headers, but its continuation is complete once the
    // response is delivered. A body read can then block until the network timeout. This child
    // cancels the socket even while that read is blocked. Unconfined cleanup does not depend on
    // the blocked download dispatcher becoming available; Call.cancel is thread-safe/nonblocking.
    val cancellationWatcher = CoroutineScope(currentCoroutineContext()).launch(
        context = Dispatchers.Unconfined,
        start = CoroutineStart.UNDISPATCHED,
    ) {
        try {
            awaitCancellation()
        } finally {
            this@useDownloadResponse.cancel()
        }
    }
    try {
        return awaitResponse().use(block)
    } finally {
        cancellationWatcher.cancel()
    }
}
