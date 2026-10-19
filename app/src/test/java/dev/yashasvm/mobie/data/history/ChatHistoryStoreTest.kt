package dev.yashasvm.mobie.data.history

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryStoreTest {
    private val modelId = "litert-community/test-model"

    @Test
    fun writeIsReadableFromANewStoreOverTheSamePreferences() = runBlocking {
        val prefs = MemoryPreferences()
        val messages = listOf(HistoryMessage(true, "Hello"), HistoryMessage(false, "Hi", thinking = "think"))

        ChatHistoryStore(prefs).write(modelId, messages)

        assertEquals(messages, ChatHistoryStore(prefs.reopened()).read(modelId))
        assertEquals("Hello", ChatHistoryStore(prefs).sessions(modelId).single().title)
    }

    @Test
    fun lastUsedModelSurvivesReopeningAndDoesNotDisturbHistory() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        assertNull(store.lastModelId())
        store.write(modelId, listOf(HistoryMessage(true, "Hello")))

        store.setLastModelId(modelId)
        val writes = prefs.commitCount
        store.setLastModelId(modelId)

        assertEquals(writes, prefs.commitCount)
        assertEquals(modelId, ChatHistoryStore(prefs.reopened()).lastModelId())
        assertEquals("Hello", ChatHistoryStore(prefs.reopened()).read(modelId).single().text)
    }

    @Test
    fun repeatedIdenticalWriteIsSkippedAndLeavesStoredPayloadUntouched() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val messages = listOf(HistoryMessage(true, "Question"), HistoryMessage(false, "Answer"))
        store.write(modelId, messages)
        val writes = prefs.commitCount
        val payload = prefs.values["sessions:$modelId"]
        val updatedAt = store.sessions(modelId).single().updatedAt

        store.write(modelId, messages)

        assertEquals(writes, prefs.commitCount)
        assertTrue(prefs.values["sessions:$modelId"] === payload)
        assertEquals(updatedAt, store.sessions(modelId).single().updatedAt)
    }

    @Test
    fun changedWriteReplacesActiveSessionWithoutDroppingInactiveOnes() = runBlocking {
        val store = ChatHistoryStore(MemoryPreferences())
        val firstId = store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "First chat")))
        val secondId = store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "Second chat")))
        store.write(modelId, listOf(HistoryMessage(true, "Second chat"), HistoryMessage(false, "Reply")))

        assertEquals(setOf(firstId, secondId), store.sessions(modelId).map { it.id }.toSet())
        assertEquals(2, store.read(modelId).size)

        store.activate(modelId, firstId)
        assertEquals(firstId, store.currentSessionId(modelId))
        assertEquals("First chat", store.read(modelId).single().text)
    }

    @Test
    fun activateIgnoresUnknownSessionIds() = runBlocking {
        val store = ChatHistoryStore(MemoryPreferences())
        val id = store.startNewSession(modelId)

        store.activate(modelId, "missing")

        assertEquals(id, store.currentSessionId(modelId))
    }

    @Test
    fun legacySingleTranscriptIsReadAsOneSession() = runBlocking {
        val prefs = MemoryPreferences()
        prefs.values[modelId] =
            """[{"fromUser":true,"text":"old question"},{"fromUser":false,"text":"old answer"}]"""
        val store = ChatHistoryStore(prefs)

        assertEquals(listOf("old question", "old answer"), store.read(modelId).map { it.text })
        assertEquals("Previous local chat", store.sessions(modelId).single().title)
    }

    @Test
    fun clearRemovesEverythingForTheModel() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        store.write(modelId, listOf(HistoryMessage(true, "Hello")))

        store.clear(modelId)

        assertEquals(emptyList<HistoryMessage>(), store.read(modelId))
        assertNull(prefs.values["current:$modelId"])
    }

    @Test
    fun concurrentReadsAndWritesKeepOneConsistentSession() = runBlocking {
        val store = ChatHistoryStore(MemoryPreferences())
        val jobs = (0 until 40).map { index ->
            async(Dispatchers.Default) {
                if (index % 2 == 0) {
                    store.write(modelId, List(index % 5 + 1) { HistoryMessage(it % 2 == 0, "m$it") })
                } else {
                    store.read(modelId)
                    store.sessions(modelId)
                }
            }
        }
        jobs.awaitAll()

        assertEquals(1, store.sessions(modelId).size)
    }

    @Test
    fun stalePointerFallsBackForReadsAndWritesWithoutDuplicatingTheSession() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val id = store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "Original")))
        prefs.values["current:$modelId"] = "missing"
        val restored = ChatHistoryStore(prefs)

        assertEquals(id, restored.currentSessionId(modelId))
        assertEquals("Original", restored.read(modelId).single().text)
        restored.write(modelId, listOf(HistoryMessage(true, "Updated")))

        assertEquals(id, restored.sessions(modelId).single().id)
        assertEquals("Updated", ChatHistoryStore(prefs).read(modelId).single().text)
    }

    @Test
    fun writingRestoredLegacyTranscriptReusesItsSessionWithMissingOrStalePointer() = runBlocking {
        for (pointer in listOf(null, "missing")) {
            val prefs = MemoryPreferences()
            prefs.values[modelId] = """[{"fromUser":true,"text":"Legacy question"}]"""
            if (pointer != null) prefs.values["current:$modelId"] = pointer
            val store = ChatHistoryStore(prefs)
            val messages = store.read(modelId) + HistoryMessage(false, "Reply")

            store.write(modelId, messages)

            assertEquals("legacy", store.sessions(modelId).single().id)
            assertEquals(messages, ChatHistoryStore(prefs).read(modelId))
        }
    }

    @Test
    fun missingPointerReusesMostRecentSession() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        store.startNewSession(modelId, "older")
        store.write(modelId, listOf(HistoryMessage(true, "Older")))
        store.startNewSession(modelId, "newer")
        store.write(modelId, listOf(HistoryMessage(true, "Newer")))
        prefs.values.remove("current:$modelId")
        val restored = ChatHistoryStore(prefs)

        assertEquals("newer", restored.currentSessionId(modelId))
        restored.write(modelId, restored.read(modelId) + HistoryMessage(false, "Reply"))

        assertEquals(2, restored.sessions(modelId).size)
        assertEquals("newer", ChatHistoryStore(prefs).currentSessionId(modelId))
    }

    @Test(timeout = 10_000)
    fun queuedWritesSessionChangesAndReadsTakeEffectInLockArrivalOrder() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        store.startNewSession(modelId, "first")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        prefs.beforeRead = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            store.write(modelId, listOf(HistoryMessage(true, "Updated first")))
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = launch(start = CoroutineStart.UNDISPATCHED) { store.startNewSession(modelId, "second") }
            val third = launch(start = CoroutineStart.UNDISPATCHED) {
                store.write(modelId, listOf(HistoryMessage(true, "Second")))
            }
            val fourth = launch(start = CoroutineStart.UNDISPATCHED) { store.activate(modelId, "first") }
            val read = async(start = CoroutineStart.UNDISPATCHED) { store.read(modelId) }
            release.countDown()
            listOf(first, second, third, fourth).forEach { it.join() }

            assertEquals("Updated first", read.await().single().text)
            store.activate(modelId, "second")
            assertEquals("Second", store.read(modelId).single().text)
        } finally {
            release.countDown()
        }
    }

    @Test(timeout = 10_000)
    fun queuedSaveCompletesAfterCallerCancellationAndCancelledReadReleasesTheQueue() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        prefs.beforeRead = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val first = launch(start = CoroutineStart.UNDISPATCHED) { store.read(modelId) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cancelledRead = launch(start = CoroutineStart.UNDISPATCHED) { store.read(modelId) }
            val save = launch(start = CoroutineStart.UNDISPATCHED) {
                store.write(modelId, listOf(HistoryMessage(true, "Saved despite cancellation")))
            }
            cancelledRead.cancel()
            save.cancel()
            release.countDown()
            first.join()
            cancelledRead.join()
            save.join()

            assertEquals("Saved despite cancellation", ChatHistoryStore(prefs.reopened()).read(modelId).single().text)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun allPreferenceAccessRunsOffTheCallingThread() = runBlocking {
        val callingThread = Thread.currentThread()
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        store.startNewSession(modelId, "session")
        store.write(modelId, listOf(HistoryMessage(true, "Hello")))
        store.read(modelId)
        store.sessions(modelId)
        store.currentSessionId(modelId)
        store.activate(modelId, "session")
        store.clear(modelId)

        assertTrue(prefs.accessThreads.isNotEmpty())
        assertTrue(prefs.accessThreads.none { it === callingThread })
    }

    @Test
    fun malformedSessionDataFallsBackToLegacyWithoutExposingThePayload() = runBlocking {
        val prefs = MemoryPreferences()
        prefs.values["sessions:$modelId"] = "invalid saved sessions"
        prefs.values[modelId] = """[{"fromUser":true,"text":"Legacy"}]"""

        assertEquals("Legacy", ChatHistoryStore(prefs).read(modelId).single().text)
    }

    @Test(timeout = 10_000)
    fun saveDoesNotReturnBeforeTheDiskCommitCompletes() = runBlocking {
        val prefs = MemoryPreferences()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        prefs.beforeCommit = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val save = async(start = CoroutineStart.UNDISPATCHED) {
            ChatHistoryStore(prefs).write(modelId, listOf(HistoryMessage(true, "Durable")))
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(save.isCompleted)
            release.countDown()
            save.await()
            assertEquals("Durable", ChatHistoryStore(prefs.reopened()).read(modelId).single().text)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun failedCommitIsReportedAndTheSameTranscriptCanBeRetried() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val original = listOf(HistoryMessage(true, "Saved"))
        val updated = original + HistoryMessage(false, "New reply")
        store.write(modelId, original)
        prefs.commitSucceeds = false
        var failed = false
        try {
            store.write(modelId, updated)
        } catch (_: IOException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(original, ChatHistoryStore(prefs.reopened()).read(modelId))
        prefs.commitSucceeds = true
        store.write(modelId, updated)
        assertEquals(updated, ChatHistoryStore(prefs.reopened()).read(modelId))
    }

    @Test
    fun identicalWriteRepairsStaleActivePointer() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val messages = listOf(HistoryMessage(true, "Question"))
        store.write(modelId, messages)
        val id = store.currentSessionId(modelId)
        prefs.values["current:$modelId"] = "missing"

        store.write(modelId, messages)

        assertEquals(id, prefs.reopened().values["current:$modelId"])
        assertEquals(1, store.sessions(modelId).size)
    }

    @Test
    fun capturedSaveUpdatesItsOriginalSessionAfterAnotherChatBecomesActive() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val first = store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "First question")))
        val captured = listOf(HistoryMessage(true, "First question"), HistoryMessage(false, "Delayed first answer"))
        val second = store.startNewSession(modelId)
        val secondMessages = listOf(HistoryMessage(true, "Second question"))
        store.write(modelId, secondMessages)

        assertEquals(first, store.writeSession(modelId, first, captured))

        val restored = ChatHistoryStore(prefs.reopened())
        assertEquals(second, restored.currentSessionId(modelId))
        assertEquals(secondMessages, restored.read(modelId))
        restored.activate(modelId, first)
        assertEquals(captured, restored.read(modelId))
    }

    @Test
    fun firstSaveOfCapturedUnsavedChatDoesNotOverwriteAnotherActiveChat() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        val active = store.startNewSession(modelId)
        val activeMessages = listOf(HistoryMessage(true, "Active question"))
        store.write(modelId, activeMessages)
        val captured = listOf(HistoryMessage(true, "Earlier unsaved chat"))

        store.writeSession(modelId, "captured", captured)

        val restored = ChatHistoryStore(prefs.reopened())
        assertEquals(active, restored.currentSessionId(modelId))
        assertEquals(activeMessages, restored.read(modelId))
        restored.activate(modelId, "captured")
        assertEquals(captured, restored.read(modelId))
    }

    @Test
    fun retentionKeepsRecentSessionsAndMessagesAcrossRecreation() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        repeat(21) { index ->
            store.startNewSession(modelId, "session-$index")
            store.write(modelId, listOf(HistoryMessage(true, "Saved conversation $index")))
        }
        val messages = List(105) { HistoryMessage(it % 2 == 0, "Message $it") }
        store.write(modelId, messages)

        val restored = ChatHistoryStore(prefs.reopened())
        assertEquals((1..20).map { "session-$it" }.toSet(), restored.sessions(modelId).map { it.id }.toSet())
        assertEquals(messages.takeLast(100), restored.read(modelId))
    }

    @Test
    fun repeatedEmptyDraftsKeepSavedConversationsAcrossRecreation() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        repeat(20) { index ->
            store.startNewSession(modelId, "saved-$index")
            store.write(modelId, listOf(HistoryMessage(true, "Saved conversation $index")))
        }
        repeat(30) { store.startNewSession(modelId, "draft-$it") }
        val writes = prefs.commitCount

        store.write(modelId, emptyList())

        assertEquals(writes, prefs.commitCount)
        val restored = ChatHistoryStore(prefs.reopened())
        val sessions = restored.sessions(modelId)
        assertEquals(21, sessions.size)
        assertEquals("draft-29", sessions.single { it.messages.isEmpty() }.id)
        assertEquals(
            (0..19).map { "saved-$it" }.toSet(),
            sessions.filter { it.messages.isNotEmpty() }.map { it.id }.toSet(),
        )
        assertEquals("draft-29", restored.currentSessionId(modelId))
        assertTrue(restored.read(modelId).isEmpty())
        restored.activate(modelId, "saved-0")
        assertEquals("Saved conversation 0", restored.read(modelId).single().text)
    }

    @Test
    fun firstMessageInDraftEvictsOnlyTheOldestSavedConversation() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        repeat(20) { index ->
            store.startNewSession(modelId, "saved-$index")
            store.write(modelId, listOf(HistoryMessage(true, "Saved conversation $index")))
        }
        val draftId = store.startNewSession(modelId, "draft")
        val messages = listOf(HistoryMessage(true, "First real message"))

        store.write(modelId, messages)

        val restored = ChatHistoryStore(prefs.reopened())
        assertEquals(20, restored.sessions(modelId).size)
        assertTrue(restored.sessions(modelId).all { it.messages.isNotEmpty() })
        assertEquals(
            (1..19).map { "saved-$it" }.toSet() + draftId,
            restored.sessions(modelId).map { it.id }.toSet(),
        )
        assertEquals(draftId, restored.currentSessionId(modelId))
        assertEquals(messages, restored.read(modelId))
    }

    @Test
    fun capturedSaveOfReplacedDraftPreservesTheNewestActiveDraft() = runBlocking {
        val prefs = MemoryPreferences()
        val store = ChatHistoryStore(prefs)
        repeat(20) { index ->
            store.startNewSession(modelId, "saved-$index")
            store.write(modelId, listOf(HistoryMessage(true, "Saved conversation $index")))
        }
        val capturedId = store.startNewSession(modelId, "captured-draft")
        val activeId = store.startNewSession(modelId, "active-draft")
        assertFalse(store.sessions(modelId).any { it.id == capturedId })
        val messages = listOf(HistoryMessage(true, "Captured question"), HistoryMessage(false, "Delayed answer"))

        assertEquals(capturedId, store.writeSession(modelId, capturedId, messages))

        val restored = ChatHistoryStore(prefs.reopened())
        assertEquals(21, restored.sessions(modelId).size)
        assertEquals(activeId, restored.sessions(modelId).single { it.messages.isEmpty() }.id)
        assertEquals(activeId, restored.currentSessionId(modelId))
        assertTrue(restored.read(modelId).isEmpty())
        assertEquals(
            (1..19).map { "saved-$it" }.toSet() + capturedId,
            restored.sessions(modelId).filter { it.messages.isNotEmpty() }.map { it.id }.toSet(),
        )
        restored.activate(modelId, capturedId)
        assertEquals(messages, restored.read(modelId))
    }

    /** Reopening discards memory and restores only the last successful disk commit. */
    private class MemoryPreferences : SharedPreferences {
        val values = HashMap<String, Any?>()
        private val diskValues = HashMap<String, Any?>()
        val accessThreads: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
        var commitCount = 0
        var commitSucceeds = true
        var beforeRead: (() -> Unit)? = null
        var beforeCommit: (() -> Unit)? = null

        fun reopened() = MemoryPreferences().also { it.values.putAll(diskValues) }

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? {
            accessThreads.add(Thread.currentThread())
            val action = beforeRead
            beforeRead = null
            action?.invoke()
            return values[key] as? String ?: defValue
        }
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor {
            accessThreads.add(Thread.currentThread())
            return Editor()
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private var clearRequested = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = apply { pending[key!!] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
            override fun commit(): Boolean {
                apply()
                beforeCommit?.invoke()
                commitCount++
                if (commitSucceeds) {
                    diskValues.clear()
                    diskValues.putAll(values)
                }
                return commitSucceeds
            }
            override fun apply() {
                accessThreads.add(Thread.currentThread())
                if (clearRequested) values.clear()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
        }
    }
}
