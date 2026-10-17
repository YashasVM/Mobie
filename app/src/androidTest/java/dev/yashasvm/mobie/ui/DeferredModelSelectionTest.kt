package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DeferredModelSelectionTest {
    @Test
    fun selectionAndRecentsDoNotLoadRuntimeOrLoseHistory() = runBlocking {
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
}
