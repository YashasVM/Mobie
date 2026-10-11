package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.estimateLiteRtRuntimeMemory
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
    fun blocksPackagesWhoseAdvertisedContextIsBelowUsefulMinimum() {
        val reason = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            totalRamBytes = 8L * GIB,
            availableRamBytes = 5L * GIB,
            lowMemoryThresholdBytes = 512L * MIB,
            isLowMemory = false,
            isLowRamDevice = false,
            contextWindowTokens = 512,
        )

        assertNotNull(reason)
        assertTrue(reason!!.contains("1,024-token minimum"))
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
        assertTrue(largeContext!!.contains("free about"))
    }

    @Test
    fun allowsModelLoadAtSevereThermalPressureForThrottledInference() {
        assertNull(
            RuntimeLoadMemoryPolicy.blockReason(
                modelWeightsBytes = 350L * MIB,
                totalRamBytes = 8L * GIB,
                availableRamBytes = 5L * GIB,
                lowMemoryThresholdBytes = 512L * MIB,
                isLowMemory = false,
                isLowRamDevice = false,
                thermalStatus = 3,
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
    fun allowsGenerationAtSevereThermalPressureForOuterTokenCap() {
        assertNull(RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 3))
    }

    @Test
    fun blocksGenerationAtCriticalThermalPressure() {
        val reason = RuntimeLoadMemoryPolicy.generationBlockReason(isLowMemory = false, thermalStatus = 4)
        assertNotNull(reason)
        assertTrue(reason!!.contains("critical thermal pressure"))
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

    @Test fun allThreeTiersAndOverrideBoundary() {
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(
            ModelArtifact("model.litertlm", "", 3L * GIB, format = ModelFormat.LITERT_LM)))
        val now = RuntimeLoadMemoryPolicy.admit(estimate, 12L * GIB, 6L * GIB, 7L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.FITS_NOW, now.tier)
        assertNull(now.blockReason(false))

        val reclaim = RuntimeLoadMemoryPolicy.admit(estimate, 12L * GIB, 2L * GIB, 5L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.FITS_AFTER_RECLAIM, reclaim.tier)
        assertNotNull(reclaim.blockReason(false))
        assertNull(reclaim.blockReason(true))

        val impossible = RuntimeLoadMemoryPolicy.admit(estimate, 3L * GIB, 2L * GIB, 3L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, impossible.tier)
        assertNotNull(impossible.blockReason(true))
    }

    @Test fun gpuAndVisionEstimatesIncludePrivateMemory() {
        val artifact = ModelArtifact("vision.litertlm", "", 2L * GIB, format = ModelFormat.LITERT_LM)
        val cpu = requireNotNull(estimateLiteRtRuntimeMemory(artifact))
        val gpuVision = requireNotNull(estimateLiteRtRuntimeMemory(artifact,
            privateWeightCopy = ComputeBackend.GPU.privateWeightCopy, visionEncoderLoaded = true))
        assertEquals(artifact.sizeBytes, cpu.fileBackedWeightsBytes)
        assertTrue(cpu.anonymousBytes >= cpu.kvCacheBytes + cpu.runtimeOverheadBytes)
        assertTrue(gpuVision.anonymousBytes >= cpu.anonymousBytes + artifact.sizeBytes + 512L * MIB)
        assertTrue(gpuVision.estimatedRamBytes > cpu.estimatedRamBytes)
    }

    @Test fun onlyCpuMapsWeightsWithoutPrivateCopy() {
        assertFalse(ComputeBackend.CPU.privateWeightCopy)
        assertTrue(ComputeBackend.GPU.privateWeightCopy)
        assertTrue(ComputeBackend.NPU.privateWeightCopy)
        // AUTO is unknown until the runtime picks, so admission assumes the expensive case.
        assertTrue(ComputeBackend.AUTO.privateWeightCopy)
    }

    @Test fun mappedWeightsAreBudgetedAsWorkingSetNotWholeFile() {
        val artifact = ModelArtifact("big.litertlm", "", 4L * GIB, format = ModelFormat.LITERT_LM)
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(artifact))
        assertEquals(2L * GIB, estimate.residentWeightBudgetBytes)
        assertEquals(4L * GIB, estimate.fileBackedWeightsBytes)
        assertEquals(estimate.residentWeightBudgetBytes + estimate.anonymousBytes, estimate.estimatedRamBytes)
    }

    @Test fun middleTierExplainsHowMuchMemoryToFree() {
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(
            ModelArtifact("model.litertlm", "", 3L * GIB, format = ModelFormat.LITERT_LM)))
        // Total RAM can hold the model, but neither current nor reclaimable memory covers it.
        val admission = RuntimeLoadMemoryPolicy.admit(estimate, 12L * GIB, 1L * GIB, 1L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.FITS_AFTER_RECLAIM, admission.tier)
        assertTrue(admission.bytesToFree > 0)
        assertTrue(admission.reason.contains("Close other apps to free about"))
        assertNull(admission.blockReason(allowReclaim = true))
    }

    @Test fun estimateAboveTotalBudgetIsImpossibleEvenWithOverride() {
        // A 2 GiB file stays under the 80% file rule, but a 32K KV cache alone exceeds the 6 GiB
        // phone's total budget (6 GiB minus a 0.6 GiB reserve), so no amount of reclaim helps.
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(
            ModelArtifact("model.litertlm", "", 2L * GIB, format = ModelFormat.LITERT_LM,
                contextWindowTokens = 32_768)))
        assertTrue(estimate.estimatedRamBytes > 6L * GIB - 6L * GIB / 10)
        val admission = RuntimeLoadMemoryPolicy.admit(estimate, 6L * GIB, 4L * GIB, 4L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, admission.tier)
        assertNotNull(admission.blockReason(allowReclaim = true))
    }

    @Test fun fileLargerThanEightyPercentOfRamIsImpossibleEvenIfResidentFits() {
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(
            ModelArtifact("model.litertlm", "", 4L * GIB, format = ModelFormat.LITERT_LM)))
        // Estimate (3.5 GiB) fits the 3.5 GiB total budget, but the 4 GiB file exceeds 80% of RAM.
        val admission = RuntimeLoadMemoryPolicy.admit(estimate, 4L * GIB, 4L * GIB, 4L * GIB,
            512L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, admission.tier)
    }

    @Test fun fitsNowRequiresNoLowMemorySignal() {
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(
            ModelArtifact("model.litertlm", "", 2L * GIB, format = ModelFormat.LITERT_LM)))
        val pressured = RuntimeLoadMemoryPolicy.admit(estimate, 12L * GIB, 8L * GIB, 8L * GIB,
            512L * MIB, true, false)
        assertEquals(MemoryAdmissionTier.FITS_AFTER_RECLAIM, pressured.tier)
        assertNotNull(pressured.blockReason(allowReclaim = false))
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
