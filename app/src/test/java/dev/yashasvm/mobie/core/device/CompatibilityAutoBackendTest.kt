package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.RuntimeLoadMemoryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compatibility must quote the same RAM the native load gate admits, per backend and context. */
class CompatibilityAutoBackendTest {
    private val resolver = CompatibilityResolver()
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib
    private val threshold = 512 * mib
    private val weights = 344_671_744L

    @Test
    fun `auto shows CPU with a reason when GPU does not fit now but CPU does`() {
        val between = (estimate(ComputeBackend.CPU, 4_096) + estimate(ComputeBackend.GPU, 4_096)) / 2
        val admission = resolver.resolveBackend(artifact(4_096), device(threshold + between), backend = ComputeBackend.AUTO)

        assertEquals(ComputeBackend.CPU, admission.backend)
        assertEquals(Compatibility.COMPATIBLE, admission.result.status)
        assertEquals(MemoryAdmissionTier.FITS_NOW, admission.result.memoryTier)
        assertEquals(estimate(ComputeBackend.CPU, 4_096), admission.result.estimatedRamBytes)
        assertTrue(admission.result.reason.startsWith("GPU needs about"))
        assertTrue(admission.result.reason.contains("Auto uses CPU."))
        // Explicit GPU is judged on its own and is not silently replaced by CPU.
        val explicitGpu = resolver.resolve(artifact(4_096), device(threshold + between), backend = ComputeBackend.GPU)
        assertEquals(Compatibility.WARNING, explicitGpu.status)
        assertEquals(estimate(ComputeBackend.GPU, 4_096), explicitGpu.estimatedRamBytes)
    }

    @Test
    fun `auto picks GPU and quotes its RAM when both backends fit now`() {
        val roomy = device(threshold + estimate(ComputeBackend.GPU, 4_096) + 256 * mib)
        val admission = resolver.resolveBackend(artifact(4_096), roomy, backend = ComputeBackend.AUTO)

        assertEquals(ComputeBackend.GPU, admission.backend)
        assertEquals(Compatibility.COMPATIBLE, admission.result.status)
        assertEquals(estimate(ComputeBackend.GPU, 4_096), admission.result.estimatedRamBytes)
    }

    @Test
    fun `auto needs accepted reclaim before it picks GPU that only fits after reclaim`() {
        val tight = device(threshold + estimate(ComputeBackend.CPU, 4_096) / 2)

        val declined = resolver.resolveBackend(artifact(4_096), tight, backend = ComputeBackend.AUTO)
        assertEquals(ComputeBackend.CPU, declined.backend)
        assertEquals(Compatibility.WARNING, declined.result.status)
        assertEquals(MemoryAdmissionTier.FITS_AFTER_RECLAIM, declined.result.memoryTier)
        assertTrue(declined.result.reason.contains("GPU needs about"))

        // Accepting reclaim lets the load gate admit GPU too, so the quoted backend must follow it.
        val accepted = resolver.resolveBackend(artifact(4_096), tight, backend = ComputeBackend.AUTO, allowReclaim = true)
        assertEquals(ComputeBackend.GPU, accepted.backend)
        assertEquals(estimate(ComputeBackend.GPU, 4_096), accepted.result.estimatedRamBytes)
    }

    @Test
    fun `GPU impossible by total RAM falls back to CPU with a reason`() {
        val threeGib = device(available = 3 * gib, total = 3 * gib)
        val admission = resolver.resolveBackend(artifact(4_096), threeGib, backend = ComputeBackend.AUTO)

        assertEquals(ComputeBackend.CPU, admission.backend)
        assertEquals(Compatibility.COMPATIBLE, admission.result.status)
        assertTrue(admission.result.reason.contains("more than this phone can provide safely"))
    }

    @Test
    fun `neither backend can ever fit total RAM so auto is incompatible with CPU's reason`() {
        val oneGib = device(available = gib, total = gib)
        val admission = resolver.resolveBackend(artifact(4_096), oneGib, backend = ComputeBackend.AUTO)

        assertEquals(ComputeBackend.CPU, admission.backend)
        assertEquals(Compatibility.INCOMPATIBLE, admission.result.status)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, admission.result.memoryTier)
        assertTrue(admission.result.reason.contains("more RAM than this phone can provide safely"))
        assertFalse(admission.result.reason.contains("GPU needs"))
    }

    @Test
    fun `context 1024 can run on GPU while 4096 falls back to CPU on the same phone`() {
        val between = (estimate(ComputeBackend.GPU, 1_024) + estimate(ComputeBackend.GPU, 4_096)) / 2
        val phone = device(threshold + between)

        val short = resolver.resolveBackend(artifact(1_024), phone, backend = ComputeBackend.AUTO)
        assertEquals(ComputeBackend.GPU, short.backend)
        assertEquals(1_024, short.result.contextWindowTokens)
        assertEquals(estimate(ComputeBackend.GPU, 1_024), short.result.estimatedRamBytes)

        val long = resolver.resolveBackend(artifact(4_096), phone, backend = ComputeBackend.AUTO)
        assertEquals(ComputeBackend.CPU, long.backend)
        assertEquals(4_096, long.result.contextWindowTokens)
        assertEquals(estimate(ComputeBackend.CPU, 4_096), long.result.estimatedRamBytes)
    }

    @Test
    fun `devices that cannot attempt GPU never quote GPU for auto`() {
        val roomy = device(available = 7 * gib, gpuAttemptable = false)
        val admission = resolver.resolveBackend(artifact(4_096), roomy, backend = ComputeBackend.AUTO)

        assertEquals(ComputeBackend.CPU, admission.backend)
        assertEquals(estimate(ComputeBackend.CPU, 4_096), admission.result.estimatedRamBytes)
        assertFalse(admission.result.reason.contains("GPU"))
    }

    @Test
    fun `explicit CPU and default resolve quote the CPU estimate`() {
        val roomy = device(available = 7 * gib)
        val cpu = resolver.resolve(artifact(4_096), roomy)
        assertEquals(estimate(ComputeBackend.CPU, 4_096), cpu.estimatedRamBytes)
        assertEquals(cpu, resolver.resolve(artifact(4_096), roomy, backend = ComputeBackend.CPU))
    }

    private fun estimate(backend: ComputeBackend, context: Int): Long =
        requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact(context), backend)).estimatedRamBytes

    private fun artifact(context: Int) = ModelArtifact(
        "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        "https://example.invalid/model.litertlm",
        weights,
        format = ModelFormat.LITERT_LM,
        contextWindowTokens = context,
    )

    private fun device(
        available: Long,
        total: Long = 8 * gib,
        gpuAttemptable: Boolean = true,
    ) = DeviceProfile(
        totalRamBytes = total,
        availableRamBytes = available,
        availableStorageBytes = 20 * gib,
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
        lowMemoryThresholdBytes = threshold,
        gpuAttemptable = gpuAttemptable,
    )
}
