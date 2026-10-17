package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The pre-flight resolver and the native load share LiteRtContextWindowPolicy.contextFor, so these
 * tests exercise the same context the load path allocates for each backend.
 */
class PerBackendContextTest {
    private val resolver = CompatibilityResolver()

    /** A mid-size artifact whose GPU estimate fits at 2048 tokens and not at 4096 on the chosen budget. */
    private val midSizeWeights = 1_200L * MIB
    private val midSizeDevice: DeviceProfile = buildMidSizeDevice()

    @Test
    fun gpuAutoContextShrinksToWhatFitsWhileCpuKeepsFullContext() {
        val device = midSizeDevice
        assertEquals(2_048, contextFor(ComputeBackend.GPU, device))
        assertEquals(4_096, contextFor(ComputeBackend.CPU, device))

        val gpu = resolver.resolve(midSizeArtifact(), device, backend = ComputeBackend.GPU)
        assertEquals(Compatibility.COMPATIBLE, gpu.status)
        assertEquals(2_048, gpu.contextWindowTokens)
        assertEquals(4_096, resolver.resolve(midSizeArtifact(), device, backend = ComputeBackend.CPU).contextWindowTokens)
    }

    @Test
    fun loadGateRefusesGpuAt4096ButAdmitsItAt2048() {
        val device = midSizeDevice
        assertNull(gateReason(ComputeBackend.GPU, contextTokens = 2_048, device = device))
        assertTrue(gateReason(ComputeBackend.GPU, contextTokens = 4_096, device = device)!!.isNotBlank())
        assertNull(gateReason(ComputeBackend.CPU, contextTokens = 4_096, device = device))
    }

    @Test
    fun autoRecommendationRanksGpuAtItsOwnContext() {
        val device = midSizeDevice.copy(gpuAttemptable = true)
        val admission = resolver.resolveBackend(midSizeArtifact(), device, backend = ComputeBackend.AUTO)
        assertEquals(ComputeBackend.GPU, admission.backend)
        assertEquals(2_048, admission.result.contextWindowTokens)
    }

    @Test
    fun fixedContextArtifactKeepsItsFixedValueAndIsNotShrunk() {
        // Even on a phone too small for the dynamic Auto size, a fixed artifact keeps its own context.
        val fixed = contextFor(
            maxContextTokens = 2_048,
            dynamicContext = false,
            requested = null,
            totalRam = 2L * GIB,
            backend = ComputeBackend.GPU,
        )
        assertEquals(2_048, fixed)
    }

    @Test
    fun fixedContextArtifactRejectsAnyOtherExplicitSize() {
        try {
            contextFor(maxContextTokens = 2_048, dynamicContext = false, requested = 1_024, backend = ComputeBackend.CPU)
            fail("A fixed artifact must reject a different explicit size")
        } catch (expected: IllegalArgumentException) {
            assertEquals("This artifact requires a fixed 2048-token context.", expected.message)
        }
    }

    @Test
    fun explicitOverrideIsHonouredByResolverAndLoadAlike() {
        val device = midSizeDevice
        // Auto would shrink GPU to 2048 here, so an explicit 4096 would be silently shrunk if either side ignored it.
        assertEquals(2_048, contextFor(ComputeBackend.GPU, device))

        val loadContext = contextFor(ComputeBackend.GPU, device, requested = 4_096)
        val verdict = resolver.resolve(midSizeArtifact(), device, backend = ComputeBackend.GPU, contextTokens = 4_096)

        assertEquals(4_096, loadContext)
        assertEquals(4_096, verdict.contextWindowTokens)
        // The verdict and the load gate must agree: both refuse the GPU allocation at 4096.
        assertEquals(verdict.status == Compatibility.INCOMPATIBLE, gateReason(ComputeBackend.GPU, 4_096, device) != null)
    }

    @Test
    fun explicitOverrideAboveTheArtifactLimitIsRejectedWithTheSameReasonEverywhere() {
        val device = midSizeDevice
        val artifact = midSizeArtifact(advertisedContext = 2_048)

        val verdict = resolver.resolve(artifact, device, backend = ComputeBackend.CPU, contextTokens = 4_096)
        assertEquals(Compatibility.INCOMPATIBLE, verdict.status)

        try {
            contextFor(maxContextTokens = 2_048, dynamicContext = true, requested = 4_096, backend = ComputeBackend.CPU)
            fail("The load path must refuse an override above the artifact limit")
        } catch (expected: IllegalArgumentException) {
            assertEquals(expected.message, verdict.reason)
        }
    }

