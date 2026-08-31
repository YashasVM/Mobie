package dev.yashasvm.mobie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelTest {
    @Test
    fun `recommended GGUF prefers mobile quality over smallest file`() {
        val smallest = artifact("model-q2.gguf", 1_000, "Q2_K")
        val recommended = artifact("model-q4-k-m.gguf", 2_000, "Q4_K_M")
        assertEquals(recommended, modelWith(smallest, recommended).bestArtifact)
    }

    @Test
    fun `published LiteRT artifact is preferred for mobile`() {
        val gguf = artifact("model-q4-k-m.gguf", 2_000, "Q4_K_M")
        val liteRt = ModelArtifact("model.litertlm", "https://example.invalid/model.litertlm", 2_500, format = ModelFormat.LITERT_LM)
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
    fun `LiteRT context naming is recognized`() {
        assertEquals(1_280, inferArtifactContextWindow("qwen3_0.6b_q4_block32_ekv1280.litertlm"))
        assertEquals(2_048, inferArtifactContextWindow("qwen3_5_q8_ekv2048.litertlm"))
        assertEquals(4_096, inferArtifactContextWindow("model_ctx4096.litertlm"))
        assertEquals(1_024, inferArtifactContextWindow("MiniCPM5-1B-qualcomm-sm8750-c1024.litertlm"))
        assertEquals(32_768, inferArtifactContextWindow("MiniCPM5-1B-c32k.litertlm"))
        assertEquals(65_536, inferArtifactContextWindow("MiniCPM5-1B-c64k.litertlm"))
        assertNull(inferArtifactContextWindow("model.litertlm"))
    }

    @Test
    fun `LiteRT INT4 is preferred over INT8 before file size`() {
        val int8 = liteRtArtifact("model_int8.litertlm", 300, "INT8")
        val int4 = liteRtArtifact("model_mixed_int4.litertlm", 400, "INT4")
        assertEquals(int4, modelWith(int8, int4).bestArtifact)
    }

    @Test
    fun `hardware targeted LiteRT artifact is not selected by generic runtime`() {
        val npu = liteRtArtifact("Qwen3-0.6B.mediatek.mt6993.litertlm", 250, "INT4")
        val generic = liteRtArtifact("Qwen3-0.6B.litertlm", 300, "INT8")
        assertEquals(ArtifactExecutionTarget.HARDWARE_SPECIFIC, npu.executionTarget)
        assertEquals(ArtifactExecutionTarget.GENERIC, generic.executionTarget)
        assertEquals(generic, modelWith(npu, generic).bestArtifact)
    }

    @Test
    fun `hardware only LiteRT model has no runnable generic artifact`() {
        val npu = liteRtArtifact("model.qualcomm.sm8750.npu.litertlm", 250, "INT4")
        assertEquals(ArtifactExecutionTarget.HARDWARE_SPECIFIC, npu.executionTarget)
        assertNull(modelWith(npu).bestArtifact)
    }

    private fun modelWith(vararg artifacts: ModelArtifact) = AiModel("example/model", "model", "example", "test", artifacts = artifacts.toList())

    private fun artifact(name: String, size: Long, quantization: String) = ModelArtifact(name, "https://example.invalid/$name", size, format = ModelFormat.GGUF, quantization = quantization)

    private fun liteRtArtifact(name: String, size: Long, quantization: String) = ModelArtifact(name, "https://example.invalid/$name", size, format = ModelFormat.LITERT_LM, quantization = quantization)
}
