package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
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
    fun resolvesRuntimeContextFromArtifactName() {
        assertEquals(65_536, runtimeContextWindowTokens("/models/qwen3-int4-c64k.litertlm"))
        assertEquals(32_768, runtimeContextWindowTokens("/models/gemma-context32768.litertlm"))
    }

    @Test
    fun usesConservativeRuntimeContextWhenArtifactHasNoMarker() {
        assertEquals(4_096, runtimeContextWindowTokens("/models/qwen3-int4.litertlm"))
    }

    @Test
    fun blocksLargeContextModelThatDefaultContextWouldAdmit() {
        val defaultContext = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 5L * GIB,
            lowMemoryThresholdBytes = 512L * MIB,
            isLowMemory = false,
            isLowRamDevice = false,
        )
        val largeContext = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 5L * GIB,
            lowMemoryThresholdBytes = 512L * MIB,
            isLowMemory = false,
            isLowRamDevice = false,
            contextWindowTokens = 65_536,
        )

        assertNull(defaultContext)
        assertNotNull(largeContext)
        assertTrue(largeContext!!.contains("Current free RAM"))
    }

    @Test
    fun blocksModelLoadAtSevereThermalPressure() {
        val reason = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 5L * GIB,
            lowMemoryThresholdBytes = 512L * MIB,
            isLowMemory = false,
            isLowRamDevice = false,
            thermalStatus = 3,
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("severe thermal pressure"))
    }

    @Test
    fun allowsModelLoadBelowSevereThermalPressure() {
        assertNull(
            RuntimeLoadMemoryPolicy.blockReason(
                modelWeightsBytes = 350L * MIB,
                totalRamBytes = 8L * GIB,
                availableRamBytes = 5L * GIB,
                lowMemoryThresholdBytes = 512L * MIB,
                isLowMemory = false,
                isLowRamDevice = false,
                thermalStatus = 2,
            ),
        )
    }

    @Test
    fun blocksGenerationWhenAndroidReportsLowMemory() {
        assertNotNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = true))
    }

    @Test
    fun blocksGenerationAtSevereThermalPressure() {
        val reason = RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 3)
        assertNotNull(reason)
        assertTrue(reason!!.contains("severe thermal pressure"))
    }

    @Test
    fun allowsGenerationBelowSevereThermalPressure() {
        assertNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 2))
    }

    @Test
    fun blocksGenerationBeforeCrossingAndroidLowMemoryThreshold() {
        val reason = RuntimeLoadMemoryPolicy.generationBlockReason(
            isLowMemory = false,
            availableRamBytes = 620L * MIB,
            lowMemoryThresholdBytes = 512L * MIB,
            totalRamBytes = 8L * GIB,
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("approaching Android's low-memory threshold"))
    }

    @Test
    fun allowsGenerationWithHealthyFreeRamHeadroom() {
        assertNull(
            RuntimeLoadMemoryPolicy.generationBlockReason(
                isLowMemory = false,
                availableRamBytes = 900L * MIB,
                lowMemoryThresholdBytes = 512L * MIB,
                totalRamBytes = 8L * GIB,
            ),
        )
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
