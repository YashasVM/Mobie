package dev.yashasvm.mobie

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.*
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.MobieViewModel
import dev.yashasvm.mobie.ui.RuntimeState
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class RuntimeReloadOrchestrationTest {
    @Test
    fun refusedApplyKeepsTheOwnedEngineAndConversation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val delegate = RefusingReloadRuntime()
        val container = AppContainer(context, RuntimeRegistry(setOf(delegate)))
        val store = ViewModelStore()
        lateinit var model: MobieViewModel
        instrumentation.runOnMainSync { model = ViewModelProvider(store, MobieViewModel.factory(container))[MobieViewModel::class.java] }
        val file = File(context.cacheDir, "reload-orchestration-fixture.litertlm").apply { writeText("fixture") }
        try {
            withTimeout(15_000) { model.state.first { it.preferencesLoaded } }
            val artifact = ModelArtifact("fixture.litertlm", "https://example.invalid/fixture.litertlm", 1024, format = ModelFormat.LITERT_LM)
            val entry = InstalledModelEntry(AiModel("integration-fixture/runtime", "Runtime fixture", "fixture", "Local fake adapter", artifacts = listOf(artifact)), file.absolutePath)
            instrumentation.runOnMainSync { model.startInstalledChat(entry) }
            val loaded = withTimeout(15_000) { model.state.first { it.runtimeState in setOf(RuntimeState.READY, RuntimeState.ERROR) && it.selected?.id == entry.model.id } }
            assertEquals(loaded.error, RuntimeState.READY, loaded.runtimeState)
            instrumentation.runOnMainSync {
                model.updateComputeOptions(model.state.value.computeOptions.copy(backend = ComputeBackend.CPU))
                model.applyComputeSettings()
            }
            val refused = withTimeout(15_000) { model.state.first { it.runtimeState != RuntimeState.LOADING && it.error != null } }
            assertEquals(RuntimeState.READY, refused.runtimeState)
            assertEquals(2, delegate.loads)
            assertEquals(0, delegate.unloads)
            assertEquals(ComputeBackend.CPU, refused.activeCompute?.backend)
            assertTrue(refused.error.orEmpty().contains("critical thermal"))
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            file.delete()
        }
    }

    private class RefusingReloadRuntime : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        var loads = 0
        var unloads = 0
        private var loaded = false
        override val activeCompute get() = if (loaded) ActiveCompute(ComputeBackend.CPU, cpuThreads = 1, contextTokens = 1024) else null
        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>): Result<Unit> {
            loads++
            if (loaded) return Result.failure(IllegalStateException("critical thermal pressure refused the replacement"))
            loaded = true
            return Result.success(Unit)
        }
        override suspend fun resetConversation(history: List<RuntimeMessage>) = Result.success(Unit)
        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig) = flowOf(InferenceEvent.Complete)
        override suspend fun cancel() = Unit
        override suspend fun unload() { unloads++; loaded = false }
    }
}
