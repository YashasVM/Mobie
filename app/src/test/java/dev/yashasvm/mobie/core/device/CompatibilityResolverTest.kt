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
        lowMemoryThresholdBytes = gib / 2,
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
    fun `model that can fit total ram but not safe current ram warns`() {
        val constrained = device.copy(availableRamBytes = 2 * gib)
        assertEquals(Compatibility.WARNING, resolver.resolve(artifact(size = gib), constrained).status)
    }

    @Test
    fun `android low memory state warns even when raw available ram looks sufficient`() {
        val pressured = device.copy(availableRamBytes = 6 * gib, isLowMemory = true)
        val result = resolver.resolve(artifact(size = gib), pressured)
        assertEquals(Compatibility.WARNING, result.status)
        assertTrue(result.reason.contains("active memory pressure"))
    }

    @Test
    fun `android low memory threshold is reserved from recommendation headroom`() {
        val thresholdConstrained = device.copy(
            availableRamBytes = 3 * gib,
            lowMemoryThresholdBytes = 2 * gib,
        )
        val result = resolver.resolve(artifact(size = gib), thresholdConstrained)
        assertEquals(Compatibility.WARNING, result.status)
    }

    @Test
    fun `low ram devices use stricter total ram ceiling`() {
        val lowRam = device.copy(
            totalRamBytes = 4 * gib,
            availableRamBytes = 4 * gib,
            isLowRamDevice = true,
        )
        val result = resolver.resolve(artifact(size = 2 * gib), lowRam)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertTrue(result.reason.contains("low-RAM device"))
    }

    @Test
    fun `low ram devices reserve more current memory headroom`() {
        val lowRam = device.copy(
            availableRamBytes = 3 * gib,
            isLowRamDevice = true,
        )
        val result = resolver.resolve(artifact(size = gib), lowRam)
        assertEquals(Compatibility.WARNING, result.status)
        assertTrue(result.reason.contains("extra Android memory headroom"))
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
