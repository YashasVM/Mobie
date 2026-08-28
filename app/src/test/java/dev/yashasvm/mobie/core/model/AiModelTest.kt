package dev.yashasvm.mobie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelTest {
    @Test
    fun `recommended GGUF prefers mobile quality over smallest file`() {
        val smallest = artifact("model-q2.gguf", 1_000, "Q2_K")
        val recommended = artifact("model-q4-k-m.gguf", 2_000, "Q4_K_M")

        val model = modelWith(smallest, recommended)

        assertEquals(recommended, model.bestArtifact)
    }

    @Test
    fun `published LiteRT artifact is preferred for mobile`() {
        val gguf = artifact("model-q4-k-m.gguf", 2_000, "Q4_K_M")
        val liteRt = ModelArtifact(
            fileName = "model.litertlm",
            downloadUrl = "https://example.invalid/model.litertlm",
            sizeBytes = 2_500,
            format = ModelFormat.LITERT_LM,
        )

        assertEquals(liteRt, modelWith(gguf, liteRt).bestArtifact)
        assertEquals("LiteRT-LM", liteRt.runtimeLabel)
        assertEquals("llama.cpp", gguf.runtimeLabel)
    }

    private fun modelWith(vararg artifacts: ModelArtifact) = AiModel(
        id = "example/model",
        title = "model",
        author = "example",
        description = "test",
        artifacts = artifacts.toList(),
    )

    private fun artifact(name: String, size: Long, quantization: String) = ModelArtifact(
        fileName = name,
        downloadUrl = "https://example.invalid/$name",
        sizeBytes = size,
        format = ModelFormat.GGUF,
        quantization = quantization,
    )
}
