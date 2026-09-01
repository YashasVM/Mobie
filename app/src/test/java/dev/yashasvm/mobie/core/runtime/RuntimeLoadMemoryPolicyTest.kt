package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLoadMemoryPolicyTest {
    @Test
    fun blocksWhenAndroidReportsActiveMemoryPressure() {
        assertNotNull(
            RuntimeLoadMemoryPolicy.blockReason(
                modelWeightsBytes = 350L * MIB,
                totalRamBytes = 8L * GIB,
                availableRamBytes = 5L * GIB,
                lowMemoryThresholdBytes = 512L * MIB,
                isLowMemory = true,
                isLowRamDevice = false,
            ),
        )
    }

    @Test
    fun blocksWhenCurrentFreeRamCannotSafelyFitRuntime() {
        assertNotNull(
            RuntimeLoadMemoryPolicy.blockReason(
                modelWeightsBytes = 2L * GIB,
                totalRamBytes = 8L * GIB,
                availableRamBytes = 2L * GIB,
                lowMemoryThresholdBytes = 512L * MIB,
                isLowMemory = false,
                isLowRamDevice = false,
            ),
        )
    }

    @Test
    fun allowsSmallModelWithHealthyHeadroom() {
        assertNull(
            RuntimeLoadMemoryPolicy.blockReason(
                modelWeightsBytes = 350L * MIB,
                totalRamBytes = 8L * GIB,
                availableRamBytes = 5L * GIB,
                lowMemoryThresholdBytes = 512L * MIB,
                isLowMemory = false,
                isLowRamDevice = false,
            ),
        )
    }

    @Test
    fun blocksModelLoadAtCriticalThermalPressure() {
        val reason = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 5L * GIB,
            lowMemoryThresholdBytes = 512L * MIB,
            isLowMemory = false,
            isLowRamDevice = false,
            thermalStatus = 4,
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("critical thermal pressure"))
    }

    @Test
    fun blocksGenerationWhenAndroidReportsLowMemory() {
        assertNotNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = true))
    }

    @Test
    fun blocksGenerationAtCriticalThermalPressure() {
        assertNotNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 4))
    }

    @Test
    fun allowsGenerationAtSevereButNotCriticalThermalPressure() {
        assertNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 3))
    }

    @Test
    fun allowsGenerationWhenAndroidIsNotUnderLowMemoryPressure() {
        assertNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false))
    }

    @Test
    fun throttlesGenerationMemoryChecksToTwicePerSecond() {
        assertFalse(RuntimeLoadMemoryPolicy.shouldRecheckGenerationMemory(lastCheckAtMs = 1_000, nowMs = 1_499))
        assertTrue(RuntimeLoadMemoryPolicy.shouldRecheckGenerationMemory(lastCheckAtMs = 1_000, nowMs = 1_500))
        assertTrue(RuntimeLoadMemoryPolicy.shouldRecheckGenerationMemory(lastCheckAtMs = 1_000, nowMs = 2_000))
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}