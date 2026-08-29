package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityResolverTest {
    private val resolver = CompatibilityResolver()
    private val gib = 1024L * 1024L * 1024L
    private val device = DeviceProfile(
        totalRamBytes = 8 * gib,
        availableRamBytes = 6 * gib,
        availableStorageBytes = 20 * gib,
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
    )

    @Test
    fun `small LiteRT LM model is compatible`() {
        val result = resolver.resolve(artifact(size = 2 * gib), device)
        assertEquals(Compatibility.COMPATIBLE, result.status)
        assertEquals(2 * gib, result.modelWeightsBytes)
        assertTrue(result.runtimeOverheadBytes > 0)
        assertTrue(result.kvCacheBytes > 0)
        assertEquals(4_096, result.contextWindowTokens)
        assertTrue(result.requiredStorageBytes > result.modelWeightsBytes)
    }

    @Test
    fun `model that can fit total ram but not current ram warns`() {
        val constrained = device.copy(availableRamBytes = 2 * gib)
        assertEquals(Compatibility.WARNING, resolver.resolve(artifact(size = gib), constrained).status)
    }

    @Test
    fun `model larger than safe RAM is rejected`() {
        val result = resolver.resolve(artifact(size = 7 * gib), device)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
    }

    @Test
    fun `unknown artifact requests conversion`() {
        val result = resolver.resolve(null, device)
        assertEquals(Compatibility.CONVERSION_REQUIRED, result.status)
    }

    @Test
    fun `unknown artifact size requires warning`() {
        val result = resolver.resolve(artifact(size = 0), device)
        assertEquals(Compatibility.WARNING, result.status)
    }

    @Test
    fun `non arm64 devices are rejected`() {
        val result = resolver.resolve(artifact(size = gib), device.copy(supportedAbis = listOf("x86")))
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
    }

    private fun artifact(size: Long) = ModelArtifact(
        fileName = "model.litertlm",
        downloadUrl = "https://example.invalid/model.litertlm",
        sizeBytes = size,
        format = ModelFormat.LITERT_LM,
    )
}
