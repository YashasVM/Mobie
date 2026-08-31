package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun blocksGenerationWhenAndroidReportsLowMemory() {
        assertNotNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = true))
    }

    @Test
    fun allowsGenerationWhenAndroidIsNotUnderLowMemoryPressure() {
        assertNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false))
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
