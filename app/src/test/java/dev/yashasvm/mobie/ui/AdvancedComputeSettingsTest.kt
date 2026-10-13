package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedComputeSettingsTest {
    private val supported = ComputeCapabilities(4_096, true, true, true)

    @Test fun `every option survives encode decode with the existing typed backend and thread keys`() {
        val options = ComputeOptions(ComputeBackend.GPU, 4, contextTokens = 2_048,
            maxOutputTokens = 128, thinkingEnabled = false, thinkingTokenBudget = 64,
            activationType = ActivationType.FP16, speculativeDecoding = true, prefillPrefaceOnInit = true)
        val values = AdvancedComputeSettingsCodec.encode(options) + mapOf("selected_backend" to "GPU", "cpu_threads" to "4")
        assertEquals(options, AdvancedComputeSettingsCodec.decode(values, 8))
        assertEquals(options, AdvancedComputePolicy.forArtifact(options, 8, supported))
    }

    @Test fun `missing or malformed preferences use recommended values`() {
        assertEquals(ComputeOptions(), AdvancedComputeSettingsCodec.decode(emptyMap(), 4))
        val invalid = mapOf("selected_backend" to "NPU", "cpu_threads" to "not a number", "context_tokens" to "99",
            "max_output_tokens" to "overflow", "thinking_enabled" to "yes", "thinking_budget" to "unknown",
            "activation_type" to "INT8", "speculative_decoding" to "maybe", "prefill_preface" to "invalid")
        assertEquals(ComputeOptions(), AdvancedComputeSettingsCodec.decode(invalid, 4))
    }

    @Test fun `persisted bounds are clamped and auto sentinels remain auto`() {
        val options = AdvancedComputeSettingsCodec.decode(mapOf("cpu_threads" to "99", "max_output_tokens" to "9999",
            "context_tokens" to "9999", "thinking_budget" to "-9"), 4)
        assertEquals(4, options.cpuThreads)
        assertEquals(1_024, options.maxOutputTokens)
        assertNull(options.contextTokens)
        assertEquals(0, options.thinkingTokenBudget)
        assertEquals(1, AdvancedComputeSettingsCodec.decode(mapOf("cpu_threads" to "-8", "max_output_tokens" to "-1"), 1).maxOutputTokens)
        assertNull(AdvancedComputeSettingsCodec.decode(mapOf("cpu_threads" to "0"), 1).cpuThreads)
    }

    @Test fun `artifact mapping suppresses unknown or unsupported options and honors bounded choices`() {
        val options = ComputeOptions(cpuThreads = 8, contextTokens = 4_096, thinkingEnabled = true,
            thinkingTokenBudget = 256, speculativeDecoding = true)
        val unknown = AdvancedComputePolicy.forArtifact(options, 1, null)
        assertEquals(1, unknown.cpuThreads)
        assertNull(unknown.contextTokens)
        assertNull(unknown.thinkingEnabled)
        assertNull(unknown.thinkingTokenBudget)
        assertNull(unknown.speculativeDecoding)
        val dynamic = ComputeCapabilities(2_048, true, false, false)
        assertNull(AdvancedComputePolicy.forArtifact(options, 4, dynamic).contextTokens)
        assertEquals(2_048, AdvancedComputePolicy.forArtifact(options.copy(contextTokens = 2_048), 4, dynamic).contextTokens)
        val fixed = ComputeCapabilities(1_280, false, false, false)
        assertNull(AdvancedComputePolicy.forArtifact(options.copy(contextTokens = 1_024), 4, fixed).contextTokens)
        assertEquals(1_280, AdvancedComputePolicy.forArtifact(options.copy(contextTokens = 1_280), 4, fixed).contextTokens)
    }

    @Test fun `sampler sanitation preserves seed and finite safe boundaries`() {
        assertEquals(SamplerSettings(2.0, 64, 1.0, Int.MIN_VALUE),
            AdvancedComputePolicy.sampler(SamplerSettings(9.0, 200, 3.0, Int.MIN_VALUE)))
        assertEquals(SamplerSettings(0.7, 1, 0.95, 7),
            AdvancedComputePolicy.sampler(SamplerSettings(Double.NaN, -2, Double.NEGATIVE_INFINITY, 7)))
        assertEquals(SamplerSettings(0.0, 1, 0.95, 0),
            AdvancedComputePolicy.sampler(SamplerSettings(-2.0, 0, 0.0, 0)))
    }

    @Test fun `cached capability metadata survives recreation only for the exact artifact and runtime`() {
        val encoded = AdvancedComputeSettingsCodec.encodeCapabilities(supported, "repo/artifact@sha", "0.18.0")
        assertEquals(supported, AdvancedComputeSettingsCodec.decodeCapabilities(encoded.toMap(), "repo/artifact@sha", "0.18.0"))
        assertNull(AdvancedComputeSettingsCodec.decodeCapabilities(encoded, "other-artifact", "0.18.0"))
        assertNull(AdvancedComputeSettingsCodec.decodeCapabilities(encoded, "repo/artifact@sha", "0.19.0"))
        assertNull(AdvancedComputeSettingsCodec.decodeCapabilities(encoded - "capability_drafter", "repo/artifact@sha", "0.18.0"))
        assertNull(AdvancedComputeSettingsCodec.decodeCapabilities(encoded + ("capability_max_context" to "-1"), "repo/artifact@sha", "0.18.0"))
    }

    @Test fun `apply never interrupts generation loading or stopping and requires a download`() {
        for (state in listOf(RuntimeState.GENERATING, RuntimeState.LOADING, RuntimeState.STOPPING)) {
            assertFalse(AdvancedComputePolicy.canApply(state, true))
        }
        for (state in listOf(RuntimeState.READY, RuntimeState.IDLE, RuntimeState.ERROR)) {
            assertTrue(AdvancedComputePolicy.canApply(state, true))
            assertFalse(AdvancedComputePolicy.canApply(state, false))
        }
    }

    @Test fun `pending settings compare with the actual load snapshot and reset to recommended`() {
        val loaded = ComputeOptions(ComputeBackend.GPU, 4, artifactIdentity = "artifact", contextTokens = 2_048)
        val changed = loaded.copy(maxOutputTokens = 128)
        assertFalse(AdvancedComputePolicy.needsApply(loaded.copy(artifactIdentity = ""), SamplerSettings(), loaded, SamplerSettings()))
        assertTrue(AdvancedComputePolicy.needsApply(changed, SamplerSettings(), loaded, SamplerSettings()))
        assertTrue(AdvancedComputePolicy.needsApply(loaded, SamplerSettings(temperature = 0.0), loaded, SamplerSettings()))
        assertFalse(AdvancedComputePolicy.needsApply(ComputeOptions(), SamplerSettings(), null, null))
        assertTrue(AdvancedComputePolicy.needsApply(ComputeOptions(), SamplerSettings(), loaded, SamplerSettings()))
    }
}
