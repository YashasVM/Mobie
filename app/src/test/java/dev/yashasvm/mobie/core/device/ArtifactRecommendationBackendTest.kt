package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactRecommendationBackendTest {
    private val gib = 1024L * 1024L * 1024L
    private val resolver = CompatibilityResolver()
    private val device = DeviceProfile(
        totalRamBytes = 8 * gib,
        availableRamBytes = 6 * gib,
        availableStorageBytes = 20 * gib,
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
        lowMemoryThresholdBytes = gib / 2,
        gpuAttemptable = true,
    )

    @Test
    fun `recommendation ranks artifacts by the backend Auto would pick`() {
        // The small file needs less RAM on CPU, but Auto runs it on GPU, where its graphics
        // allowance makes it need more RAM than the large file's CPU fallback. The recommendation
        // must follow the Auto line the user sees, not the CPU-only estimate.
        val small = artifact(gib / 2, "small.litertlm")
        val large = artifact(2 * gib, "large.litertlm")
        assertTrue(resolver.resolve(small, device).estimatedRamBytes < resolver.resolve(large, device).estimatedRamBytes)
        val smallAuto = resolver.resolveBackend(small, device, false, ComputeBackend.AUTO)
        assertEquals(ComputeBackend.GPU, smallAuto.backend)
        assertEquals(Compatibility.COMPATIBLE, smallAuto.result.status)
        assertEquals(ComputeBackend.CPU, resolver.resolveBackend(large, device, false, ComputeBackend.AUTO).backend)
        val model = AiModel(
            id = "example/model",
            title = "Example",
            author = "example",
            description = "",
            artifacts = listOf(small, large),
        )
        assertEquals("large.litertlm", resolver.selectBestArtifact(model, device)?.fileName)
    }

    private fun artifact(size: Long, name: String) = ModelArtifact(
        fileName = name,
        downloadUrl = "https://example.invalid/$name",
        sizeBytes = size,
        format = ModelFormat.LITERT_LM,
    )
}
