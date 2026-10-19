package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.CancellationException
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

class RuntimeLoadCancellationGuardTest {
    @Test fun cancelledLoadCallerCannotReuseUncertainEngine() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val native = RecordingRuntime { started.complete(Unit); awaitCancellation() }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        val caller = launch { runtime.load("model") }
        started.await()
        caller.cancelAndJoin()
        assertBlocked(runtime, native)
        runtime.unload()
        assertSame(native.activeCompute, runtime.activeCompute)
    }

    @Test fun nativeLoadCancellationPropagatesAndFailsClosed() = runBlocking {
        val failure = CancellationException("native load cancelled")
        val native = RecordingRuntime { throw failure }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        try { runtime.load("model"); fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(failure, actual) }
        assertBlocked(runtime, native)
    }

    @Test fun returnedAdmissionRefusalPreservesPreviouslyLoadedCompute() = runBlocking {
        val refusal = IllegalStateException("insufficient free memory")
        val native = RecordingRuntime { Result.failure(refusal) }
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        assertSame(refusal, runtime.load("replacement").exceptionOrNull())
        assertSame(native.activeCompute, runtime.activeCompute)
        assertTrue(runtime.resetConversation().isSuccess)
        assertEquals(listOf(InferenceEvent.Complete), runtime.generate("old conversation").toList())
    }

    private suspend fun assertBlocked(runtime: InferenceStallGuardRuntimeAdapter, native: RecordingRuntime) {
        assertNull(runtime.activeCompute)
        assertTrue(runtime.load("second").isFailure)
        assertTrue(runtime.resetConversation().isFailure)
        assertTrue((runtime.generate("next").toList().single() as InferenceEvent.Error).requiresReload)
        assertEquals(1, native.loadCount)
    }

    private class RecordingRuntime(private val load: suspend () -> Result<Unit>) : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        override val activeCompute = ActiveCompute(ComputeBackend.CPU, cpuThreads = 1, contextTokens = 1280)
        var loadCount = 0
        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>): Result<Unit> {
            loadCount++
            return load()
        }
        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> = Result.success(Unit)
        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> = flowOf(InferenceEvent.Complete)
        override suspend fun cancel() = Unit
        override suspend fun unload() = Unit
    }
}
