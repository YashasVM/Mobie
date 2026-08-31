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

    @Test
    fun `current LiteRT quantization naming is recognized`() {
        assertEquals("Q4_BLOCK32", inferArtifactQuantization("qwen3_0.6b_q4_block32_ekv1280.litertlm"))
        assertEquals("INT4", inferArtifactQuantization("qwen3_0_6b_mixed_int4.litertlm"))
        assertEquals("INT4", inferArtifactQuantization("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"))
        assertEquals("INT8", inferArtifactQuantization("qwen3_14b_channelwise_int8_float32kv.litertlm"))
    }

    @Test
    fun `LiteRT INT4 is preferred over INT8 before file size`() {
        val int8 = liteRtArtifact("model_int8.litertlm", 300, "INT8")
        val int4 = liteRtArtifact("model_mixed_int4.litertlm", 400, "INT4")

        assertEquals(int4, modelWith(int8, int4).bestArtifact)
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

    private fun liteRtArtifact(name: String, size: Long, quantization: String) = ModelArtifact(
        fileName = name,
        downloadUrl = "https://example.invalid/$name",
        sizeBytes = size,
        format = ModelFormat.LITERT_LM,
        quantization = quantization,
    )
}
