package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeConversationReconfigurationPolicy
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadedEnginePolicyTest {
    private val identity = "litert-community/model|model.litertlm|https://example.invalid/model|abc|100"
    private val path = "/data/user/0/dev.yashasvm.mobie/files/models/model.litertlm"
    private val saved = ComputeOptions(ComputeBackend.CPU, cpuThreads = 4, contextTokens = 2_048)
    private val loadedOptions = LoadedEnginePolicy.requested(saved, identity, coreCount = 8)
    private val loaded = LoadedEngine("litert-community/model", path, vision = false, loadedOptions, SamplerSettings())
    private val running = ActiveCompute(ComputeBackend.CPU, cpuThreads = 4, contextTokens = 2_048)

    private fun canReconfigure(
        requested: ComputeOptions,
        engine: LoadedEngine? = loaded,
        modelId: String = loaded.modelId,
        enginePath: String = path,
        vision: Boolean = false,
        state: RuntimeState = RuntimeState.READY,
        active: ActiveCompute? = running,
    ) = LoadedEnginePolicy.canReconfigure(engine, modelId, enginePath, vision, requested, state, active)

    private fun request(options: ComputeOptions, coreCount: Int = 8) = LoadedEnginePolicy.requested(options, identity, coreCount)

    @Test fun `each conversation-only change keeps the loaded engine`() {
        listOf(
            saved.copy(maxOutputTokens = 256),
            saved.copy(thinkingEnabled = true),
            saved.copy(thinkingEnabled = true, thinkingTokenBudget = 64),
            saved.copy(speculativeDecoding = true),
            saved.copy(prefillPrefaceOnInit = true),
            saved,
        ).forEach { assertTrue("Should reconfigure $it", canReconfigure(request(it))) }
    }

    @Test fun `each engine-level change needs a full reload`() {
        listOf(
            saved.copy(backend = ComputeBackend.GPU),
            saved.copy(backend = ComputeBackend.AUTO),
            saved.copy(cpuThreads = 2),
            saved.copy(cpuThreads = null),
            saved.copy(contextTokens = 4_096),
            saved.copy(contextTokens = null),
            saved.copy(activationType = ActivationType.FP16),
        ).forEach { assertFalse("Unsafe engine reuse for $it", canReconfigure(request(it))) }
    }

    @Test fun `a different artifact file or vision mode needs a full reload`() {
        assertFalse(canReconfigure(LoadedEnginePolicy.requested(saved, "$identity-replaced", 8)))
        assertFalse(canReconfigure(request(saved), modelId = "litert-community/other"))
        assertFalse(canReconfigure(request(saved), enginePath = "$path.copy"))
        assertFalse(canReconfigure(request(saved), vision = true))
    }

    @Test fun `an unproven engine is never reconfigured`() {
        assertFalse("nothing recorded", canReconfigure(request(saved), engine = null))
        assertFalse("blank identity", canReconfigure(saved, engine = loaded.copy(options = saved)))
        // The runtime watchdog hides activeCompute until it has been unloaded.
        assertFalse("runtime needs recovery", canReconfigure(request(saved), active = null))
        RuntimeState.entries.filterNot { it == RuntimeState.READY }.forEach { state ->
            assertFalse("from $state", canReconfigure(request(saved), state = state))
        }
    }

    @Test fun `thread counts are normalized like the runtime so a core-count change cannot pass only here`() {
        val pinned = LoadedEnginePolicy.requested(saved.copy(cpuThreads = 8), identity, coreCount = 8)
        val engine = loaded.copy(options = pinned)
        assertTrue(canReconfigure(LoadedEnginePolicy.requested(saved.copy(cpuThreads = 8), identity, 8), engine = engine))
        // Fewer processors now clamp the same request differently, which the runtime would refuse.
        assertFalse(canReconfigure(LoadedEnginePolicy.requested(saved.copy(cpuThreads = 8), identity, 2), engine = engine))
    }

    @Test fun `admission never exceeds the runtime policy`() {
        val candidates = listOf(saved, saved.copy(maxOutputTokens = 64), saved.copy(backend = ComputeBackend.GPU),
            saved.copy(cpuThreads = 2), saved.copy(contextTokens = 1_024), saved.copy(activationType = ActivationType.FP32),
            saved.copy(speculativeDecoding = false), saved.copy(thinkingEnabled = false))
        candidates.forEach { candidate ->
            val requested = request(candidate)
            if (canReconfigure(requested)) {
                assertTrue(RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded.options, requested))
            }
        }
    }

    @Test fun `history-only reuse needs exactly the loaded settings and sampler`() {
        val sampler = SamplerSettings()
        assertTrue(LoadedEnginePolicy.canReuse(loaded, loaded.modelId, path, false, request(saved), sampler))
        assertFalse(LoadedEnginePolicy.canReuse(loaded, loaded.modelId, path, false, request(saved.copy(maxOutputTokens = 64)), sampler))
        assertFalse(LoadedEnginePolicy.canReuse(loaded, loaded.modelId, path, false, request(saved), sampler.copy(seed = 7)))
        assertFalse(LoadedEnginePolicy.canReuse(loaded, "litert-community/other", path, false, request(saved), sampler))
        assertFalse(LoadedEnginePolicy.canReuse(loaded, loaded.modelId, path, true, request(saved), sampler))
        assertFalse(LoadedEnginePolicy.canReuse(null, loaded.modelId, path, false, request(saved), sampler))
        assertFalse(LoadedEnginePolicy.canReuse(loaded.copy(options = saved), loaded.modelId, path, false, saved, sampler))
    }

    @Test fun `failed reconfigure fails closed and the next apply cannot reconfigure again`() {
        val before = MobieUiState(
            runtimeState = RuntimeState.LOADING,
            messages = listOf(ChatMessage(true, "hi"), ChatMessage(false, "hello")),
            sessionId = "session",
            activeCompute = running,
            imageInputReady = true,
            appliedComputeOptions = saved,
            appliedSampler = SamplerSettings(),
        )
        val failed = before.afterFailedReconfigure("Not enough free memory.", unloadFailure = null)
        assertEquals(RuntimeState.ERROR, failed.runtimeState)
        assertNull(failed.activeCompute)
        assertFalse(failed.imageInputReady)
        assertNull(failed.appliedComputeOptions)
        assertNull(failed.appliedSampler)
        assertEquals(before.messages, failed.messages)
        assertEquals("session", failed.sessionId)
        assertEquals("Couldn't apply these settings: Not enough free memory. " +
            "The model was unloaded. Go back and open this chat again to reload it.", failed.error)
        // Nothing is applied any more, so no reload is offered against a model that is gone.
        assertFalse(failed.computeNeedsApply)
        // The ViewModel clears its record before reconfiguring and only restores it on success.
        assertFalse(canReconfigure(request(saved.copy(maxOutputTokens = 64)), engine = null, state = failed.runtimeState,
            active = failed.activeCompute))
    }

    @Test fun `an unload failure after a failed reconfigure says to restart`() {
        val message = reconfigureFailureMessage(null, "Restart Mobie before loading or deleting this model.")
        assertEquals("Couldn't apply these settings. Restart Mobie before loading or deleting this model.", message)
    }

    @Test fun `successful reconfigure publishes exactly the applied settings`() {
        val changed = saved.copy(maxOutputTokens = 128)
        val sampler = SamplerSettings(temperature = 0.2)
        val state = MobieUiState(runtimeState = RuntimeState.LOADING, computeOptions = changed, sampler = sampler,
            appliedComputeOptions = saved, appliedSampler = SamplerSettings(), error = "old")
            .afterReconfigured(changed.copy(artifactIdentity = identity), sampler, running, imageInputReady = false)
        assertEquals(RuntimeState.READY, state.runtimeState)
        assertEquals(running, state.activeCompute)
        assertEquals(changed, state.appliedComputeOptions)
        assertEquals(sampler, state.appliedSampler)
        assertNull(state.error)
    }

    @Test fun `apply copy promises no reload only for conversation-only changes`() {
        assertTrue(AdvancedComputePolicy.changesNeedReload(saved, null))
        assertFalse(AdvancedComputePolicy.changesNeedReload(saved.copy(maxOutputTokens = 64, speculativeDecoding = true), saved))
        assertTrue(AdvancedComputePolicy.changesNeedReload(saved.copy(contextTokens = 4_096), saved))
        assertTrue(AdvancedComputePolicy.changesNeedReload(saved.copy(activationType = ActivationType.FP16), saved))
    }
}