    @Test
    fun resolverWithoutOverrideMatchesTheAutoContextTheLoadWillUse() {
        val device = midSizeDevice
        assertNotEquals(
            contextFor(ComputeBackend.GPU, device),
            contextFor(ComputeBackend.CPU, device),
        )
        assertEquals(
            contextFor(ComputeBackend.CPU, device),
            resolver.resolve(midSizeArtifact(), device, backend = ComputeBackend.CPU).contextWindowTokens,
        )
    }

    private fun midSizeArtifact(advertisedContext: Int = 4_096) = ModelArtifact(
        fileName = "mid-size-model.litertlm",
        downloadUrl = "",
        sizeBytes = midSizeWeights,
        format = ModelFormat.LITERT_LM,
        executionTarget = ArtifactExecutionTarget.GENERIC,
        contextWindowTokens = advertisedContext,
    )

    /**
     * Picks total RAM so that the GPU Auto budget sits between the estimates for 2048 and 2304
     * tokens. The 2048 result therefore fits and 4096 does not. The bounds are asserted, not assumed.
     */
    private fun buildMidSizeDevice(): DeviceProfile {
        val gpu2048 = gpuEstimate(2_048)
        val gpu2304 = gpuEstimate(2_304)
        val budget = gpu2048 + (gpu2304 - gpu2048) / 2
        // Budget is total minus a 10% reserve when the low-memory threshold is zero.
        val total = budget * 10 / 9 + 10
        val device = DeviceProfile(
            totalRamBytes = total,
            availableRamBytes = total,
            availableStorageBytes = 100L * GIB,
            supportedAbis = listOf("arm64-v8a"),
            sdkInt = 35,
            lowMemoryThresholdBytes = 0,
            availableAfterReclaimBytes = total,
        )
        val usableBudget = total - total / 10
        assertTrue("2048-token GPU estimate must fit the budget", gpu2048 <= usableBudget)
        assertTrue("2304-token GPU estimate must not fit the budget", gpu2304 > usableBudget)
        return device
    }

    private fun gpuEstimate(context: Int): Long = requireNotNull(
        RuntimeLoadMemoryPolicy.estimate(
            ModelArtifact("mid-size-model.litertlm", "", midSizeWeights,
                format = ModelFormat.LITERT_LM, contextWindowTokens = context),
            ComputeBackend.GPU,
        ),
    ).estimatedRamBytes

    private fun contextFor(
        backend: ComputeBackend,
        device: DeviceProfile,
        requested: Int? = null,
    ): Int = LiteRtContextWindowPolicy.contextFor(
        maxContextTokens = 4_096,
        dynamicContext = true,
        requestedContextTokens = requested,
        advertisedContextWindowTokens = 4_096,
        modelWeightsBytes = midSizeWeights,
        totalRamBytes = device.totalRamBytes,
        availableRamBytes = device.availableRamBytes,
        lowMemoryThresholdBytes = device.lowMemoryThresholdBytes,
        isLowRamDevice = device.isLowRamDevice,
        backend = backend,
    )

    private fun contextFor(
        maxContextTokens: Int,
        dynamicContext: Boolean,
        requested: Int?,
        totalRam: Long = 8L * GIB,
        backend: ComputeBackend,
    ): Int = LiteRtContextWindowPolicy.contextFor(
        maxContextTokens = maxContextTokens,
        dynamicContext = dynamicContext,
        requestedContextTokens = requested,
        advertisedContextWindowTokens = maxContextTokens,
        modelWeightsBytes = midSizeWeights,
        totalRamBytes = totalRam,
        availableRamBytes = totalRam,
        lowMemoryThresholdBytes = 0,
        isLowRamDevice = false,
        backend = backend,
    )

    /** Same admission the native load runs before allocating: a block reason means the load is refused. */
    private fun gateReason(backend: ComputeBackend, contextTokens: Int, device: DeviceProfile): String? =
        RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = midSizeWeights,
            totalRamBytes = device.totalRamBytes,
            availableRamBytes = device.availableRamBytes,
            lowMemoryThresholdBytes = device.lowMemoryThresholdBytes,
            isLowMemory = false,
            isLowRamDevice = device.isLowRamDevice,
            contextWindowTokens = contextTokens,
            backend = backend,
        )

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
