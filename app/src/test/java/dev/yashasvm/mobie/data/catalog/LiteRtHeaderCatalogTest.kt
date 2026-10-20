package dev.yashasvm.mobie.data.catalog

import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.LiteRtArtifactHeader
import dev.yashasvm.mobie.core.model.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses trimmed Hub API responses and the real headers of the same published files. */
class LiteRtHeaderCatalogTest {
    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing fixture $path" }.use { it.readBytes() }

    private fun hub(name: String) = String(resource("/hub/$name.json"), Charsets.UTF_8)
    private fun header(name: String) = LiteRtArtifactHeader.parse(resource("/litertlm-headers/$name"))

    private val gemmaHeaders get() = mapOf(
        "gemma-4-E2B-it.litertlm" to header("gemma-4-E2B-it.hdr"),
        "gemma-4-E2B-it-gpu.litertlm" to header("gemma-4-E2B-it-gpu.hdr"),
    )

    private val flagshipPhone = DeviceProfile(
        totalRamBytes = 12L * GIB,
        availableRamBytes = 7L * GIB,
        availableStorageBytes = 64L * GIB,
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
        gpuAttemptable = true,
    )

    @Test
    fun `without headers Gemma 4 has no task tag and is hidden as an unknown type`() {
        val model = checkNotNull(catalogModelFromHubJson(hub("gemma-4-E2B-it-litert-lm")))
        assertEquals(ModelType.UNKNOWN, model.type)
    }

    @Test
    fun `Gemma 4 probes only its generic-named files`() {
        assertEquals(
            listOf("gemma-4-E2B-it-gpu.litertlm", "gemma-4-E2B-it.litertlm"),
            catalogHeaderProbeTargets(hub("gemma-4-E2B-it-litert-lm")),
        )
    }

    @Test
    fun `headers make Gemma 4 a vision chat model and recommend the drafter build Gallery uses`() {
        val model = checkNotNull(catalogModelFromHubJson(hub("gemma-4-E2B-it-litert-lm"), gemmaHeaders))
        assertEquals(ModelType.VISION, model.type)

        val generic = model.artifacts.single { it.fileName == "gemma-4-E2B-it.litertlm" }
        assertEquals(ArtifactExecutionTarget.GENERIC, generic.executionTarget)
        assertEquals(true, generic.speculativeDrafter)
        assertNull(generic.acceleratorRequirement)

        val artisan = model.artifacts.single { it.fileName == "gemma-4-E2B-it-gpu.litertlm" }
        assertEquals(ArtifactExecutionTarget.HARDWARE_SPECIFIC, artisan.executionTarget)
        assertEquals(false, artisan.speculativeDrafter)
        assertEquals("GPU-only artisan build", artisan.acceleratorRequirement)

        assertEquals("gemma-4-E2B-it.litertlm", model.bestArtifact?.fileName)
        assertEquals("gemma-4-E2B-it.litertlm", CompatibilityResolver().selectBestArtifact(model, flagshipPhone)?.fileName)
    }

    @Test
    fun `an artisan build explains why it cannot run`() {
        val model = checkNotNull(catalogModelFromHubJson(hub("gemma-4-E2B-it-litert-lm"), gemmaHeaders))
        val artisan = model.artifacts.single { it.fileName == "gemma-4-E2B-it-gpu.litertlm" }
        val result = CompatibilityResolver().resolve(artisan, flagshipPhone)
        assertEquals(Compatibility.INCOMPATIBLE, result.status)
        assertTrue(result.reason, result.reason.contains("GPU-only artisan build"))
    }

    @Test
    fun `tagged repositories probe only generic backend hints and keep portable gpu builds`() {
        assertEquals(listOf("LFM2.5-1.2B-Instruct_int4_gpu.litertlm"), catalogHeaderProbeTargets(hub("LFM2.5-1.2B-Instruct")))
        val model = checkNotNull(
            catalogModelFromHubJson(
                hub("LFM2.5-1.2B-Instruct"),
                mapOf("LFM2.5-1.2B-Instruct_int4_gpu.litertlm" to header("LFM2.5-1.2B-Instruct_int4_gpu.hdr")),
            ),
        )
        assertEquals(ModelType.TEXT_GENERATION, model.type)
        val gpuNamed = model.artifacts.single { it.fileName == "LFM2.5-1.2B-Instruct_int4_gpu.litertlm" }
        assertEquals(ArtifactExecutionTarget.GENERIC, gpuNamed.executionTarget)
        assertEquals(false, gpuNamed.speculativeDrafter)
    }

    @Test
    fun `an embedding container never becomes a chat model`() {
        val targets = catalogHeaderProbeTargets(hub("embeddinggemma-2-text-270m-litert-lm"))
        assertEquals(listOf("embeddinggemma-2-text-270m.litertlm"), targets)
        val model = checkNotNull(
            catalogModelFromHubJson(
                hub("embeddinggemma-2-text-270m-litert-lm"),
                mapOf("embeddinggemma-2-text-270m.litertlm" to header("embeddinggemma-2-text-270m.hdr")),
            ),
        )
        assertEquals(ModelType.UNKNOWN, model.type)
    }

    @Test
    fun `probe rules skip vendor named files and non LiteRT files`() {
        assertFalse(LiteRtHeaderInspector.shouldProbe("gemma-4-E2B-it_Google_Tensor_G5.litertlm", repositoryTypeUnknown = true))
        assertFalse(LiteRtHeaderInspector.shouldProbe("gemma-4-E2B-it-web.litertlm", repositoryTypeUnknown = true))
        assertFalse(LiteRtHeaderInspector.shouldProbe("README.md", repositoryTypeUnknown = true))
        assertFalse(LiteRtHeaderInspector.shouldProbe("Qwen3-0.6B.litertlm", repositoryTypeUnknown = false))
        assertTrue(LiteRtHeaderInspector.shouldProbe("model_opencl.litertlm", repositoryTypeUnknown = false))
        assertTrue(LiteRtHeaderInspector.shouldProbe("model.litertlm", repositoryTypeUnknown = true))
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
