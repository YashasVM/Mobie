package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class ChatOpeningRequestTest {
    private val first = installed("first")
    private val second = installed("second")

    @Test
    fun `request order is reserved before coroutine dispatch`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val latestLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        val latest = fixture.open(this, latestLookup)
        assertEquals(2L, fixture.revision.get())

        latestLookup.started.await()
        assertFalse(oldLookup.started.isCompleted)
        latestLookup.succeed(second)
        old.join()
        latest.join()

        assertEquals(listOf(second), fixture.state.installedModels)
        assertEquals(second.model, fixture.state.selected)
        assertEquals(2L, fixture.selections.single().revision)
    }

    @Test
    fun `slow chat lookup cannot replace a newer explicit model selection`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val old = fixture.open(this, lookup)
        lookup.started.await()
        fixture.selectExplicitly(second)
        val selectedState = fixture.state

        lookup.succeed(first)
        old.join()

        assertEquals(selectedState, fixture.state)
        assertTrue(fixture.selections.isEmpty())
    }

    @Test
    fun `newest request wins even when older lookup finishes first`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val latestLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        oldLookup.started.await()
        val latest = fixture.open(this, latestLookup)
        latestLookup.started.await()

        oldLookup.succeed(first)
        old.join()
        assertTrue(fixture.selections.isEmpty())
        assertFalse(fixture.state.launchResolved)
        assertTrue(fixture.state.installedModels.isEmpty())

        latestLookup.succeed(second)
        latest.join()
        assertEquals(second.model, fixture.state.selected)
        assertEquals(listOf(second), fixture.state.installedModels)
        assertEquals(2L, fixture.selections.single().revision)
        fixture.selections.single().complete()
        assertTrue(fixture.state.launchResolved)
        assertFalse(fixture.state.openingChat)
    }

    @Test
    fun `older lookup cannot overwrite the newer request that finished first`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val latestLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        oldLookup.started.await()
        val latest = fixture.open(this, latestLookup)
        latestLookup.started.await()

        latestLookup.succeed(second)
        latest.join()
        val latestState = fixture.state
        oldLookup.succeed(first)
        old.join()

        assertEquals(latestState, fixture.state)
        assertEquals(1, fixture.selections.size)
    }

    @Test
    fun `stale completion cannot resolve the newer selection while its chat opens`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        oldLookup.started.await()
        oldLookup.succeed(first)
        old.join()
        val oldCompletion = fixture.selections.single().complete

        val latestLookup = Lookup()
        val latest = fixture.open(this, latestLookup)
        latestLookup.started.await()
        latestLookup.succeed(second)
        latest.join()
        val openingState = fixture.state

        // beginSelection can call this from either onSuperseded or its afterSelect finally.
        oldCompletion()
        yield()
        oldCompletion()
        assertEquals(openingState, fixture.state)
        assertTrue(fixture.state.openingChat)
        assertFalse(fixture.state.launchResolved)
        assertEquals(second.model, fixture.state.selected)

        fixture.selections.last().complete()
        assertFalse(fixture.state.openingChat)
        assertTrue(fixture.state.launchResolved)
    }

    @Test
    fun `explicit navigation also invalidates an already issued completion callback`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val opening = fixture.open(this, lookup)
        lookup.started.await()
        lookup.succeed(first)
        opening.join()
        fixture.selectExplicitly(second)
        val selectedState = fixture.state

        fixture.selections.single().complete()

        assertEquals(selectedState, fixture.state)
    }

    @Test
    fun `stale empty lookup cannot clear the newer opening state`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val latestLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        oldLookup.started.await()
        val latest = fixture.open(this, latestLookup)
        latestLookup.started.await()
        latestLookup.succeed(second)
        latest.join()
        val openingState = fixture.state

        oldLookup.result.complete(emptyList<InstalledModelEntry>() to null)
        old.join()

        assertEquals(openingState, fixture.state)
    }

    @Test
    fun `stale lookup error cannot overwrite newer state or resolve its opening`() = runBlocking {
        val fixture = Fixture()
        val oldLookup = Lookup()
        val latestLookup = Lookup()
        val old = fixture.open(this, oldLookup)
        oldLookup.started.await()
        val latest = fixture.open(this, latestLookup)
        latestLookup.started.await()
        latestLookup.succeed(second)
        latest.join()
        fixture.state = fixture.state.copy(error = "Current model warning")
        val openingState = fixture.state

        oldLookup.result.completeExceptionally(IOException("Old lookup failed"))
        old.join()

        assertEquals(openingState, fixture.state)
    }

    @Test
    fun `current empty lookup resolves startup to the catalog`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val opening = fixture.open(this, lookup)
        lookup.started.await()
        lookup.result.complete(emptyList<InstalledModelEntry>() to null)
        opening.join()

        assertTrue(fixture.state.launchResolved)
        assertFalse(fixture.state.openingChat)
        assertTrue(fixture.selections.isEmpty())
    }

    @Test
    fun `current lookup error resolves startup and reports the failure`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val opening = fixture.open(this, lookup)
        lookup.started.await()
        lookup.result.completeExceptionally(IOException("Disk unavailable"))
        opening.join()

        assertTrue(fixture.state.launchResolved)
        assertFalse(fixture.state.openingChat)
        assertEquals("Disk unavailable", fixture.state.error)
        assertTrue(fixture.selections.isEmpty())
    }

    @Test
    fun `cancelled lookup propagates cancellation without error or completion callbacks`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val opening = fixture.open(this, lookup)
        lookup.started.await()
        val previousState = fixture.state
        opening.cancelAndJoin()

        assertTrue(opening.isCancelled)
        assertEquals(previousState, fixture.state)
        assertTrue(fixture.selections.isEmpty())
    }

    @Test
    fun `lookup cancellation is not reported as a storage error`() = runBlocking {
        val fixture = Fixture()
        val lookup = Lookup()
        val opening = fixture.open(this, lookup)
        lookup.started.await()
        lookup.result.completeExceptionally(CancellationException("Stopped"))
        opening.join()

        assertTrue(opening.isCancelled)
        assertFalse(fixture.state.launchResolved)
        assertEquals(null, fixture.state.error)
        assertTrue(fixture.selections.isEmpty())
    }

    @Test
    fun `session write cannot open old model path after selecting a different model`() = runBlocking {
        assertSessionWriteCannotOpenAfterSelections(selectionCount = 1)
    }

    @Test
    fun `session write cannot reopen old chat after switching away and back to the same model`() = runBlocking {
        assertSessionWriteCannotOpenAfterSelections(selectionCount = 2)
    }

    private suspend fun CoroutineScope.assertSessionWriteCannotOpenAfterSelections(selectionCount: Int) {
        val revision = AtomicLong(1L)
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val openedPaths = mutableListOf<String>()
        val opening = launch {
            openChatAfterSessionWrite(
                selectionRevision = revision,
                revision = 1L,
                writeSession = {
                    // Production history writes finish even when their original screen closes.
                    withContext(NonCancellable) {
                        writeStarted.complete(Unit)
                        finishWrite.await()
                        true
                    }
                },
                open = { openedPaths += first.localPath },
            )
        }
        writeStarted.await()
        repeat(selectionCount) { revision.incrementAndGet() }
        finishWrite.complete(Unit)
        opening.join()

        assertTrue(openedPaths.isEmpty())
    }

    @Test
    fun `current successful session write opens the captured model path once`() = runBlocking {
        val revision = AtomicLong(1L)
        val openedPaths = mutableListOf<String>()
        openChatAfterSessionWrite(
            selectionRevision = revision,
            revision = 1L,
            writeSession = { yield(); true },
            open = { openedPaths += first.localPath },
        )

        assertEquals(listOf(first.localPath), openedPaths)
    }

    @Test
    fun `failed session write does not open a chat`() = runBlocking {
        var opened = false
        openChatAfterSessionWrite(
            selectionRevision = AtomicLong(1L),
            revision = 1L,
            writeSession = { yield(); false },
            open = { opened = true },
        )

        assertFalse(opened)
    }

    @Test
    fun `already superseded selection does not start a session write`() = runBlocking {
        var wrote = false
        var opened = false
        openChatAfterSessionWrite(
            selectionRevision = AtomicLong(2L),
            revision = 1L,
            writeSession = { wrote = true; true },
            open = { opened = true },
        )

        assertFalse(wrote)
        assertFalse(opened)
    }

    private class Lookup {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Pair<List<InstalledModelEntry>, String?>>()

        fun succeed(entry: InstalledModelEntry) {
            result.complete(listOf(entry) to entry.model.id)
        }
    }

    private class Fixture {
        val revision = AtomicLong()
        var state = MobieUiState()
        val selections = mutableListOf<Selection>()

        fun open(scope: CoroutineScope, lookup: Lookup) = scope.openLatestChat(
            selectionRevision = revision,
            readSnapshot = {
                lookup.started.complete(Unit)
                lookup.result.await()
            },
            onInstalled = { state = state.copy(installedModels = it) },
            onOpening = { state = state.copy(openingChat = true) },
            onResolved = { state = state.copy(launchResolved = true, openingChat = false) },
            onFailure = { state = state.copy(error = it.message) },
        ) { entry, reservedRevision, complete ->
            selections += Selection(reservedRevision, complete)
            state = state.copy(selected = entry.model)
        }

        fun selectExplicitly(entry: InstalledModelEntry) {
            revision.incrementAndGet()
            state = state.copy(selected = entry.model, launchResolved = true, openingChat = false)
        }
    }

    private data class Selection(val revision: Long, val complete: () -> Unit)

    private fun installed(id: String) = InstalledModelEntry(AiModel(id, id, "litert-community", ""), "/models/$id")
}
