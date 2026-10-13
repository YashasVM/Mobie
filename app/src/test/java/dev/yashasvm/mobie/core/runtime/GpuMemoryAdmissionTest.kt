package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.estimateLiteRtRuntimeMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuMemoryAdmissionTest {
    @Test
    fun oneSessionBudgetCoversMeasuredContextSweepWithMargin() {
        val observedGiB = mapOf(1_024 to 1.54, 2_048 to 2.042, 4_096 to 3.2)
        for ((context, measured) in observedGiB) {
            val estimate = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(context), ComputeBackend.GPU))
            assertTrue("GPU budget at $context must exceed measured PSS", estimate.estimatedRamBytes > measured * GIB)
            assertTrue("No two-session allowance at $context", estimate.estimatedRamBytes < measured * GIB * 1.25)
            assertEquals(estimate.estimatedRamBytes, estimate.residentWeightBudgetBytes + estimate.anonymousBytes)
        }
    }

    @Test
    fun graphicsBudgetGrowsWithContextAndLargerArtifacts() {
        val short = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(1_024), ComputeBackend.GPU))
        val long = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(4_096), ComputeBackend.GPU))
        val larger = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(4_096).copy(sizeBytes = GIB), ComputeBackend.GPU))
        assertTrue(long.estimatedRamBytes > short.estimatedRamBytes)
        assertTrue(larger.runtimeOverheadBytes > long.runtimeOverheadBytes)
        assertTrue(larger.estimatedRamBytes > long.estimatedRamBytes)
    }

    @Test
    fun cpuEstimateAndUnknownArtifactHandlingRemainUnchanged() {
        assertEquals(estimateLiteRtRuntimeMemory(artifact()), RuntimeLoadMemoryPolicy.estimate(artifact(), ComputeBackend.CPU))
        assertNull(RuntimeLoadMemoryPolicy.estimate(artifact().copy(sizeBytes = 0), ComputeBackend.GPU))
        assertNull(RuntimeLoadMemoryPolicy.estimate(artifact().copy(format = ModelFormat.GGUF), ComputeBackend.GPU))
    }

    @Test
    fun autoKeepsGpuAtExactFreeMemoryBoundaryAndFallsBackOneByteBelow() {
        val estimate = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(), ComputeBackend.GPU))
        fun admitted(available: Long) = admitAttemptsForMemory(
            ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU),
        ) { backend -> blockReason(backend, available = available) }

        val exact = admitted(estimate.estimatedRamBytes + 512L * MIB)
        assertEquals(listOf(ComputeBackend.GPU, ComputeBackend.CPU), exact.attempts)
        assertNull(exact.skippedReason)
        val below = estimate.estimatedRamBytes + 512L * MIB - 1
        assertNull(blockReason(ComputeBackend.CPU, available = below))
        val fallback = admitted(below)
        assertEquals(listOf(ComputeBackend.CPU), fallback.attempts)
        assertTrue(fallback.skippedReason!!.startsWith("GPU skipped:"))
        assertTrue(fallback.skippedReason.contains("free about"))
        assertTrue(fallback.skippedReason.endsWith("Using CPU."))
        val loaded = mutableListOf<ComputeBackend>()
        val result = loadWithBackendFallback(fallback.attempts, load = { loaded += it; "engine" })
        assertEquals(listOf(ComputeBackend.CPU), loaded)
        assertEquals(ComputeBackend.CPU, result.backend)
    }

    @Test
    fun gpuAboveTotalMemoryBudgetCannotBeOverriddenButCpuFits() {
        val estimate = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(), ComputeBackend.GPU))
        val admission = RuntimeLoadMemoryPolicy.admit(estimate, 4L * GIB, 4L * GIB, 4L * GIB,
            256L * MIB, false, false)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, admission.tier)
        assertNotNull(admission.blockReason(allowReclaim = true))
        assertNull(blockReason(ComputeBackend.CPU, total = 4L * GIB, available = 4L * GIB, threshold = 256L * MIB))
        assertNotNull(blockReason(ComputeBackend.GPU, total = 4L * GIB, available = 4L * GIB,
            threshold = 256L * MIB, allowReclaim = true))
        val admitted = admitAttemptsForMemory(ComputeBackend.AUTO, listOf(ComputeBackend.GPU, ComputeBackend.CPU)) {
            blockReason(it, total = 4L * GIB, available = 4L * GIB, threshold = 256L * MIB, allowReclaim = true)
        }
        assertEquals(listOf(ComputeBackend.CPU), admitted.attempts)
        assertTrue(admitted.skippedReason!!.contains("more RAM than this phone can provide safely"))
    }

    @Test
    fun explicitGpuRemainsExplicitAndIsRejectedByItsLoadGate() {
        val reason = blockReason(ComputeBackend.GPU, available = 3L * GIB)
        assertNotNull(reason)
        assertNull(blockReason(ComputeBackend.CPU, available = 3L * GIB))
        val admitted = admitAttemptsForMemory(ComputeBackend.GPU, listOf(ComputeBackend.GPU)) { reason }
        assertEquals(listOf(ComputeBackend.GPU), admitted.attempts)
        assertNull(admitted.skippedReason)
    }

    private fun artifact(context: Int = 4_096) = ModelArtifact(
        "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", "", 344_671_744,
        format = ModelFormat.LITERT_LM, contextWindowTokens = context,
    )

    private fun blockReason(
        backend: ComputeBackend,
        total: Long = 8L * GIB,
        available: Long,
        threshold: Long = 512L * MIB,
        allowReclaim: Boolean = false,
    ) = RuntimeLoadMemoryPolicy.blockReason(
        modelWeightsBytes = 344_671_744,
        totalRamBytes = total,
        availableRamBytes = available,
        lowMemoryThresholdBytes = threshold,
        isLowMemory = false,
        isLowRamDevice = false,
        backend = backend,
        allowReclaim = allowReclaim,
    )

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
