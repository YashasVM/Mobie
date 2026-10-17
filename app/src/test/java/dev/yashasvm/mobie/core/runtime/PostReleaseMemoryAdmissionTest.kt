package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.*
import org.junit.Test

class PostReleaseMemoryAdmissionTest {
    @Test
    fun outgoingEngineCreditIsUsedBeforeReleaseButNeverAddedToAFreshPostReleaseSample() {
        val weights = 344_671_744L
        val oldEngine = RuntimeLoadMemoryPolicy.estimatedRamBytes(weights, 4096, ComputeBackend.CPU, false)
        val gpu = RuntimeLoadMemoryPolicy.estimatedRamBytes(weights, 4096, ComputeBackend.GPU, false)
        val reserve = 512L * MIB
        var freeRam = gpu - oldEngine + reserve
        val events = mutableListOf<String>()
        fun reason(credit: Long) = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = weights, totalRamBytes = 12L * GIB, availableRamBytes = freeRam,
            lowMemoryThresholdBytes = reserve, isLowMemory = false, isLowRamDevice = false,
            contextWindowTokens = 4096, backend = ComputeBackend.GPU, reclaimableBytes = credit,
        )
        admitThenReleaseRuntime(
            admit = { events += "admit"; assertNull(reason(oldEngine)) },
            release = {
                events += "release"
                // A fresh Android sample shows most, but not all, of the outgoing RAM released.
                freeRam += oldEngine - MIB
            },
        )
        val attempts = admitAttemptsForMemory(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU)) {
            events += "sample-$it"
            reason(credit = 0)
        }
        assertEquals(listOf(ComputeBackend.CPU), attempts.attempts)
        assertEquals(listOf("admit", "release", "sample-GPU"), events)
        // Crediting the outgoing allocation a second time would wrongly admit the GPU attempt.
        assertNull(reason(oldEngine))
        assertNotNull(reason(0))
    }
    private companion object { const val MIB = 1024L * 1024; const val GIB = 1024L * MIB }
}
