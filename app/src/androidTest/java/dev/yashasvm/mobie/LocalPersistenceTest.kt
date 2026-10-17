package dev.yashasvm.mobie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.data.history.ChatHistoryStore
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.DownloadSourceIdentity
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    @Test
    fun chatHistorySurvivesStoreRecreationAndCanBeCleared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/history-model"
        val messages = listOf(HistoryMessage(true, "Hello"), HistoryMessage(false, "Hi"))

        ChatHistoryStore(context).write(modelId, messages)
        assertEquals(messages, ChatHistoryStore(context).read(modelId))

        ChatHistoryStore(context).clear(modelId)
        assertEquals(emptyList<HistoryMessage>(), ChatHistoryStore(context).read(modelId))
    }

    @Test
    fun chatHistoryKeepsMoreThanOneLocalSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/session-model"
        val store = ChatHistoryStore(context)

        store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "First chat")))
        val first = store.sessions(modelId).first().id
        store.startNewSession(modelId)
        store.write(modelId, listOf(HistoryMessage(true, "Second chat")))

        assertEquals(2, store.sessions(modelId).size)
        store.activate(modelId, first)
        assertEquals(first, store.currentSessionId(modelId))
        assertEquals("First chat", store.read(modelId).single().text)
        store.clear(modelId)
    }

    @Test
    fun repeatedHistoryWritesPreserveInactiveSessionsAndSurviveRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/cached-session-writes"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        try {
            val firstId = store.startNewSession(modelId)
            store.write(modelId, listOf(HistoryMessage(true, "First chat")))
            val secondId = store.startNewSession(modelId)
            store.write(modelId, listOf(HistoryMessage(true, "Second chat")))

            store.activate(modelId, firstId)
            val updatedFirst = listOf(HistoryMessage(true, "First chat"), HistoryMessage(false, "Updated answer"))
            store.write(modelId, updatedFirst)
            store.write(modelId, updatedFirst + HistoryMessage(true, "Another question"))

            val recreated = ChatHistoryStore(context)
            assertEquals(2, recreated.sessions(modelId).size)
            assertEquals(firstId, recreated.currentSessionId(modelId))
            assertEquals(updatedFirst + HistoryMessage(true, "Another question"), recreated.read(modelId))
            recreated.activate(modelId, secondId)
            assertEquals(listOf(HistoryMessage(true, "Second chat")), recreated.read(modelId))
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun writingRestoredLegacyChatUpdatesItInsteadOfCreatingDuplicateSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/legacy-chat-write"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
            .edit()
            .putString(
                modelId,
                """[{"fromUser":true,"text":"Earlier question"},{"fromUser":false,"text":"Earlier answer"}]""",
            )
            .commit()

        try {
            val restored = ChatHistoryStore(context)
            val original = listOf(
                HistoryMessage(true, "Earlier question"),
                HistoryMessage(false, "Earlier answer"),
            )
            assertEquals(original, restored.read(modelId))
            assertEquals("legacy", restored.currentSessionId(modelId))

            val updated = original + HistoryMessage(true, "Follow-up")
            restored.write(modelId, updated)

            assertEquals(1, restored.sessions(modelId).size)
            assertEquals("legacy", restored.currentSessionId(modelId))
            assertEquals(updated, ChatHistoryStore(context).read(modelId))
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun staleActiveSessionPointerFallsBackToExistingSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/stale-chat-pointer"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        try {
            val actualId = store.startNewSession(modelId)
            val original = listOf(HistoryMessage(true, "Existing chat"))
            store.write(modelId, original)
            context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
                .edit().putString("current:$modelId", "missing-session").commit()

            val restored = ChatHistoryStore(context)
            assertEquals(actualId, restored.currentSessionId(modelId))
            assertEquals(original, restored.read(modelId))

            val updated = original + HistoryMessage(false, "Existing answer")
            restored.write(modelId, updated)
            assertEquals(1, restored.sessions(modelId).size)
            assertEquals(actualId, restored.currentSessionId(modelId))
            assertEquals(updated, ChatHistoryStore(context).read(modelId))
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun allSessionsListsChatsAcrossModelsNewestFirstAndSkipsEmptyOnes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = "test/all-sessions-a"
        val second = "test/all-sessions-b"
        val store = ChatHistoryStore(context)
        listOf(first, second).forEach(store::clear)
        try {
            val older = store.writeSession(first, null, listOf(HistoryMessage(true, "Older chat")), modelTitle = "Model A")
            Thread.sleep(5)
            val newer = store.writeSession(second, null, listOf(HistoryMessage(true, "Newer chat")), modelTitle = "Model B")
            // A new chat stays unsaved until it has a message.
            store.writeSession(first, null, emptyList())

            val threads = ChatHistoryStore(context).allSessions().filter { it.modelId in setOf(first, second) }
            assertEquals(listOf(newer, older), threads.map { it.session.id })
            assertEquals(listOf(second, first), threads.map { it.modelId })
            assertEquals("Model B", threads.first().session.modelTitle)
            assertEquals(1, store.sessions(first).size)
        } finally {
            listOf(first, second).forEach(store::clear)
        }
    }

    @Test
    fun renamedChatKeepsItsTitleAfterNewMessages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/rename-chat"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        try {
            val id = store.writeSession(modelId, null, listOf(HistoryMessage(true, "Original prompt")))
            assertEquals("Original prompt", store.session(modelId, id)?.title)
            assertTrue(store.rename(modelId, id, "  Trip ideas \n"))
            assertFalse(store.rename(modelId, id, "   "))
            store.writeSession(modelId, id, listOf(HistoryMessage(true, "Original prompt"), HistoryMessage(false, "Answer")))

            val restored = ChatHistoryStore(context).session(modelId, id)
            assertEquals("Trip ideas", restored?.title)
            assertEquals(2, restored?.messages?.size)
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun deletingChatsRemovesThemAndTheirActivePointer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/delete-chat"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        try {
            val kept = store.writeSession(modelId, null, listOf(HistoryMessage(true, "Keep me")))
            val removed = store.writeSession(modelId, null, listOf(HistoryMessage(true, "Delete me")))
            assertEquals(removed, store.currentSessionId(modelId))

            assertTrue(store.delete(modelId, removed))
            assertFalse(store.delete(modelId, removed))
            val restored = ChatHistoryStore(context)
            assertEquals(listOf(kept), restored.sessions(modelId).map { it.id })
            assertEquals(kept, restored.currentSessionId(modelId))
            assertEquals(null, context.getSharedPreferences("chat_history", Context.MODE_PRIVATE).getString("current:$modelId", null))
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun legacyChatIsListedAndCanBeDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/legacy-delete"
        val store = ChatHistoryStore(context)
        store.clear(modelId)
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
            .edit()
            .putString(modelId, """[{"fromUser":true,"text":"Earlier question"}]""")
            .commit()
        try {
            val restored = ChatHistoryStore(context)
            val legacy = restored.allSessions().single { it.modelId == modelId }
            assertEquals(ChatHistoryStore.LEGACY_SESSION_ID, legacy.session.id)

            assertTrue(restored.delete(modelId, legacy.session.id))
            val recreated = ChatHistoryStore(context)
            assertTrue(recreated.allSessions().none { it.modelId == modelId })
            assertTrue(recreated.sessions(modelId).isEmpty())
        } finally {
            store.clear(modelId)
        }
    }

    @Test
    fun lastUsedModelSurvivesStoreRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ChatHistoryStore(context)
        val previous = store.lastModelId()
        try {
            store.setLastModelId("test/last-used")
            assertEquals("test/last-used", ChatHistoryStore(context).lastModelId())
            // The pointer is not a chat, so it never shows up as one.
            assertTrue(ChatHistoryStore(context).allSessions().none { it.modelId == "test/last-used" })
        } finally {
            val preferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE).edit()
            if (previous == null) preferences.remove("meta:last_model") else preferences.putString("meta:last_model", previous)
            preferences.commit()
        }
    }

    @Test
    fun installedModelPreservesSourceArtifactIdentity() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/artifact-identity"
        val sourceFileName = "Qwen3-0.6B-int4-ekv2048.litertlm"
        val sourceUrl = "https://huggingface.co/example/model/resolve/0123456789abcdef0123456789abcdef01234567/$sourceFileName"
        val directory = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        directory.deleteRecursively()
        directory.mkdirs()
        val artifactFile = File(directory, DownloadFilePolicy.storageFileName(sourceFileName)).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        Properties().apply {
            setProperty("modelId", modelId)
            setProperty("title", "Artifact identity")
            setProperty("author", "test")
            setProperty("type", "TEXT_GENERATION")
            setProperty("fileName", artifactFile.name)
            setProperty("sourceFileName", sourceFileName)
            setProperty("quantization", "INT4")
            setProperty("installedLength", artifactFile.length().toString())
            DownloadSourceIdentity.stamp(this, sourceUrl)
        }.also { properties ->
            File(directory, DownloadFilePolicy.METADATA_FILE).outputStream().use { properties.store(it, null) }
        }

        val manager = ModelDownloadManager(context)
        val entry = manager.installedModels().single { it.model.id == modelId }
        val artifact = entry.model.artifacts.single()
        assertEquals(sourceFileName, artifact.fileName)
        assertEquals(sourceUrl, artifact.downloadUrl)
        assertEquals(2_048, artifact.contextWindowTokens)
        assertEquals("INT4", artifact.quantization)
        assertEquals(artifactFile.absolutePath, manager.completedFile(modelId, artifact)?.absolutePath)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun installedModelCanBeDeleted() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/deletable-model"
        val directory = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        directory.deleteRecursively()
        directory.mkdirs()
        val artifact = File(directory, "model.litertlm").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val liteRtCache = File(directory, ".litert-cache").apply {
            mkdirs()
            File(this, "compiled-cache.bin").writeBytes(byteArrayOf(4, 5, 6))
        }
        Properties().apply {
            setProperty("modelId", modelId)
            setProperty("title", "Deletable model")
            setProperty("author", "test")
            setProperty("type", "TEXT_GENERATION")
            setProperty("fileName", artifact.name)
        }.also { properties ->
            File(directory, DownloadFilePolicy.METADATA_FILE).outputStream().use { properties.store(it, null) }
        }

        val manager = ModelDownloadManager(context)
        val entry = manager.installedModels().single { it.model.id == modelId }
        assertTrue(liteRtCache.isDirectory)
        assertTrue(manager.deleteInstalled(entry.model))
        assertFalse(directory.exists())
        assertFalse(liteRtCache.exists())
    }
}
