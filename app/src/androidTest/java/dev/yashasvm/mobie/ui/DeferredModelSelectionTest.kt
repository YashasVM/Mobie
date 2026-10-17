package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.DownloadSourceIdentity
import dev.yashasvm.mobie.data.download.ModelFileVerification
import dev.yashasvm.mobie.data.history.HistoryMessage
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DeferredModelSelectionTest {
    @Test
    fun selectingAndStartingNewChatDoesNotLoadRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context)
        val store = ViewModelStore()
        val model = AiModel("test/deferred", "Deferred test", "test", "", artifacts = listOf(
            ModelArtifact("model.litertlm", "https://example.invalid/model.litertlm", 1024, format = ModelFormat.LITERT_LM),
        ))
        val entry = InstalledModelEntry(model, "/missing/model.litertlm")
        val vm = withContext(Dispatchers.Main) { ViewModelProvider(store, MobieViewModel.factory(container))[MobieViewModel::class.java] }
        try {
            withContext(Dispatchers.Main) { vm.startInstalledChat(entry) }
            withTimeout(10_000) { while (!vm.state.value.chatting) delay(50) }
            assertEquals(RuntimeState.IDLE, vm.state.value.runtimeState)
            assertNull(vm.state.value.error)
            withContext(Dispatchers.Main) { vm.newChat() }
            assertEquals(RuntimeState.IDLE, vm.state.value.runtimeState)
            assertTrue(vm.state.value.messages.isEmpty())
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            container.chatHistory.clear(model.id)
            container.lastUsedModel.write("")
        }
    }

    @Test
    fun recentsReopensSavedTranscriptsAcrossModelsWithoutLoadingRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context)
        val store = ViewModelStore()
        val modelIds = listOf("mobie-test/deferred-recents-first", "mobie-test/deferred-recents-second")
        val fixtureDirs = modelIds.map { File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(it)) }
        try {
            modelIds.zip(fixtureDirs).forEach { (id, directory) ->
                writeInstalledFixture(id, directory)
                container.chatHistory.clear(id)
            }
            val firstTranscript = listOf(HistoryMessage(true, "First question"), HistoryMessage(false, "First saved answer"))
            val secondTranscript = listOf(HistoryMessage(true, "Second question"), HistoryMessage(false, "Second saved answer"))
            val firstSession = container.chatHistory.startNewSession(modelIds[0])
            container.chatHistory.write(modelIds[0], firstTranscript)
            // Leave a different current session so opening Recents must actually activate the chosen one.
            container.chatHistory.startNewSession(modelIds[0])
            container.chatHistory.write(modelIds[0], listOf(HistoryMessage(true, "A different conversation")))
            val secondSession = container.chatHistory.startNewSession(modelIds[1])
            container.chatHistory.write(modelIds[1], secondTranscript)
            container.lastUsedModel.write(modelIds[1])
            val vm = withContext(Dispatchers.Main) { ViewModelProvider(store, MobieViewModel.factory(container))[MobieViewModel::class.java] }
            withTimeout(10_000) {
                while (vm.state.value.installedModels.none { it.model.id == modelIds[0] } || !vm.state.value.chatting) delay(50)
            }
            withContext(Dispatchers.Main) { vm.refreshRecents() }
            val recents = vm.state.value.recentChats.filter { it.modelId in modelIds }
            assertEquals(3, recents.size)
            for ((modelId, sessionId, transcript) in listOf(
                Triple(modelIds[0], firstSession, firstTranscript),
                Triple(modelIds[1], secondSession, secondTranscript),
            )) {
                val recent = recents.single { it.modelId == modelId && it.session.id == sessionId }
                withContext(Dispatchers.Main) { vm.openRecent(recent) }
                withTimeout(10_000) {
                    while (vm.state.value.selected?.id != modelId || vm.state.value.sessionId != sessionId || !vm.state.value.chatting) delay(50)
                }
                val opened = vm.state.value
                assertEquals(transcript.map { it.text }, opened.messages.map { it.text })
                assertEquals(transcript.map { it.fromUser }, opened.messages.map { it.fromUser })
                assertEquals(RuntimeState.IDLE, opened.runtimeState)
                assertNull(opened.error)
                assertEquals(sessionId, container.chatHistory.currentSessionId(modelId))
            }
            assertEquals(firstTranscript, container.chatHistory.sessions(modelIds[0]).single { it.id == firstSession }.messages)
            assertEquals(secondTranscript, container.chatHistory.sessions(modelIds[1]).single { it.id == secondSession }.messages)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            modelIds.forEach(container.chatHistory::clear)
            fixtureDirs.forEach { it.deleteRecursively() }
            container.lastUsedModel.write("")
        }
    }

    @Test
    fun returningFromUninstalledModelClearsSelectionWithoutActiveInstalledModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context)
        val store = ViewModelStore()
        val model = AiModel("mobie-test/not-installed", "Not installed", "test", "", artifacts = listOf(
            ModelArtifact("not-installed.litertlm", "https://example.invalid/not-installed.litertlm", 1024, format = ModelFormat.LITERT_LM),
        ))
        container.lastUsedModel.write("")
        try {
            val vm = withContext(Dispatchers.Main) {
                ViewModelProvider(store, MobieViewModel.factory(container))[MobieViewModel::class.java].also { it.select(model) }
            }
            withTimeout(10_000) { while (vm.state.value.selected?.id != model.id) delay(50) }
            assertFalse(vm.state.value.chatting)
            withContext(Dispatchers.Main) { vm.returnToChat() }
            withTimeout(10_000) { while (vm.state.value.selected != null) delay(50) }
            assertFalse(vm.state.value.chatting)
            assertTrue(vm.state.value.messages.isEmpty())
            assertNull(vm.state.value.downloadedPath)
            assertEquals(RuntimeState.IDLE, vm.state.value.runtimeState)
            assertNull(vm.state.value.error)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            container.chatHistory.clear(model.id)
            container.lastUsedModel.write("")
        }
    }

}

// These bytes validate installed-file metadata only; no test loads them into a model engine.
internal fun writeInstalledFixture(modelId: String, directory: File) {
    directory.deleteRecursively()
    check(directory.mkdirs())
    val sourceFileName = "model.litertlm"
    val modelFile = File(directory, DownloadFilePolicy.storageFileName(sourceFileName))
    modelFile.writeBytes(ByteArray(64 * 1024) { index -> (index * 29).toByte() })
    Properties().apply {
        setProperty("modelId", modelId)
        setProperty("title", modelId.substringAfterLast('/'))
        setProperty("author", "Mobie test")
        setProperty("description", "Deferred model selection fixture")
        setProperty("type", "TEXT_GENERATION")
        setProperty("gated", "false")
        setProperty("fileName", modelFile.name)
        setProperty("sourceFileName", sourceFileName)
        setProperty("quantization", "int4")
        DownloadSourceIdentity.stamp(this, "https://huggingface.co/$modelId/resolve/0123456789abcdef0123456789abcdef01234567/$sourceFileName")
        ModelFileVerification.stampInstalledLength(this, modelFile)
        DownloadFilePolicy.artifactMetadataFile(directory, sourceFileName).outputStream().use { store(it, null) }
    }
}
