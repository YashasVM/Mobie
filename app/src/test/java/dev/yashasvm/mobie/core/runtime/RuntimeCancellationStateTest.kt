package dev.yashasvm.mobie.core.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeCancellationStateTest {
    @Test
    fun idleStateSkipsNativeCancellationAttempt() {
        val state = RuntimeCancellationState()
        var calls = 0

        val failure = state.attempt { calls++ }

        assertEquals(0, calls)
        assertEquals(null, failure)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun beginGenerationEnablesCancellationCleanup() {
        val state = RuntimeCancellationState()

        state.beginGeneration()

        assertTrue(state.isGenerationActive())
        assertTrue(state.shouldAttemptCleanup())
    }

    @Test
    fun successfulCancellationSuppressesDuplicateCleanupAttempt() {
        val state = RuntimeCancellationState()
        state.beginGeneration()
        var calls = 0

        val failure = state.attempt { calls++ }
        val duplicateFailure = state.attempt { calls++ }

        assertEquals(1, calls)
        assertEquals(null, failure)
        assertEquals(null, duplicateFailure)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun recoverableCancellationFailureAllowsCleanupRetry() {
        val state = RuntimeCancellationState()
        state.beginGeneration()
        val expected = IllegalStateException("cancel failed")

        val failure = state.attempt { throw expected }

        assertSame(expected, failure)
        assertTrue(state.shouldAttemptCleanup())
    }

    @Test
    fun successfulRetryStopsFurtherCleanupAttempts() {
        val state = RuntimeCancellationState()
        state.beginGeneration()
        var calls = 0

        state.attempt {
            calls++
            throw IllegalStateException("first cancel failed")
        }
        state.attempt { calls++ }
        state.attempt { calls++ }

        assertEquals(2, calls)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun concurrentAttemptsSerializeAndIssueOnlyOneSuccessfulNativeCancellation() {
        val state = RuntimeCancellationState()
        state.beginGeneration()
        val calls = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)

        val first = Thread {
            state.attempt {
                calls.incrementAndGet()
                firstEntered.countDown()
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
            }
        }
        val second = Thread {
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            state.attempt { calls.incrementAndGet() }
        }

        first.start()
        second.start()
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
        releaseFirst.countDown()
        first.join(2_000)
        second.join(2_000)

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(1, calls.get())
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun endGenerationReturnsStateToIdleAndSkipsLaterCancellation() {
        val state = RuntimeCancellationState()
        var calls = 0
        state.beginGeneration()
        state.attempt { calls++ }

        state.endGeneration()
        val idleFailure = state.attempt { calls++ }

        assertFalse(state.isGenerationActive())
        assertFalse(state.shouldAttemptCleanup())
        assertEquals(null, idleFailure)
        assertEquals(1, calls)
    }

    @Test
    fun coroutineCancellationPreventsFurtherNativeCleanup() {
        val state = RuntimeCancellationState()
        state.beginGeneration()

        try {
            state.attempt { throw CancellationException("cancelled") }
            fail("CancellationException should escape")
        } catch (_: CancellationException) {
            assertFalse(state.shouldAttemptCleanup())
        }
    }

    @Test
    fun fatalFailurePreventsFurtherNativeCleanup() {
        val state = RuntimeCancellationState()
        state.beginGeneration()

        try {
            state.attempt { throw OutOfMemoryError("fatal") }
            fail("Fatal failure should escape")
        } catch (_: OutOfMemoryError) {
            assertFalse(state.shouldAttemptCleanup())
        }
    }

    @Test
    fun resetReturnsStateToIdle() {
        val state = RuntimeCancellationState()
        state.beginGeneration()
        state.attempt { Unit }

        state.reset()

        assertFalse(state.isGenerationActive())
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun permitRemainsValidWithoutLifecycleTransition() {
        val state = RuntimeCancellationState()
        val permit = state.captureGenerationPermit()

        assertTrue(state.isGenerationPermitValid(permit))
    }

    @Test
    fun permitCapturedBeforeTransitionStaysInvalidAfterTransitionCompletes() {
        val state = RuntimeCancellationState()
        val permit = state.captureGenerationPermit()

        val transition = state.beginLifecycleTransition()
        state.endLifecycleTransition(transition)

        assertFalse(state.isGenerationPermitValid(permit))
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun permitCapturedDuringTransitionStaysInvalidAfterTransitionCompletes() {
        val state = RuntimeCancellationState()
        val transition = state.beginLifecycleTransition()
        val permit = state.captureGenerationPermit()

        state.endLifecycleTransition(transition)

        assertFalse(state.isGenerationPermitValid(permit))
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun overlappingLifecycleTransitionsKeepAdmissionClosedUntilAllComplete() {
        val state = RuntimeCancellationState()
        val beforeTransitions = state.captureGenerationPermit()
        val first = state.beginLifecycleTransition()
        val second = state.beginLifecycleTransition()

        state.endLifecycleTransition(first)

        assertFalse(state.isGenerationPermitValid(beforeTransitions))
        assertFalse(state.isGenerationPermitValid(state.captureGenerationPermit()))

        state.endLifecycleTransition(second)

        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun generationResetDoesNotClearLifecycleTransition() {
        val state = RuntimeCancellationState()
        val transition = state.beginLifecycleTransition()
        state.beginGeneration()

        state.reset()
        val permitDuringTransition = state.captureGenerationPermit()

        assertFalse(state.isGenerationActive())
        assertFalse(state.isGenerationPermitValid(permitDuringTransition))

        state.endLifecycleTransition(transition)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }
}
