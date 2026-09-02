package dev.yashasvm.mobie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactExecutionTargetTest {
    @Test
    fun `desktop and web LiteRT artifacts are not treated as generic Android candidates`() {
        val targeted = listOf(
            "horizon-edge-e4b_intel_LNL.litertlm",
            "horizon-edge-e4b-web.litertlm",
            "model_webgpu.litertlm",
            "model-windows.litertlm",
            "model-linux.litertlm",
            "model-macos.litertlm",
            "model-ios.litertlm",
            "model-metal.litertlm",
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
    fun `generic Android CPU LiteRT artifact remains eligible`() {
        assertEquals(
            ArtifactExecutionTarget.GENERIC,
            inferArtifactExecutionTarget("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"),
        )
    }
}
