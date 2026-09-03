package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationContextPolicyTest {
    @Test
    fun `keeps requested output when context has headroom`() {
        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = "Hello",
            requestedMaxOutputTokens = 256,
            hasImage = false,
        )

        assertEquals(256, result)
    }

    @Test
    fun `clamps output before combined input and output cross context limit`() {
        val history = listOf(
            RuntimeMessage(fromUser = true, text = "u".repeat(1400)),
            RuntimeMessage(fromUser = false, text = "a".repeat(1400)),
        )

        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = history,
            prompt = "p".repeat(600),
            requestedMaxOutputTokens = 512,
            hasImage = false,
        )

        assertEquals(184, result)
    }

    @Test
    fun `vision request reserves additional context`() {
        val prompt = "p".repeat(2800)
        val textBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = false,
        )
        val visionBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = true,
        )

        assertEquals(512, textBudget)
        assertTrue(visionBudget < textBudget)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects prompt when no useful output space remains`() {
        GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = "x".repeat(3580),
            requestedMaxOutputTokens = 256,
            hasImage = false,
        )
    }
}
