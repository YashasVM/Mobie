package dev.yashasvm.mobie.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArtifactContextMetadataTest {
    @Test
    fun `parses artifact specific context from LiteRT model card table`() {
        val card = """
            | File | Quantization | Context | Size |
            | --- | --- | --- | --- |
            | `Qwen3-0.6B.litertlm` | dynamic INT8 weights, float KV | 4096 | 586 MB |
            | `qwen3_0_6b_mixed_int4.litertlm` | mixed INT4 | 2048 | 474 MiB |
            | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | dynamic INT4 | 4K | 328 MB |
        """.trimIndent()

        val contexts = parseArtifactContextWindows(card)

        assertEquals(4096, contexts["Qwen3-0.6B.litertlm"])
        assertEquals(2048, contexts["qwen3_0_6b_mixed_int4.litertlm"])
        assertEquals(4096, contexts["Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"])
    }

    @Test
    fun `ignores unrelated tables and implausible context values`() {
        val card = """
            | File | Size |
            | --- | --- |
            | `ignored.litertlm` | 400 MB |

            | Artifact | Context window |
            | --- | --- |
            | `valid.litertlm` | 8K tokens |
            | `invalid.litertlm` | 64 tokens |
        """.trimIndent()

        val contexts = parseArtifactContextWindows(card)

        assertEquals(8192, contexts["valid.litertlm"])
        assertFalse(contexts.containsKey("ignored.litertlm"))
        assertFalse(contexts.containsKey("invalid.litertlm"))
    }

    @Test
    fun `does not leak context columns into a later table`() {
        val card = """
            | Artifact | Context window | Notes |
            | --- | --- | --- |
            | `valid.litertlm` | 4K | Android package |
            | Model | Download size | Notes |
            | --- | --- | --- |
            | `not-context.litertlm` | 768 MB | benchmark fixture |
        """.trimIndent()

        val contexts = parseArtifactContextWindows(card)

        assertEquals(4096, contexts["valid.litertlm"])
        assertFalse(contexts.containsKey("not-context.litertlm"))
    }

    @Test
    fun `resets context schema across prose boundaries`() {
        val card = """
            | File | Context |
            | --- | --- |
            | `valid.litertlm` | 2048 |

            Benchmark results
            | Package | Memory |
            | --- | --- |
            | `benchmark.litertlm` | 512 MB |
        """.trimIndent()

        val contexts = parseArtifactContextWindows(card)

        assertEquals(2048, contexts["valid.litertlm"])
        assertFalse(contexts.containsKey("benchmark.litertlm"))
    }
}
