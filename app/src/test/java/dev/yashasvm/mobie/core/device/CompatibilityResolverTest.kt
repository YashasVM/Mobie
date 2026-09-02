package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityResolverTest {
    private val resolver = CompatibilityResolver()
    private val gib = 1024L * 1024L * 1024L
    private val mib = 1024L * 1024L
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
    fun `artifact context metadata scales KV estimate`() {
        val result = resolver.resolve(artifact(size = gib, name = "model_ekv2048.litertlm"), device)
        assertEquals(2_048, result.contextWindowTokens)
        assertEquals(128 * mib, result.kvCacheBytes)
    }

    @Test
    fun `compact 64k context marker prevents unsafe 4k fallback`() {
        val result = resolver.resolve(artifact(size = gib, name = "MiniCPM5-1B-c64k.litertlm"), device)
        assertEquals(65_536, result.contextWindowTokens)
        assertEquals(4 * gib, result.kvCacheBytes)
        assertEquals(Compatibility.WARNING, result.status)
    }

    @Test
    fun `device selector prefers artifact that safely fits current device`() {
        val model = AiModel(
            id = "example/model",
            title = "Example",
            author = "example",
            description = "",
            artifacts = listOf(
                artifact(size = gib, name = "model-c64k.litertlm"),
                artifact(size = 2 * gib, name = "model-ekv2048.litertlm"),
            ),
        )
        val selected = resolver.selectBestArtifact(model, device.copy(availableRamBytes = 4 * gib))
        assertEquals("model-ekv2048.litertlm", selected?.fileName)
    }

    @Test
    fun `device selector prefers measurable warning over unknown-size warning`() {
        val model = AiModel(
            id = "example/model",
            title = "Example",
            author = "example",
            description = "",
            artifacts = listOf(
                artifact(size = 0, name = "unknown-size.litertlm"),
                artifact(size = gib, name = "known-size.litertlm"),
            ),
        )
        val pressured = device.copy(isLowMemory = true)

        assertEquals("known-size.litertlm", resolver.selectBestArtifact(model, pressured)?.fileName)
    }

    @Test
    fun `device selector does not recommend artifacts that cannot fit storage`() {
        val model = AiModel(
            id = "example/model",
            title = "Example",
            author = "example",
            description = "",
            artifacts = listOf(artifact(size = 2 * gib)),
        )
        assertNull(resolver.selectBestArtifact(model, device.copy(availableStorageBytes = gib)))
    }

    @Test
    fun `device selector ignores hardware specific artifacts`() {
        val model = AiModel(
            id = "example/model",
            title = "Example",
            author = "example",
            description = "",
            artifacts = listOf(
                artifact(size = gib / 2, name = "model.mediatek.mt6993.litertlm"),
                artifact(size = gib, name = "model-ekv2048.litertlm"),
            ),
        )
        assertEquals("model-ekv2048.litertlm", resolver.selectBestArtifact(model, device)?.fileName)
    }

    @Test
    fun `direct compatibility rejects unsupported hardware specific artifacts`() {
        val result = resolver.resolve(artifact(size = gib / 2, name = "model.qualcomm.npu.litertlm"), device)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertTrue(result.reason.contains("device-specific acceleration"))
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
    fun `severe thermal pressure warns before recommending a local model load`() {
        val hot = device.copy(thermalStatus = 3)
        val result = resolver.resolve(artifact(size = gib), hot)
        assertEquals(Compatibility.WARNING, result.status)
        assertTrue(result.reason.contains("severe thermal pressure"))
    }

    @Test
    fun `android low memory threshold is reserved from recommendation headroom`() {
        val thresholdConstrained = device.copy(availableRamBytes = 3 * gib, lowMemoryThresholdBytes = 2 * gib)
        assertEquals(Compatibility.WARNING, resolver.resolve(artifact(size = gib), thresholdConstrained).status)
    }

    @Test
    fun `low ram devices use stricter total ram ceiling`() {
        val lowRam = device.copy(totalRamBytes = 4 * gib, availableRamBytes = 4 * gib, isLowRamDevice = true)
        val result = resolver.resolve(artifact(size = 2 * gib), lowRam)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertTrue(result.reason.contains("low-RAM device"))
    }

    @Test
    fun `low ram devices reserve more current memory headroom`() {
        val lowRam = device.copy(availableRamBytes = 5 * gib / 2, isLowRamDevice = true)
        val result = resolver.resolve(artifact(size = gib), lowRam)
        assertEquals(Compatibility.WARNING, result.status)
        assertTrue(result.reason.contains("extra Android memory headroom"))
    }

    @Test
    fun `model larger than safe RAM is rejected`() {
        assertEquals(Compatibility.INCOMPATIBLE, resolver.resolve(artifact(size = 7 * gib), device).status)
    }

    @Test
    fun `unknown artifact requests conversion`() {
        assertEquals(Compatibility.CONVERSION_REQUIRED, resolver.resolve(null, device).status)
    }

    @Test
    fun `unknown artifact size requires warning`() {
        assertEquals(Compatibility.WARNING, resolver.resolve(artifact(size = 0), device).status)
    }

    @Test
    fun `non arm64 devices are rejected`() {
        assertEquals(Compatibility.INCOMPATIBLE, resolver.resolve(artifact(size = gib), device.copy(supportedAbis = listOf("x86"))).status)
    }

    private fun artifact(size: Long, name: String = "model.litertlm") = ModelArtifact(
        fileName = name,
        downloadUrl = "https://example.invalid/$name",
        sizeBytes = size,
        format = ModelFormat.LITERT_LM,
    )
}