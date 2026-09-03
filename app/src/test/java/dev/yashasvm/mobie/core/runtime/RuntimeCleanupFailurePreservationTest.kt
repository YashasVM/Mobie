package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RuntimeCleanupFailurePreservationTest {
    @Test
    fun recoverableCleanupFailureStaysSuppressedBehindPrimaryFailure() {
        val primary = IllegalStateException("conversation creation failed")

        runRuntimeCleanupUnlessFatal(primary) {
            throw IllegalArgumentException("engine close failed")
        }

        assertEquals("conversation creation failed", primary.message)
        assertEquals(1, primary.suppressed.size)
        assertEquals("engine close failed", primary.suppressed.single().message)
    }

    @Test
    fun fatalCleanupFailureStillTakesPrecedence() {
        val primary = IllegalStateException("engine initialize failed")

        try {
            runRuntimeCleanupUnlessFatal(primary) {
                throw OutOfMemoryError("fatal cleanup")
            }
            fail("Fatal cleanup failure should take precedence")
        } catch (error: OutOfMemoryError) {
            assertEquals("fatal cleanup", error.message)
        }
    }
}
