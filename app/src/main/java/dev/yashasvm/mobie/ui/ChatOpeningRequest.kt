package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.data.download.InstalledModelEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Reserves chat-home navigation at invocation, before any disk read or coroutine dispatch. All
 * selections share [selectionRevision], so both a newer chat-home request and an explicit model
 * selection supersede this request, regardless of which lookup finishes first.
 *
 * Call on the UI thread. Callbacks must not suspend; [select] receives the reserved revision to
 * keep through selection, plus a completion callback that is safe to call even after supersession.
 */
internal fun CoroutineScope.openLatestChat(
    selectionRevision: AtomicLong,
    readSnapshot: suspend () -> Pair<List<InstalledModelEntry>, String?>,
    onInstalled: (List<InstalledModelEntry>) -> Unit,
    onOpening: () -> Unit,
    onResolved: () -> Unit,
    onFailure: (Exception) -> Unit,
    select: (InstalledModelEntry, Long, () -> Unit) -> Unit,
): Job {
    val revision = selectionRevision.incrementAndGet()
    val resolveIfCurrent = {
        if (selectionRevision.get() == revision) onResolved()
    }
    return launch {
        if (selectionRevision.get() != revision) return@launch
        val (installed, lastUsed) = try {
            readSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (selectionRevision.get() == revision) onFailure(error)
            resolveIfCurrent()
            return@launch
        }
        if (selectionRevision.get() != revision) return@launch
        onInstalled(installed)
        val entry = chooseLaunchModel(installed, lastUsed)
        if (entry == null) {
            resolveIfCurrent()
            return@launch
        }
        if (selectionRevision.get() != revision) return@launch
        onOpening()
        select(entry, revision, resolveIfCurrent)
    }
}

/** A non-cancellable history save may finish, but it must not open a superseded model's path. */
internal suspend fun openChatAfterSessionWrite(
    selectionRevision: AtomicLong,
    revision: Long,
    writeSession: suspend () -> Boolean,
    open: suspend () -> Unit,
) {
    if (selectionRevision.get() != revision) return
    if (writeSession() && selectionRevision.get() == revision) open()
}
