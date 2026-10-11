package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationContextPolicyTest {
    @Test
    fun `replay budgets leave a prompt and short output after fixed reserves`() {
        assertEquals(368L, GenerationContextPolicy.replayTextBudget(1024, hasImage = false))
        assertEquals(624L, GenerationContextPolicy.replayTextBudget(1280, hasImage = false))
        assertEquals(1392L, GenerationContextPolicy.replayTextBudget(2048, hasImage = false))
        assertEquals(3440L, GenerationContextPolicy.replayTextBudget(4096, hasImage = false))
        assertEquals(0L, GenerationContextPolicy.replayTextBudget(1024, hasImage = true))
        assertEquals(112L, GenerationContextPolicy.replayTextBudget(1280, hasImage = true))
        assertEquals(880L, GenerationContextPolicy.replayTextBudget(2048, hasImage = true))
        assertEquals(2928L, GenerationContextPolicy.replayTextBudget(4096, hasImage = true))
    }
    @Test
    fun `cached image consumes its measured tokens without a second image reserve`() {
        val output = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 2_048,
            history = emptyList(),
            prompt = "Hi",
            requestedMaxOutputTokens = 128,
            hasImage = false,
            historyHasImage = true,
            cachedTokenCount = 1_100,
        )
        assertEquals(128, output)
    }

    @Test
    fun `small live caches reserve enough room for continuation`() {
        assertTrue(GenerationContextPolicy.liveCacheBudget(1_024) < 600)
        assertEquals(3_072L, GenerationContextPolicy.liveCacheBudget(4_096))
        assertEquals(
            16,
            GenerationContextPolicy.maxOutputTokens(
                contextWindowTokens = 1_024,
                history = emptyList(),
                prompt = "p".repeat(128),
                requestedMaxOutputTokens = 16,
                hasImage = false,
                cachedTokenCount = GenerationContextPolicy.liveCacheBudget(1_024).toInt(),
            ),
        )
    }
    @Test
    fun `measured native history preserves output room for multilingual text`() {
        val history = listOf(
            RuntimeMessage(true, "你好".repeat(300)),
            RuntimeMessage(false, "回答".repeat(300)),
        )

        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = history,
            prompt = "Continue",
            requestedMaxOutputTokens = 1024,
            hasImage = false,
            cachedTokenCount = 1200,
        )

        assertEquals(1024, result)
    }

    @Test
    fun `native occupancy budgets reasoning absent from the visible transcript`() {
        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = listOf(RuntimeMessage(true, "Question"), RuntimeMessage(false, "Answer")),
            prompt = "Next",
            requestedMaxOutputTokens = 1024,
            hasImage = false,
            cachedTokenCount = 3400,
        )

        assertEquals(180, result)
    }

    @Test(expected = IllegalStateException::class)
    fun `pending history is fully budgeted before lazy prefill even beyond replay caps`() {
        GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = listOf(RuntimeMessage(true, "u".repeat(2000)), RuntimeMessage(false, "a".repeat(2000))),
            prompt = "Next",
            requestedMaxOutputTokens = 1024,
            hasImage = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an invalid native count`() {
        GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = "Hello",
            requestedMaxOutputTokens = 1024,
            hasImage = false,
            cachedTokenCount = -1,
        )
    }

    @Test
    fun `keeps requested output when context has headroom`() {
        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = "Hello",
            requestedMaxOutputTokens = 1_024,
            hasImage = false,
        )

        assertEquals(1_024, result)
    }

    @Test
    fun `larger response ceiling is still clamped by a small model context`() {
        val result = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 1_280,
            history = emptyList(),
            prompt = "Hello",
            requestedMaxOutputTokens = 1_024,
            hasImage = false,
        )

        assertEquals(763, result)
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

    @Test
    fun `restored history image reserves the same context as a new image`() {
        val prompt = "p".repeat(2800)
        val newImageBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = true,
        )
        val restoredImageBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = false,
            historyHasImage = true,
        )

        assertEquals(newImageBudget, restoredImageBudget)
    }

    @Test
    fun `new and restored images do not double reserve the single vision slot`() {
        val prompt = "p".repeat(2800)
        val oneImageBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = true,
        )
        val bothFlagsBudget = GenerationContextPolicy.maxOutputTokens(
            contextWindowTokens = 4096,
            history = emptyList(),
            prompt = prompt,
            requestedMaxOutputTokens = 512,
            hasImage = true,
            historyHasImage = true,
        )

        assertEquals(oneImageBudget, bothFlagsBudget)
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
