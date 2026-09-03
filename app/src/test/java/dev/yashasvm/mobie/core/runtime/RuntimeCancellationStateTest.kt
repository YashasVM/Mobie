package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeCancellationStateTest {
    @Test
    fun cleanupIsNeededBeforeAnyNativeCancellationAttempt() {
        val state = RuntimeCancellationState()

        assertTrue(state.shouldAttemptCleanup())
    }

    @Test
    fun successfulCancellationSuppressesDuplicateCleanupAttempt() {
        val state = RuntimeCancellationState()
        var calls = 0

        val failure = state.attempt { calls++ }

        assertEquals(1, calls)
        assertEquals(null, failure)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun recoverableCancellationFailureAllowsCleanupRetry() {
        val state = RuntimeCancellationState()
        val expected = IllegalStateException("cancel failed")

        val failure = state.attempt { throw expected }

        assertSame(expected, failure)
        assertTrue(state.shouldAttemptCleanup())
    }

    @Test
    fun successfulRetryStopsFurtherCleanupAttempts() {
        val state = RuntimeCancellationState()
        var calls = 0

        state.attempt {
            calls++
            throw IllegalStateException("first cancel failed")
        }
        state.attempt { calls++ }

        assertEquals(2, calls)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun coroutineCancellationPreventsFurtherNativeCleanup() {
        val state = RuntimeCancellationState()

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

        try {
            state.attempt { throw OutOfMemoryError("fatal") }
            fail("Fatal failure should escape")
        } catch (_: OutOfMemoryError) {
            assertFalse(state.shouldAttemptCleanup())
        }
    }

    @Test
    fun resetAllowsCancellationForNextGeneration() {
        val state = RuntimeCancellationState()

        state.attempt { Unit }
        state.reset()

        assertTrue(state.shouldAttemptCleanup())
    }
}
