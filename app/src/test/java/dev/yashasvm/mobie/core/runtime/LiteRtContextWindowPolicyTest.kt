package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtContextWindowPolicyTest {
    @Test
    fun keepsAdvertisedContextWhenItFitsConservativeRamBudget() {
        val selected = LiteRtContextWindowPolicy.select(
            advertisedContextWindowTokens = 65_536,
            modelWeightsBytes = 1L * GIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 7L * GIB,
            lowMemoryThresholdBytes = 256L * MIB,
            isLowRamDevice = false,
        )

        assertEquals(65_536, selected)
    }

    @Test
    fun shrinksExtendedContextWhenCurrentFreeRamCannotSafelyBackFullKvCache() {
        val selected = LiteRtContextWindowPolicy.select(
            advertisedContextWindowTokens = 65_536,
            modelWeightsBytes = 1L * GIB,
            totalRamBytes = 4L * GIB,
            availableRamBytes = 2_500L * MIB,
            lowMemoryThresholdBytes = 192L * MIB,
            isLowRamDevice = false,
        )

        assertTrue(selected in 4_096 until 65_536)
        assertEquals(0, selected % 256)
    }

    @Test
    fun lowRamDevicesUseStricterAvailableMemoryFraction() {
        val normal = LiteRtContextWindowPolicy.select(
            advertisedContextWindowTokens = 32_768,
            modelWeightsBytes = 768L * MIB,
            totalRamBytes = 4L * GIB,
            availableRamBytes = 2_800L * MIB,
            lowMemoryThresholdBytes = 192L * MIB,
            isLowRamDevice = false,
        )
        val lowRam = LiteRtContextWindowPolicy.select(
            advertisedContextWindowTokens = 32_768,
            modelWeightsBytes = 768L * MIB,
            totalRamBytes = 4L * GIB,
            availableRamBytes = 2_800L * MIB,
            lowMemoryThresholdBytes = 192L * MIB,
            isLowRamDevice = true,
        )

        assertTrue(lowRam < normal)
    }

    @Test
    fun preservesAdvertisedContextWhenMemoryTelemetryIsUnavailable() {
        assertEquals(
            16_384,
            LiteRtContextWindowPolicy.select(
                advertisedContextWindowTokens = 16_384,
                modelWeightsBytes = 512L * MIB,
                totalRamBytes = 0,
                availableRamBytes = 0,
                lowMemoryThresholdBytes = 0,
                isLowRamDevice = false,
            ),
        )
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}