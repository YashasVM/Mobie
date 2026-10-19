package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuntimeConversationResetGuardTest {
    private val history = listOf(RuntimeMessage(true, "Question"), RuntimeMessage(false, "Answer"))

    @Test fun successfulResetPreservesHistoryAndLoadedEngine() = runBlocking {
        val native = RecordingRuntime()
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        assertTrue(runtime.resetConversation(history).isSuccess)
        assertEquals(history, native.history)
        assertEquals(1, native.resetCount)
        assertSame(native.activeCompute, runtime.activeCompute)
        assertEquals(0, native.loadCount)
        assertEquals(0, native.unloadCount)
    }

    @Test fun failedResetRequiresUnloadBeforeAnyReuse() = runBlocking {
        val failure = IllegalStateException("reset allocation failed")
        val native = RecordingRuntime { Result.failure(failure) }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        assertSame(failure, runtime.resetConversation(history).exceptionOrNull())
        assertBlocked(runtime, native)
        runtime.unload()
        assertTrue(runtime.load("model").isSuccess)
        assertEquals(1, native.unloadCount)
        assertEquals(1, native.loadCount)
    }

    @Test fun thrownResetFailureIsReturnedAndFailsClosed() = runBlocking {
        val failure = IllegalStateException("native reset threw")
        val native = RecordingRuntime { throw failure }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        assertSame(failure, runtime.resetConversation(history).exceptionOrNull())
        assertBlocked(runtime, native)
    }

    @Test(timeout = 5_000) fun stuckResetDoesNotHoldCallerUntilNativeCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val native = RecordingRuntime {
            started.complete(Unit)
            check(release.await(2, TimeUnit.SECONDS))
            Result.success(Unit)
        }
        val runtime = InferenceStallGuardRuntimeAdapter(native, lifecycleTimeoutMs = 100)
        try {
            assertTrue(runtime.resetConversation(history).isFailure)
            assertTrue(started.isCompleted)
            assertEquals("Native call should still be blocked when watchdog returns", 1L, release.count)
            assertBlocked(runtime, native)
        } finally { release.countDown() }
    }

    @Test fun cancellingResetCallerFailsClosed() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val native = RecordingRuntime { started.complete(Unit); awaitCancellation() }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        val caller = launch { runtime.resetConversation(history) }
        started.await()
        caller.cancelAndJoin()
        assertBlocked(runtime, native)
    }

    @Test fun nativeAllocationErrorIsReturnedAsReloadRequiredFailure() = runBlocking {
        val failure = OutOfMemoryError("native allocation")
        val native = RecordingRuntime { throw failure }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        assertSame(failure, runtime.resetConversation(history).exceptionOrNull())
        assertBlocked(runtime, native)
    }

    @Test fun fatalResetFailureIsNotConvertedToOrdinaryFailure() = runBlocking {
        val fatal = StackOverflowError("native reset")
        val native = RecordingRuntime { throw fatal }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        try {
            runtime.resetConversation(history)
            fail("Fatal failure must propagate")
        } catch (actual: StackOverflowError) { assertSame(fatal, actual) }
        assertBlocked(runtime, native)
    }

    private suspend fun assertBlocked(runtime: InferenceStallGuardRuntimeAdapter, native: RecordingRuntime) {
        assertNull(runtime.activeCompute)
        assertFalse(runtime.supportsImageInput)
        assertTrue(runtime.resetConversation(history).isFailure)
        assertTrue(runtime.reconfigureConversation(ComputeOptions(), SamplerSettings(), history).isFailure)
        assertTrue(runtime.load("model").isFailure)
        assertTrue((runtime.generate("next").toList().single() as InferenceEvent.Error).requiresReload)
        assertEquals(1, native.resetCount)
        assertEquals(0, native.loadCount)
        assertEquals(0, native.generateCount)
    }

    private class RecordingRuntime(private val reset: suspend () -> Result<Unit> = { Result.success(Unit) }) : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        override val activeCompute = ActiveCompute(ComputeBackend.CPU, cpuThreads = 1, contextTokens = 1280)
        override val supportsImageInput = true
        var history: List<RuntimeMessage>? = null
        var resetCount = 0
        var loadCount = 0
        var unloadCount = 0
        var generateCount = 0
        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> {
            resetCount++
            this.history = history
            return reset()
        }
        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>): Result<Unit> {
            loadCount++
            return Result.success(Unit)
        }
        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> {
            generateCount++
            return flowOf(InferenceEvent.Complete)
        }
        override suspend fun cancel() = Unit
        override suspend fun unload() { unloadCount++ }
    }
}
