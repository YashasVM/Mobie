package dev.yashasvm.mobie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactExecutionTargetTest {
    @Test
    fun `desktop and vendor specific LiteRT artifacts are not treated as generic Android candidates`() {
        val targeted = listOf(
            "horizon-edge-e4b_intel_LNL.litertlm",
            "horizon-edge-e4b-web.litertlm",
            "model_webgpu.litertlm",
            "model-windows.litertlm",
            "model-linux.litertlm",
            "model-macos.litertlm",
            "model-ios.litertlm",
            "model-metal.litertlm",
            "Qwen3-0.6B.mediatek.mt6993.litertlm",
            "model-adreno.litertlm",
            "model-qnn.litertlm",
        )

        targeted.forEach { fileName ->
            assertEquals(
                fileName,
                ArtifactExecutionTarget.HARDWARE_SPECIFIC,
                inferArtifactExecutionTarget(fileName),
            )
        }
    }

    @Test
    fun `generic backend hints remain eligible Android candidates`() {
        val generic = listOf(
            "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "llama3_2_1b_mixed_int4_gpu.litertlm",
            "model-opencl.litertlm",
        )

        generic.forEach { fileName ->
            assertEquals(
                fileName,
                ArtifactExecutionTarget.GENERIC,
                inferArtifactExecutionTarget(fileName),
            )
        }
    }
}
