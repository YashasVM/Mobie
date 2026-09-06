package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InferenceStallGuardRuntimeAdapterTest {
    @Test
    fun cancelsGenerationThatStallsAfterStreamingStarts() = runBlocking {
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                emit(InferenceEvent.Token("hello"))
                delay(1_000L)
                emit(InferenceEvent.Complete)
            },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 20L,
            cancellationTimeoutMs = 100L,
        )

        val events = adapter.generate("prompt").toList()

        assertTrue(delegate.cancelCalled)
        assertEquals(InferenceEvent.Token("hello"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertTrue(error.message.contains("stopped making progress", ignoreCase = true))
    }

    @Test
    fun cancelsGenerationWhosePrefillNeverProducesAnEvent() = runBlocking {
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                delay(1_000L)
                emit(InferenceEvent.Complete)
            },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 20L,
            activeIdleTimeoutMs = 20L,
            cancellationTimeoutMs = 100L,
        )

        val events = adapter.generate("prompt").toList()

        assertTrue(delegate.cancelCalled)
        val error = events.single() as InferenceEvent.Error
        assertTrue(error.message.contains("did not start", ignoreCase = true))
    }

    @Test
    fun nonCooperativeNativeCollectorCannotHoldWatchdogOpen() = runBlocking {
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                emit(InferenceEvent.Token("partial"))
                Thread.sleep(750L)
                emit(InferenceEvent.Complete)
            },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 20L,
            cancellationTimeoutMs = 50L,
        )
        lateinit var events: List<InferenceEvent>

        val elapsedMs = measureTimeMillis {
            events = adapter.generate("prompt").toList()
        }

        assertTrue(delegate.cancelCalled)
        assertTrue("watchdog waited ${elapsedMs}ms for a non-cooperative collector", elapsedMs < 500L)
        assertEquals(InferenceEvent.Token("partial"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertTrue(error.message.contains("stopped making progress", ignoreCase = true))
    }

    @Test
    fun nonCooperativeNativeCancelCannotHoldWatchdogOpen() = runBlocking {
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                emit(InferenceEvent.Token("partial"))
                delay(1_000L)
            },
            cancelBlock = { Thread.sleep(750L) },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 20L,
            cancellationTimeoutMs = 50L,
        )
        lateinit var events: List<InferenceEvent>

        val elapsedMs = measureTimeMillis {
            events = adapter.generate("prompt").toList()
        }

        assertTrue(delegate.cancelCalled)
        assertTrue("watchdog waited ${elapsedMs}ms for a blocked native cancel", elapsedMs < 500L)
        assertEquals(InferenceEvent.Token("partial"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertTrue(error.message.contains("stopped making progress", ignoreCase = true))
    }

    @Test
    fun explicitStopReturnsBoundedFailureWhenNativeCancelBlocks() = runBlocking {
        val delegate = RecordingRuntimeAdapter(
            generation = flowOf(),
            cancelBlock = { Thread.sleep(750L) },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 100L,
            cancellationTimeoutMs = 50L,
        )
        var elapsedMs = 0L

        try {
            elapsedMs = measureTimeMillis { adapter.cancel() }
            fail("Expected bounded cancellation failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("did not return", ignoreCase = true))
        }

        assertTrue(delegate.cancelCalled)
        assertTrue("explicit Stop waited ${elapsedMs}ms for blocked native cancel", elapsedMs < 500L)
    }

    @Test
    fun healthyStreamingPassesThroughWithoutCancellation() = runBlocking {
        val expected = listOf(
            InferenceEvent.Token("hello"),
            InferenceEvent.Stats(InferenceStats(tokensPerSecond = 5.0, ramBytes = 100L)),
            InferenceEvent.Complete,
        )
        val delegate = RecordingRuntimeAdapter(generation = flowOf(*expected.toTypedArray()))
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 100L,
            cancellationTimeoutMs = 100L,
        )

        val actual = adapter.generate("prompt").toList()

        assertFalse(delegate.cancelCalled)
        assertEquals(expected, actual)
    }

    @Test
    fun missingTerminalEventBecomesExplicitFailure() = runBlocking {
        val delegate = RecordingRuntimeAdapter(generation = flowOf(InferenceEvent.Token("partial")))
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 100L,
            cancellationTimeoutMs = 100L,
        )

        val events = adapter.generate("prompt").toList()

        assertEquals(InferenceEvent.Token("partial"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertTrue(error.message.contains("without a completion signal", ignoreCase = true))
    }

    private class RecordingRuntimeAdapter(
        private val generation: Flow<InferenceEvent>,
        private val cancelBlock: (() -> Unit)? = null,
    ) : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        @Volatile var cancelCalled = false

        override suspend fun load(
            modelPath: String,
            vision: Boolean,
            history: List<RuntimeMessage>,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> = Result.success(Unit)

        override fun generate(
            prompt: String,
            imagePath: String?,
            config: GenerationConfig,
        ): Flow<InferenceEvent> = generation

        override suspend fun cancel() {
            cancelCalled = true
            cancelBlock?.invoke()
        }

        override suspend fun unload() = Unit
    }
}
