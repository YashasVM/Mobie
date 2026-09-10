package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceStallGuardCallerCancellationTest {
    @Test
    fun callerCancellationStillRequestsNativeCancellation() = runBlocking {
        val sawToken = CompletableDeferred<Unit>()
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                emit(InferenceEvent.Token("partial"))
                delay(10_000L)
            },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 1_000L,
            activeIdleTimeoutMs = 1_000L,
            cancellationTimeoutMs = 100L,
        )
        val collection = launch {
            adapter.generate("prompt").collect { event ->
                if (event is InferenceEvent.Token) sawToken.complete(Unit)
            }
        }
        sawToken.await()

        collection.cancelAndJoin()

        assertTrue(delegate.cancelCalled)
    }

    @Test
    fun blockedNativeCancellationOnCallerStopFailsClosedWithoutHanging() = runBlocking {
        val sawToken = CompletableDeferred<Unit>()
        val delegate = RecordingRuntimeAdapter(
            generation = flow {
                emit(InferenceEvent.Token("partial"))
                delay(10_000L)
            },
            cancelBlock = { Thread.sleep(750L) },
        )
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 1_000L,
            activeIdleTimeoutMs = 1_000L,
            cancellationTimeoutMs = 50L,
        )
        val collection = launch {
            adapter.generate("prompt").collect { event ->
                if (event is InferenceEvent.Token) sawToken.complete(Unit)
            }
        }
        sawToken.await()

        val elapsedMs = measureTimeMillis { collection.cancelAndJoin() }

        assertTrue(delegate.cancelCalled)
        assertTrue("caller cancellation waited ${elapsedMs}ms for blocked native cancel", elapsedMs < 500L)
        assertTrue(adapter.load("model.litertlm", vision = false).isFailure)
        assertFalse(delegate.loadCalled)
    }

    private class RecordingRuntimeAdapter(
        private val generation: Flow<InferenceEvent>,
        private val cancelBlock: (() -> Unit)? = null,
    ) : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        @Volatile var cancelCalled = false
        @Volatile var loadCalled = false

        override suspend fun load(
            modelPath: String,
            vision: Boolean,
            history: List<RuntimeMessage>,
        ): Result<Unit> {
            loadCalled = true
            return Result.success(Unit)
        }

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
