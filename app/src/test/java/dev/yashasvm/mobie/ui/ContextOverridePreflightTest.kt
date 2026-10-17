package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeLoadMemoryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A saved context override must change the pre-flight verdict the same way the native load gate does.
 * The device is sized so GPU Auto fits at 2048 tokens but not at 4096.
 */
class ContextOverridePreflightTest {
    private val resolver = CompatibilityResolver()
    private val weights = 1_200L * MIB
    private val artifact = ModelArtifact(
        fileName = "context-override-model.litertlm",
        downloadUrl = "",
        sizeBytes = weights,
        format = ModelFormat.LITERT_LM,
        executionTarget = ArtifactExecutionTarget.GENERIC,
        contextWindowTokens = 4_096,
    )
    private val capabilities = ComputeCapabilities(4_096, dynamicContext = true, supportsThinking = false, supportsSpeculativeDecoding = false)
    private val device = buildDevice()

    @Test
    fun autoGpuVerdictIsJudgedAtTheAutoContext() {
        val verdict = gpuVerdict(ComputeOptions(backend = ComputeBackend.GPU))

        assertEquals(Compatibility.COMPATIBLE, verdict.status)
        assertEquals(2_048, verdict.contextWindowTokens)
    }

    @Test
    fun savedOverrideAboveWhatFitsTurnsThePreflightIntoTheSameRefusalAsTheLoadGate() {
        val verdict = gpuVerdict(ComputeOptions(backend = ComputeBackend.GPU, contextTokens = 4_096))

        assertEquals(4_096, verdict.contextWindowTokens)
        assertEquals(Compatibility.INCOMPATIBLE, verdict.status)
        assertNotNull(gateBlockReason(contextTokens = 4_096))
    }

    @Test
    fun savedOverrideThatFitsIsAdmittedAtExactlyThatContextAndTheGateAgrees() {
        val verdict = gpuVerdict(ComputeOptions(backend = ComputeBackend.GPU, contextTokens = 2_048))

        assertEquals(2_048, verdict.contextWindowTokens)
        assertEquals(Compatibility.COMPATIBLE, verdict.status)
        assertNull(gateBlockReason(contextTokens = 2_048))
    }

    @Test
    fun overrideIsIgnoredWhenTheArtifactMetadataDoesNotAllowIt() {
        val options = ComputeOptions(backend = ComputeBackend.GPU, contextTokens = 4_096)
        val smallArtifact = capabilities.copy(maxContextTokens = 2_048)

        // Same rule the load applies: a context the metadata does not offer is never requested.
        assertNull(AdvancedComputePolicy.contextOverride(options, smallArtifact))
        val verdict = AdvancedComputePolicy.backendAdmissions(device, artifact, false, options, smallArtifact, resolver)
            .getValue(ComputeBackend.GPU).result
        assertEquals(2_048, verdict.contextWindowTokens)
        assertEquals(Compatibility.COMPATIBLE, verdict.status)
    }

    private fun gpuVerdict(options: ComputeOptions): CompatibilityResult =
        AdvancedComputePolicy.backendAdmissions(device, artifact, false, options, capabilities, resolver)
            .getValue(ComputeBackend.GPU).result

    /** Same admission the native load runs before allocating: a block reason means the load is refused. */
    private fun gateBlockReason(contextTokens: Int): String? = RuntimeLoadMemoryPolicy.blockReason(
        modelWeightsBytes = weights,
        totalRamBytes = device.totalRamBytes,
        availableRamBytes = device.availableRamBytes,
        lowMemoryThresholdBytes = device.lowMemoryThresholdBytes,
        isLowMemory = false,
        isLowRamDevice = device.isLowRamDevice,
        contextWindowTokens = contextTokens,
        backend = ComputeBackend.GPU,
    )

    /** Picks total RAM so the GPU budget sits between the 2048 and 2304 estimates, and asserts those bounds. */
    private fun buildDevice(): DeviceProfile {
        val gpu2048 = gpuEstimate(2_048)
        val gpu2304 = gpuEstimate(2_304)
        val budget = gpu2048 + (gpu2304 - gpu2048) / 2
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
            ModelArtifact("context-override-model.litertlm", "", weights,
                format = ModelFormat.LITERT_LM, contextWindowTokens = context),
            ComputeBackend.GPU,
        ),
    ).estimatedRamBytes

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
