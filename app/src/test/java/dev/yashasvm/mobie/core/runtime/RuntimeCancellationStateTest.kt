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
    fun cleanupIsNeededBeforeAnyNativeCancellationAttempt() {
        val state = RuntimeCancellationState()

        assertTrue(state.shouldAttemptCleanup())
    }

    @Test
    fun successfulCancellationSuppressesDuplicateCleanupAttempt() {
        val state = RuntimeCancellationState()
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
        state.attempt { calls++ }

        assertEquals(2, calls)
        assertFalse(state.shouldAttemptCleanup())
    }

    @Test
    fun concurrentAttemptsSerializeAndIssueOnlyOneSuccessfulNativeCancellation() {
        val state = RuntimeCancellationState()
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
