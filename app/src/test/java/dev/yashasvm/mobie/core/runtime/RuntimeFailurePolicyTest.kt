package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
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
