package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConversationReconfigurationGuardTest {
    private val options = ComputeOptions(artifactIdentity = "model", maxOutputTokens = 128)
    private val sampler = SamplerSettings(temperature = 0.2, seed = 7)
    private val history = listOf(RuntimeMessage(true, "Question"), RuntimeMessage(false, "Answer"))

    @Test
    fun unsupportedRuntimeFailsInsteadOfSilentlyKeepingOldSettings() = runBlocking {
        val runtime = object : BasicRuntime() {}

        val result = runtime.reconfigureConversation(options, sampler, history)

        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(0, runtime.loadCount)
    }

    @Test
    fun bothGuardsForwardSettingsAndHistoryWithoutReloadingEngine() = runBlocking {
        val native = RecordingRuntime()
        val runtime = ThermalGuardRuntimeAdapter(InferenceStallGuardRuntimeAdapter(native)) { 0 }

        assertTrue(runtime.reconfigureConversation(options, sampler, history).isSuccess)

        assertEquals(options, native.options)
        assertEquals(sampler, native.sampler)
        assertEquals(history, native.history)
        assertEquals(1, native.reconfigurationCount)
        assertEquals(0, native.loadCount)
        assertEquals(0, native.unloadCount)
    }

    @Test
    fun failedReconfigurationBlocksGenerationResetAndReloadUntilUnload() = runBlocking {
        val failure = IllegalStateException("allocation failed")
        val native = RecordingRuntime { Result.failure(failure) }
        val runtime = InferenceStallGuardRuntimeAdapter(native)

        assertSame(failure, runtime.reconfigureConversation(options, sampler, history).exceptionOrNull())
        assertTrue(runtime.resetConversation().isFailure)
        assertTrue(runtime.load("model").isFailure)
        assertEquals(0, native.loadCount)
        val error = runtime.generate("next").toList().single() as InferenceEvent.Error
        assertTrue(error.requiresReload)
        assertEquals(0, native.generateCount)

        runtime.unload()
        assertTrue(runtime.load("model").isSuccess)
        assertEquals(1, native.unloadCount)
        assertEquals(1, native.loadCount)
    }

    @Test(timeout = 5_000)
    fun reconfigurationTimeoutFailsClosedWithoutWaitingForNativeCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val native = RecordingRuntime {
            started.complete(Unit)
            check(release.await(2, java.util.concurrent.TimeUnit.SECONDS))
            Result.success(Unit)
        }
        val runtime = InferenceStallGuardRuntimeAdapter(native, lifecycleTimeoutMs = 100)
        try {
            val result = runtime.reconfigureConversation(options, sampler, history)
            assertTrue(started.isCompleted)
            assertTrue(result.isFailure)
            assertTrue(runtime.reconfigureConversation(options, sampler, history).isFailure)
            assertEquals(1, native.reconfigurationCount)
            assertTrue((runtime.generate("next").toList().single() as InferenceEvent.Error).requiresReload)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun cancellingReconfigurationCallerFailsClosedEvenIfWorkerWasDetached() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val native = RecordingRuntime {
            started.complete(Unit)
            awaitCancellation()
        }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        val caller = launch { runtime.reconfigureConversation(options, sampler, history) }
        started.await()

        caller.cancelAndJoin()

        assertTrue(runtime.resetConversation().isFailure)
        assertTrue(runtime.load("model").isFailure)
        assertFalse(runtime.supportsImageInput)
        assertEquals(0, native.loadCount)
    }

    private open class BasicRuntime : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        var loadCount = 0
        var unloadCount = 0
        var generateCount = 0
        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>): Result<Unit> {
            loadCount++
            return Result.success(Unit)
        }
        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> = Result.success(Unit)
        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> {
            generateCount++
            return flowOf(InferenceEvent.Complete)
        }
        override suspend fun cancel() = Unit
        override suspend fun unload() { unloadCount++ }
    }

    private class RecordingRuntime(
        private val reconfigure: suspend () -> Result<Unit> = { Result.success(Unit) },
    ) : BasicRuntime() {
        var options: ComputeOptions? = null
        var sampler: SamplerSettings? = null
        var history: List<RuntimeMessage>? = null
        var reconfigurationCount = 0
        override suspend fun reconfigureConversation(
            options: ComputeOptions, sampler: SamplerSettings, history: List<RuntimeMessage>,
        ): Result<Unit> {
            reconfigurationCount++
            this.options = options
            this.sampler = sampler
            this.history = history
            return reconfigure()
        }
    }
}
