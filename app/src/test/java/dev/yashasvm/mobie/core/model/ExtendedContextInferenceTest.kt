package dev.yashasvm.mobie.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedContextInferenceTest {
    @Test
    fun `very large explicit context markers remain visible to admission logic`() {
        assertEquals(262_144, inferArtifactContextWindow("model_ctx256k.litertlm"))
        assertEquals(524_288, inferArtifactContextWindow("model-context-512k.litertlm"))
        assertEquals(1_048_576, inferArtifactContextWindow("model_c1024k.litertlm"))
        assertEquals(1_048_576, inferArtifactContextWindow("model_ctx1m.litertlm"))
        assertEquals(1_048_576, inferArtifactContextWindow("model-context-1m.litertlm"))
        assertEquals(1_048_576, inferArtifactContextWindow("model_kv1m.litertlm"))
        assertEquals(1_048_576, inferArtifactContextWindow("model_c1m.litertlm"))
        assertEquals(262_144, inferArtifactContextWindow("model_kv262144.litertlm"))
    }

    @Test
    fun `large context estimate scales KV cache instead of falling back to 4k`() {
        val mib = 1024L * 1024L
        val artifact = ModelArtifact(
            fileName = "model_int4_ctx256k.litertlm",
            downloadUrl = "https://example.invalid/model.litertlm",
            sizeBytes = 500L * mib,
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
        )
        val estimate = requireNotNull(estimateLiteRtRuntimeMemory(artifact))

        assertEquals(262_144, estimate.contextWindowTokens)
        assertEquals(16L * 1024L * mib, estimate.kvCacheBytes)
    }
}
