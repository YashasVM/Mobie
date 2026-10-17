package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InferenceStallGuardFailureTest {
    @Test
    fun outOfMemoryInNativeGenerationBecomesReloadRequiredEvent() = runBlocking {
        val delegate = FakeRuntimeAdapter(generation = flow {
            emit(InferenceEvent.Token("partial"))
            throw OutOfMemoryError("native allocation failed")
        })
        val adapter = guard(delegate)

        val events = adapter.generate("prompt").toList()

        assertEquals(InferenceEvent.Token("partial"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertEquals("native allocation failed", error.message)
        assertTrue(error.requiresReload)
    }

    @Test
    fun runtimeAfterAnErrorRefusesReuseUntilUnload() = runBlocking {
        val delegate = FakeRuntimeAdapter(generation = flow { throw UnsatisfiedLinkError("libllm missing") })
        val adapter = guard(delegate)

        val first = adapter.generate("prompt").toList().single() as InferenceEvent.Error
        assertTrue(first.requiresReload)

        val blocked = adapter.generate("second prompt").toList().single() as InferenceEvent.Error
        assertTrue(blocked.requiresReload)
        assertEquals(1, delegate.generateCalls)

        adapter.unload()
        assertTrue(adapter.load("model.litertlm", vision = false).isSuccess)
    }

    @Test
    fun fatalVmErrorPropagatesToTheCollectorNotADetachedScope() = runBlocking {
        val delegate = FakeRuntimeAdapter(generation = flow { throw InternalError("JVM state corrupt") })
        val adapter = guard(delegate)

        try {
            adapter.generate("prompt").toList()
            fail("A truly fatal VM error must reach the caller")
        } catch (expected: InternalError) {
            assertEquals("JVM state corrupt", expected.message)
        }
    }

    @Test
    fun errorThrownByNativeLoadIsReturnedAsFailureAndFailsClosed() = runBlocking {
        val delegate = FakeRuntimeAdapter(loadFailure = OutOfMemoryError("load ran out of memory"))
        val adapter = guard(delegate)

        val result = adapter.load("model.litertlm", vision = false)

        assertTrue(result.isFailure)
        assertEquals("load ran out of memory", result.exceptionOrNull()?.message)
        val blocked = adapter.load("model.litertlm", vision = false)
        assertTrue(blocked.isFailure)
        assertEquals(1, delegate.loadCalls)
    }

    @Test
    fun hungLoadReturnsBoundedFailureAndUnloadRestoresTheRuntime() = runBlocking {
        val releaseNativeLoad = CountDownLatch(1)
        val delegate = FakeRuntimeAdapter(loadBlock = { releaseNativeLoad.await(10, TimeUnit.SECONDS) })
        val adapter = guard(delegate, loadTimeoutMs = 100L)

        try {
            var result: Result<Unit>? = null
            val elapsedMs = measureTimeMillis { result = adapter.load("model.litertlm", vision = false) }

            assertTrue("load waited ${elapsedMs}ms for a hung native load", elapsedMs < 2_000L)
            assertTrue(result!!.isFailure)
            assertTrue(result!!.exceptionOrNull()!!.message!!.contains("safety timeout"))
            assertEquals(1, delegate.loadCalls)

            // Fails closed: no second native load runs while the first may still hold native state.
            assertTrue(adapter.load("model.litertlm", vision = false).isFailure)
            assertEquals(1, delegate.loadCalls)
        } finally {
            releaseNativeLoad.countDown()
        }

        // A bounded unload clears the failure and the lifecycle is usable again.
        adapter.unload()
        assertTrue(adapter.load("model.litertlm", vision = false).isSuccess)
        assertEquals(2, delegate.loadCalls)
    }

    private fun guard(delegate: RuntimeAdapter, loadTimeoutMs: Long = 5_000L) = InferenceStallGuardRuntimeAdapter(
        delegate = delegate,
        firstEventTimeoutMs = 1_000L,
        activeIdleTimeoutMs = 1_000L,
        cancellationTimeoutMs = 100L,
        lifecycleTimeoutMs = 100L,
        loadTimeoutMs = loadTimeoutMs,
    )

    private class FakeRuntimeAdapter(
        private val generation: Flow<InferenceEvent> = flowOf(),
        private val loadFailure: Throwable? = null,
        private val loadBlock: (() -> Unit)? = null,
    ) : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        @Volatile var generateCalls = 0
        @Volatile var loadCalls = 0

        override suspend fun load(
            modelPath: String,
            vision: Boolean,
            history: List<RuntimeMessage>,
        ): Result<Unit> {
            loadCalls += 1
            loadBlock?.invoke()
            loadFailure?.let { throw it }
            return Result.success(Unit)
        }

        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> = Result.success(Unit)

        override fun generate(
            prompt: String,
            imagePath: String?,
            config: GenerationConfig,
        ): Flow<InferenceEvent> {
            generateCalls += 1
            return generation
        }

        override suspend fun cancel() = Unit

        override suspend fun unload() = Unit
    }
}
