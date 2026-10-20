package dev.yashasvm.mobie.core.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are the exact header bytes (everything before the first section) of published
 * litert-community artifacts, fetched with an HTTP range request. They contain no weights.
 */
class LiteRtArtifactHeaderTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/litertlm-headers/$name")) { "Missing fixture $name" }.use { it.readBytes() }

    private fun header(name: String) = LiteRtArtifactHeader.parse(fixture(name))

    @Test
    fun `Gemma 4 E2B generic build is a portable vision LLM with an MTP drafter`() {
        val header = header("gemma-4-E2B-it.hdr")
        assertEquals(1, header.majorVersion)
        assertTrue(header.isLlm)
        assertTrue(header.hasVisionEncoder)
        assertTrue(header.hasSpeculativeDrafter)
        assertEquals(44_325_712L, header.speculativeDrafterBytes)
        assertNull(header.acceleratorRequirement)
        assertTrue(header.runsOnCpu)
    }

    @Test
    fun `Gemma 4 E2B gpu file is a GPU-only artisan build without drafter or vision`() {
        val header = header("gemma-4-E2B-it-gpu.hdr")
        assertTrue(header.isLlm)
        assertFalse(header.hasSpeculativeDrafter)
        assertFalse(header.hasVisionEncoder)
        assertEquals("GPU-only artisan build", header.acceleratorRequirement)
        assertFalse(header.runsOnCpu)
    }

    @Test
    fun `a gpu name hint alone does not make a portable build hardware specific`() {
        val header = header("LFM2.5-1.2B-Instruct_int4_gpu.hdr")
        assertTrue(header.isLlm)
        assertNull(header.acceleratorRequirement)
        assertTrue(header.runsOnCpu)
    }

    @Test
    fun `plain text LLMs have no drafter`() {
        val header = header("Qwen3-0.6B.hdr")
        assertTrue(header.isLlm)
        assertFalse(header.hasSpeculativeDrafter)
        assertEquals(0L, header.speculativeDrafterBytes)
        assertNull(header.acceleratorRequirement)
    }

    @Test
    fun `older containers with upper-case section types are still recognized`() {
        val header = header("Qwen2.5-1.5B-Instruct_f32_v1_3.hdr")
        assertEquals(3, header.minorVersion)
        assertTrue(header.isLlm)
    }

    @Test
    fun `embedding containers are not chat models`() {
        val header = header("embeddinggemma-2-text-270m.hdr")
        assertFalse(header.isLlm)
        assertFalse(header.hasSpeculativeDrafter)
    }

    @Test
    fun `NPU auxiliary graphs make a build hardware specific`() {
        assertEquals("includes an NPU graph", header("FastVLM-0.5B.qualcomm.sm8750.hdr").acceleratorRequirement)
        val portable = header("FastVLM-0.5B.hdr")
        assertNull(portable.acceleratorRequirement)
        assertTrue(portable.hasVisionEncoder)
    }

    @Test
    fun `preamble yields the header end offset`() {
        assertEquals(1_872, LiteRtArtifactHeader.headerEndOffset(fixture("gemma-4-E2B-it.hdr").copyOf(32)))
        assertEquals(704, LiteRtArtifactHeader.headerEndOffset(fixture("gemma-4-E2B-it-gpu.hdr").copyOf(32)))
    }

    @Test
    fun `rejects non LiteRT-LM files, other major versions, truncation and huge headers`() {
        val valid = fixture("Qwen3-0.6B.hdr")
        assertThrows(IllegalArgumentException::class.java) { LiteRtArtifactHeader.parse("GGUF".toByteArray() + valid) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtArtifactHeader.parse(valid.copyOf(valid.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtArtifactHeader.headerEndOffset(valid.copyOf(31)) }
        val otherMajor = valid.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(8, 2) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtArtifactHeader.parse(otherMajor) }
        val huge = valid.copyOf().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(24, LiteRtArtifactHeader.MAX_HEADER_BYTES + 1L)
        }
        assertThrows(IllegalArgumentException::class.java) { LiteRtArtifactHeader.headerEndOffset(huge.copyOf(32)) }
    }

    @Test
    fun `corrupted headers fail with IllegalArgumentException and never read out of bounds`() {
        val random = Random(20261020)
        listOf("gemma-4-E2B-it.hdr", "gemma-4-E2B-it-gpu.hdr", "Qwen3-0.6B.hdr", "embeddinggemma-2-text-270m.hdr").forEach { name ->
            val original = fixture(name)
            repeat(3_000) {
                val mutated = original.copyOf()
                repeat(1 + random.nextInt(6)) {
                    // Keep the preamble intact so mutations reach the flatbuffer reader.
                    val at = 32 + random.nextInt(mutated.size - 32)
                    mutated[at] = random.nextInt(256).toByte()
                }
                try {
                    LiteRtArtifactHeader.parse(mutated)
                } catch (expected: IllegalArgumentException) {
                    // A malformed header is refused; any other exception type fails the test.
                }
            }
        }
    }
}
