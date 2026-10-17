package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeCleanupGuardTest {
    @Test
    fun failedCloseCannotBecomeSuccessfulUnloadOrAllowAnotherLoad() {
        val guard = NativeCleanupGuard()
        var closes = 0
        val failure = IllegalStateException("JNI close failed after freeing an unknown subset of resources")
        try {
            guard.close { closes++; throw failure }
            fail("Close must report its failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error.cause)
        }
        for (operation in listOf<() -> Unit>({ guard.close { closes++ } }, { guard.ensureSafe() })) {
            try {
                operation()
                fail("Uncertain native ownership must require a restart")
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("Restart Mobie"))
                assertEquals(failure, error.cause)
            }
        }
        assertEquals(1, closes)
    }

    @Test
    fun uncertainCleanupAlwaysProducesAReloadRequiredEvent() {
        val event = runtimeFailureEvent(NativeCleanupException(IllegalStateException("close failed")))
        assertTrue(event.requiresReload)
        assertTrue(event.message.contains("Restart Mobie"))
    }

    @Test
    fun successfulCloseAllowsLaterLoadsAndRepeatedEmptyUnload() {
        val guard = NativeCleanupGuard()
        var closes = 0
        guard.close { closes++ }
        guard.ensureSafe()
        guard.close { closes++ }
        assertEquals(2, closes)
    }
}
