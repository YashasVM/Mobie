package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeReloadAdmissionTest {
    @Test
    fun refusedReloadLeavesPreviousRuntimeLoadedAndUsable() {
        val session = FakeSession("old chat")

        try {
            admitThenReleaseRuntime(
                admit = { throw IllegalStateException("Not enough memory for the new model.") },
                release = { session.release() },
            )
            fail("A refused admission must surface as a failure")
        } catch (expected: IllegalStateException) {
            assertEquals("Not enough memory for the new model.", expected.message)
        }

        assertTrue(session.loaded)
        assertEquals("answer to old chat", session.generate())
        assertEquals(0, session.releaseCount)
    }

    @Test
    fun previousRuntimeIsReleasedOnlyAfterAdmissionAndBeforeReplacementInstalls() {
        val events = mutableListOf<String>()
        val session = FakeSession("old chat")

        val admitted = admitThenReleaseRuntime(
            admit = { events += "admit"; "admitted plan" },
            release = { events += "release"; session.release() },
        )

        assertEquals("admitted plan", admitted)
        assertEquals(listOf("admit", "release"), events)
        assertFalse(session.loaded)
    }

    @Test
    fun reclaimCreditTurnsARefusedReloadIntoAnAdmittedOne() {
        val weights = 344_671_744L
        val context = 4_096
        val engineBytes = RuntimeLoadMemoryPolicy.estimatedRamBytes(weights, context, ComputeBackend.CPU, false)
        assertTrue(engineBytes > 0)
        // Free RAM alone is short of the new engine by 1 MiB, so the old engine must be credited.
        val available = engineBytes + 512L * MIB - MIB

        val withoutCredit = reloadBlockReason(weights, context, available, reclaimableBytes = 0L)
        assertNotNull(withoutCredit)
        assertNull(reloadBlockReason(weights, context, available, reclaimableBytes = engineBytes))
    }

    @Test
    fun reclaimCreditDoesNotPretendMemoryIsFreeWhenTotalRamCannotFit() {
        val weights = 344_671_744L
        val context = 4_096
        val engineBytes = RuntimeLoadMemoryPolicy.estimatedRamBytes(weights, context, ComputeBackend.CPU, false)
        val totalRam = engineBytes / 2

        // Total RAM decides whether a model can ever fit, so no amount of reclaim can change that verdict.
        assertNotNull(reloadBlockReason(weights, context, totalRam, reclaimableBytes = engineBytes, total = totalRam))
    }

    @Test
    fun negativeCreditIsIgnored() {
        val weights = 344_671_744L
        val context = 4_096
        val engineBytes = RuntimeLoadMemoryPolicy.estimatedRamBytes(weights, context, ComputeBackend.CPU, false)
        val available = engineBytes + 512L * MIB - MIB
        assertNotNull(reloadBlockReason(weights, context, available, reclaimableBytes = -engineBytes))
    }

    private fun reloadBlockReason(
        weights: Long,
        context: Int,
        available: Long,
        reclaimableBytes: Long,
        total: Long = 8L * GIB,
    ) = RuntimeLoadMemoryPolicy.blockReason(
        modelWeightsBytes = weights,
        totalRamBytes = total,
        availableRamBytes = available,
        lowMemoryThresholdBytes = 512L * MIB,
        isLowMemory = false,
        isLowRamDevice = false,
        contextWindowTokens = context,
        backend = ComputeBackend.CPU,
        reclaimableBytes = reclaimableBytes,
    )

    /** Stands in for a loaded native engine: it must stay usable after a refused reload. */
    private class FakeSession(private val history: String) {
        var loaded = true
            private set
        var releaseCount = 0
            private set

        fun generate(): String {
            check(loaded) { "Session was released" }
            return "answer to $history"
        }

        fun release() {
            releaseCount += 1
            loaded = false
        }
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
