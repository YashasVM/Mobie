package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.*
import dev.yashasvm.mobie.core.runtime.*
import org.junit.Assert.*
import org.junit.Test

class UnknownPackageContextTest {
    private val artifact = ModelArtifact("unknown.litertlm", "", 1200L * MIB, format = ModelFormat.LITERT_LM, contextWindowTokens = 4096)
    private val estimate = requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact.copy(contextWindowTokens = 2048), ComputeBackend.GPU)).estimatedRamBytes
    private val total = (estimate + 20 * MIB) * 10 / 9
    private val device = DeviceProfile(total, total, 100 * GIB, listOf("arm64-v8a"), 35)

    @Test
    fun unverifiedPackageCannotClaimCompatibilityByAssumingItsContextCanShrink() {
        val result = CompatibilityResolver().resolve(artifact, device, backend = ComputeBackend.GPU)
        assertEquals(2048, result.contextWindowTokens)
        assertEquals(Compatibility.WARNING, result.status)
        assertTrue(result.reason.contains("unknown until first load"))
        assertTrue(result.reason.contains("fixed 4096-token"))
    }

    @Test
    fun verifiedFixedPackageIsJudgedAtItsFullContextAndRefused() {
        val fixed = ComputeCapabilities(4096, false, false, false)
        val result = CompatibilityResolver().resolve(artifact, device, backend = ComputeBackend.GPU, capabilities = fixed)
        assertEquals(4096, result.contextWindowTokens)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, result.memoryTier)
    }

    @Test
    fun verifiedDynamicPackageMayUseTheSmallerContextWithoutTheUnknownLimitsWarning() {
        val dynamic = ComputeCapabilities(4096, true, false, false)
        val result = CompatibilityResolver().resolve(artifact, device, backend = ComputeBackend.GPU, capabilities = dynamic)
        assertEquals(2048, result.contextWindowTokens)
        assertEquals(Compatibility.COMPATIBLE, result.status)
        assertFalse(result.reason.contains("unknown until first load"))
    }

    private companion object { const val MIB = 1024L * 1024; const val GIB = 1024L * MIB }
}
