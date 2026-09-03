package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityStorageHeadroomTest {
    private val resolver = CompatibilityResolver()
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib

    @Test
    fun `storage estimate includes first-load optimized cache headroom`() {
        val weights = 2 * gib
        val required = resolver.requiredStorageBytes(weights)

        assertEquals(weights + weights / 20 + weights * 3 / 10, required)
        assertTrue(required > weights + weights / 20)
    }

    @Test
    fun `model is rejected when weights fit but runtime cache does not`() {
        val weights = 2 * gib
        val required = resolver.requiredStorageBytes(weights)
        val result = resolver.resolve(
            ModelArtifact(
                fileName = "model.litertlm",
                downloadUrl = "https://example.invalid/model.litertlm",
                sizeBytes = weights,
                format = ModelFormat.LITERT_LM,
            ),
            DeviceProfile(
                totalRamBytes = 12 * gib,
                availableRamBytes = 10 * gib,
                availableStorageBytes = required - 1,
                supportedAbis = listOf("arm64-v8a"),
                sdkInt = 35,
                lowMemoryThresholdBytes = gib / 2,
            ),
        )

        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertEquals(required, result.requiredStorageBytes)
        assertTrue(result.reason.contains("optimized cache"))
    }

    @Test
    fun `small models still reserve a minimum optimized cache allowance`() {
        val weights = 256 * mib
        val required = resolver.requiredStorageBytes(weights)

        assertEquals(weights + 64 * mib + 256 * mib, required)
    }
}
