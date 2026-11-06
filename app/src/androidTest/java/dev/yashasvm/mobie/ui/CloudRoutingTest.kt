package dev.yashasvm.mobie.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.CloudModel
import dev.yashasvm.mobie.core.runtime.CloudChatProvider
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CloudRoutingTest {
    @Test fun selectedCloudRoutesMessagesAndCancellationThenLocalSelectionStopsRouting() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cloud = dev.yashasvm.mobie.core.model.CloudModel("fixture-cloud-routing", "Cloud fixture", true, "Fixture privacy")
        val started = CompletableDeferred<List<RuntimeMessage>>()
        val stopped = CompletableDeferred<Unit>()
        val fake = object : CloudChatProvider {
            override suspend fun models() = listOf(cloud)
            override fun generate(model: CloudModel, messages: List<RuntimeMessage>): Flow<InferenceEvent> = flow {
                assertEquals(cloud, model)
                started.complete(messages)
                try {
                    emit(InferenceEvent.Token("Cloud response"))
                    awaitCancellation()
                } finally { stopped.complete(Unit) }
            }
        }
        val store = ViewModelStore()
        val container = AppContainer(instrumentation.targetContext, fake)
        val previousConnection = container.cloudConnectionStore.read()
        lateinit var vm: MobieViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, MobieViewModel.factory(container))[MobieViewModel::class.java]
        }
        try {
            withTimeout(10_000) { vm.state.first { it.launchResolved && it.preferencesLoaded } }
            instrumentation.runOnMainSync { vm.saveCloudConnection("https://fixture.invalid", "fixture-key") }
            withTimeout(10_000) { vm.state.first { it.cloudModels.isNotEmpty() } }
            instrumentation.runOnMainSync { vm.selectCloud(cloud) }
            withTimeout(10_000) { vm.state.first { it.chatting && it.selected?.cloudModel == cloud } }
            instrumentation.runOnMainSync { vm.newChat() }
            instrumentation.runOnMainSync { assertTrue(vm.sendMessage("A synthetic test", "/private/synthetic.png")) }
            val sent = withTimeout(10_000) { started.await() }
            assertEquals(listOf(RuntimeMessage(true, "A synthetic test", imagePath = "/private/synthetic.png")), sent)
            withTimeout(10_000) { vm.state.first { it.messages.lastOrNull()?.text == "Cloud response" } }
            instrumentation.runOnMainSync { vm.stopGeneration() }
            withTimeout(10_000) { stopped.await(); vm.state.first { it.runtimeState == RuntimeState.READY } }
            assertEquals("Cloud response", vm.state.value.messages.last().text)
            assertTrue(vm.state.value.imageInputReady)
            instrumentation.runOnMainSync {
                vm.select(AiModel("fixture/local", "Local fixture", "fixture", "No runtime loaded"))
            }
            withTimeout(10_000) { vm.state.first { it.selected?.id == "fixture/local" } }
            assertNull(vm.state.value.selected?.cloudModel)
            assertFalse(vm.state.value.chatting)
            instrumentation.runOnMainSync { assertFalse(vm.sendMessage("No loaded local model")) }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            container.chatHistory.clear(cloud.asAiModel().id)
            if (previousConnection == null) container.cloudConnectionStore.clear()
            else container.cloudConnectionStore.save(previousConnection.baseUrl, previousConnection.token)
        }
    }
}
