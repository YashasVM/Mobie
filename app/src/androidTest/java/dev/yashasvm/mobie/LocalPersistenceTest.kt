package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.data.history.ChatHistoryStore
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.ModelDownloadManager
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
    fun chatHistorySurvivesStoreRecreationAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/history-model"
        val messages = listOf(HistoryMessage(true, "Hello"), HistoryMessage(false, "Hi"))

        ChatHistoryStore(context).write(modelId, messages)
        assertEquals(messages, ChatHistoryStore(context).read(modelId))

        ChatHistoryStore(context).clear(modelId)
        assertEquals(emptyList<HistoryMessage>(), ChatHistoryStore(context).read(modelId))
    }

    @Test
    fun chatHistoryKeepsMoreThanOneLocalSession() {
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
        assertEquals("First chat", store.read(modelId).single().text)
        store.clear(modelId)
    }

    @Test
    fun installedModelPreservesSourceArtifactIdentity() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/artifact-identity"
        val sourceFileName = "Qwen3-0.6B-int4-ekv2048.litertlm"
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
        }.also { properties ->
            File(directory, DownloadFilePolicy.METADATA_FILE).outputStream().use { properties.store(it, null) }
        }

        val manager = ModelDownloadManager(context)
        val entry = manager.installedModels().single { it.model.id == modelId }
        val artifact = entry.model.artifacts.single()
        assertEquals(sourceFileName, artifact.fileName)
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
