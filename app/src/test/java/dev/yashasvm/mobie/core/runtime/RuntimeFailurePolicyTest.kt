package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeFailurePolicyTest {
    @Test
    fun convertsRecoverableExceptionsIntoFailureResults() {
        val result = recoverableRuntimeResult<Unit> {
            throw IllegalStateException("recoverable")
        }

        assertTrue(result.isFailure)
        assertEquals("recoverable", result.exceptionOrNull()?.message)
    }

    @Test
    fun rethrowsCancellation() {
        try {
            recoverableRuntimeResult<Unit> {
                throw CancellationException("cancelled")
            }
            fail("CancellationException should propagate")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    @Test
    fun rethrowsFatalVmErrors() {
        try {
            recoverableRuntimeResult<Unit> {
                throw OutOfMemoryError("fatal")
            }
            fail("OutOfMemoryError should propagate")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal", error.message)
        }
    }

    @Test
    fun fatalBoundaryAllowsRecoverableExceptions() {
        rethrowFatalRuntimeFailure(IllegalStateException("recoverable"))
    }

    @Test
    fun fatalBoundaryAllowsCancellationForOrderedCleanup() {
        rethrowFatalRuntimeFailure(CancellationException("cancelled"))
    }

    @Test
    fun fatalBoundaryRethrowsFatalVmErrors() {
        try {
            rethrowFatalRuntimeFailure(OutOfMemoryError("fatal before cleanup"))
            fail("OutOfMemoryError should propagate before cleanup")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal before cleanup", error.message)
        }
    }

    @Test
    fun cleanupBoundaryRunsForRecoverableExceptions() {
        var cleanedUp = false

        runRuntimeCleanupUnlessFatal(IllegalStateException("recoverable")) {
            cleanedUp = true
        }

        assertTrue(cleanedUp)
    }

    @Test
    fun cleanupBoundaryRunsForCancellation() {
        var cleanedUp = false

        runRuntimeCleanupUnlessFatal(CancellationException("cancelled")) {
            cleanedUp = true
        }

        assertTrue(cleanedUp)
    }

    @Test
    fun cleanupBoundarySkipsFatalVmErrors() {
        var cleanedUp = false

        try {
            runRuntimeCleanupUnlessFatal(OutOfMemoryError("fatal before JNI cleanup")) {
                cleanedUp = true
            }
            fail("OutOfMemoryError should propagate before cleanup")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal before JNI cleanup", error.message)
        }

        assertFalse(cleanedUp)
    }

    @Test
    fun preservingCleanupKeepsRecoverablePrimaryAndSuppressesCleanupFailure() {
        val primary = IllegalStateException("memory pressure")

        runRuntimeCleanupPreservingPrimary(primary) {
            throw IllegalArgumentException("cancelProcess failed")
        }

        assertEquals("memory pressure", primary.message)
        assertEquals(1, primary.suppressed.size)
        assertEquals("cancelProcess failed", primary.suppressed.single().message)
    }

    @Test
    fun preservingCleanupRunsForCancellationWithoutReplacingIt() {
        val primary = CancellationException("generation cancelled")
        var cleanedUp = false

        runRuntimeCleanupPreservingPrimary(primary) {
            cleanedUp = true
        }

        assertTrue(cleanedUp)
        assertEquals("generation cancelled", primary.message)
    }

    @Test
    fun preservingCleanupSkipsJniAfterFatalPrimary() {
        var cleanedUp = false

        try {
            runRuntimeCleanupPreservingPrimary(OutOfMemoryError("fatal generation")) {
                cleanedUp = true
            }
            fail("Fatal primary failure should escape before cleanup")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal generation", error.message)
        }

        assertFalse(cleanedUp)
    }

    @Test
    fun preservingCleanupLetsFatalCleanupTakePrecedence() {
        val primary = IllegalStateException("memory pressure")

        try {
            runRuntimeCleanupPreservingPrimary(primary) {
                throw OutOfMemoryError("fatal cancel")
            }
            fail("Fatal cleanup failure should take precedence")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal cancel", error.message)
        }
    }

    @Test
    fun capturesRecoverableCancellationFailureForLaterTeardown() {
        val failure = captureRecoverableRuntimeFailure {
            throw IllegalStateException("cancelProcess failed")
        }

        assertEquals("cancelProcess failed", failure?.message)
    }

    @Test
    fun captureBoundaryRethrowsCancellation() {
        try {
            captureRecoverableRuntimeFailure {
                throw CancellationException("scope cancelled")
            }
            fail("CancellationException should propagate")
        } catch (error: CancellationException) {
            assertEquals("scope cancelled", error.message)
        }
    }

    @Test
    fun captureBoundaryRethrowsFatalVmErrors() {
        try {
            captureRecoverableRuntimeFailure {
                throw OutOfMemoryError("fatal cancel")
            }
            fail("OutOfMemoryError should propagate")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal cancel", error.message)
        }
    }

    @Test
    fun deferredFailureRunsTeardownBeforeRethrowingPrimaryFailure() {
        var cleanedUp = false
        val primary = IllegalStateException("cancelProcess failed")

        try {
            rethrowAfterRuntimeCleanup(primary) {
                cleanedUp = true
            }
            fail("Primary failure should propagate after teardown")
        } catch (error: IllegalStateException) {
            assertTrue(cleanedUp)
            assertEquals("cancelProcess failed", error.message)
        }
    }

    @Test
    fun deferredFailurePreservesRecoverableCleanupFailureAsSuppressed() {
        val primary = IllegalStateException("cancelProcess failed")

        try {
            rethrowAfterRuntimeCleanup(primary) {
                throw IllegalArgumentException("close failed")
            }
            fail("Primary failure should propagate")
        } catch (error: IllegalStateException) {
            assertEquals("cancelProcess failed", error.message)
            assertEquals(1, error.suppressed.size)
            assertEquals("close failed", error.suppressed.single().message)
        }
    }

    @Test
    fun deferredFailureStopsOnFatalCleanupFailure() {
        val primary = IllegalStateException("cancelProcess failed")

        try {
            rethrowAfterRuntimeCleanup(primary) {
                throw OutOfMemoryError("fatal close")
            }
            fail("Fatal cleanup failure should take precedence")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal close", error.message)
        }
    }

    @Test
    fun multiCleanupContinuesAfterRecoverableFailure() {
        val actions = mutableListOf<String>()

        try {
            runAllRuntimeCleanup(
                {
                    actions += "conversation"
                    throw IllegalStateException("conversation close failed")
                },
                { actions += "engine" },
            )
            fail("Recoverable cleanup failure should propagate after all cleanup runs")
        } catch (error: IllegalStateException) {
            assertEquals("conversation close failed", error.message)
        }

        assertEquals(listOf("conversation", "engine"), actions)
    }

    @Test
    fun multiCleanupPreservesAdditionalRecoverableFailuresAsSuppressed() {
        try {
            runAllRuntimeCleanup(
                { throw IllegalStateException("conversation close failed") },
                { throw IllegalArgumentException("engine close failed") },
            )
            fail("Cleanup failures should propagate")
        } catch (error: IllegalStateException) {
            assertEquals("conversation close failed", error.message)
            assertEquals(1, error.suppressed.size)
            assertEquals("engine close failed", error.suppressed.single().message)
        }
    }

    @Test
    fun multiCleanupStopsBeforeFurtherJniAfterFatalFailure() {
        var engineCleanupRan = false

        try {
            runAllRuntimeCleanup(
                { throw OutOfMemoryError("fatal conversation close") },
                { engineCleanupRan = true },
            )
            fail("Fatal cleanup failure should propagate immediately")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal conversation close", error.message)
        }

        assertFalse(engineCleanupRan)
    }

    @Test
    fun generationBoundaryAllowsRecoverableExceptions() {
        rethrowNonRecoverableRuntimeFailure(IllegalStateException("recoverable"))
    }

    @Test
    fun generationBoundaryRethrowsCancellation() {
        try {
            rethrowNonRecoverableRuntimeFailure(CancellationException("cancelled"))
            fail("CancellationException should propagate")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    @Test
    fun generationBoundaryRethrowsFatalVmErrors() {
        try {
            rethrowNonRecoverableRuntimeFailure(OutOfMemoryError("fatal generation"))
            fail("OutOfMemoryError should propagate")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal generation", error.message)
        }
    }
}
