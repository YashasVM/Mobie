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
