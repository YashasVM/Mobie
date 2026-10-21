package dev.yashasvm.mobie.core.runtime

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistorySelectionTest {
    @Test
    fun `reverse grouping preserves blank users and multiple assistant chunks`() {
        val history = listOf(
            RuntimeMessage(false, "orphan"),
            RuntimeMessage(true, " abandoned "),
            RuntimeMessage(true, " prompt ", imagePath = "/image.jpg"),
            RuntimeMessage(false, " first ", imagePath = "/ignored.jpg"),
            RuntimeMessage(true, "   "),
            RuntimeMessage(false, " second "),
        )
        val expected = listOf(
            RuntimeMessage(true, "prompt", imagePath = "/image.jpg"),
            RuntimeMessage(false, "first"), RuntimeMessage(false, "second"),
        )
        assertEquals(expected, ConversationHistoryPolicy.select(history))
        assertEquals(expected, ConversationHistoryPolicy.select(java.util.LinkedList(history)))
        assertEquals(emptyList<RuntimeMessage>(), ConversationHistoryPolicy.select(emptyList()))
        assertEquals(emptyList<RuntimeMessage>(), ConversationHistoryPolicy.select(history.take(1)))
    }

    @Test
    fun `newest interrupted and oversized turns do not erase earlier complete turn`() {
        val complete = listOf(RuntimeMessage(true, "keep"), RuntimeMessage(false, "answer"))
        val history = complete + listOf(
            RuntimeMessage(true, "large".repeat(1000)), RuntimeMessage(false, "too large"),
            RuntimeMessage(true, "stopped"), RuntimeMessage(false, "partial", interrupted = true),
            RuntimeMessage(true, "unanswered"),
        )
        assertEquals(complete, ConversationHistoryPolicy.select(history))
    }

    @Test
    fun `lazy selection matches eager reference across malformed and interrupted histories`() {
        val random = Random(71839)
        val texts = listOf("", "  ", " hello ", "answer", "🙂漢字", "x".repeat(5000), "\uD800", "\uDC00")
        repeat(2000) { case ->
            val history = List(random.nextInt(0, 160)) {
                RuntimeMessage(
                    fromUser = random.nextBoolean(),
                    text = texts.random(random),
                    interrupted = random.nextInt(5) == 0,
                    imagePath = if (random.nextInt(7) == 0) "/image.jpg" else null,
                )
            }
            val context = listOf(512, 1024, 1280, 2048, 4096, 8192, 32768, 65536).random(random)
            assertEquals("case=$case context=$context", EagerReplayReference.select(history, context),
                ConversationHistoryPolicy.select(history, context))
        }
    }

    @Test
    fun `eager and lazy paths agree at the short history boundary`() {
        for (size in listOf(63, 64, 65, 66)) {
            val complete = List(size) { RuntimeMessage(it % 2 == 0, "message-$it") }
            val interrupted = complete.mapIndexed { index, message ->
                if (index >= size - 2) message.copy(interrupted = true) else message
            }
            for (history in listOf(complete, interrupted)) for (context in listOf(1024, 4096, 32768)) {
                val expected = EagerReplayReference.select(history, context)
                assertEquals(expected, ConversationHistoryPolicy.select(history, context))
                assertEquals(expected, ConversationHistoryPolicy.select(java.util.LinkedList(history), context))
            }
        }
    }

    @Test
    fun `long transcript reads only the bounded newest suffix`() {
        var reads = 0
        val history = object : AbstractList<RuntimeMessage>() {
            override val size = 100_000
            override fun get(index: Int): RuntimeMessage {
                reads++
                return RuntimeMessage(fromUser = index % 2 == 0, text = "message-$index")
            }
        }
        val selected = ConversationHistoryPolicy.select(history)
        assertEquals(20, selected.size)
        assertEquals("message-99980", selected.first().text)
        assertEquals("message-99999", selected.last().text)
        assertTrue("Read $reads messages despite bounded replay", reads <= 22)
    }
}

// Frozen pre-optimization selector used only as a differential correctness oracle.
private object EagerReplayReference {
    const val MAX_RESTORED_MESSAGES = 20
    const val MAX_RESTORED_CHARS = 12 * 1024
    const val MAX_RESTORED_UTF8_BYTES = 3 * 1024

    private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 4_096
    private const val MIN_CONTEXT_WINDOW_TOKENS = 1_024
    private const val MAX_CONTEXT_SCALE = 8
    private const val MAX_SCALED_MESSAGES = 64

    fun select(
        history: List<RuntimeMessage>,
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<RuntimeMessage> {
        if (history.isEmpty()) return emptyList()

        val budget = replayBudget(contextWindowTokens)
        val turns = mutableListOf<MutableList<RuntimeMessage>>()
        for (message in history) {
            val text = message.text.trim()
            if (text.isEmpty()) continue

            if (message.fromUser) {
                turns += mutableListOf(
                    RuntimeMessage(
                        fromUser = true,
                        text = text,
                        interrupted = message.interrupted,
                        imagePath = message.imagePath,
                    ),
                )
            } else {
                // Ignore assistant-only prefixes; LiteRT restoration must always be user-led.
                turns.lastOrNull()?.add(
                    RuntimeMessage(
                        fromUser = false,
                        text = text,
                        interrupted = message.interrupted,
                    ),
                )
            }
        }
        if (turns.isEmpty()) return emptyList()

        val selectedNewestFirst = mutableListOf<List<RuntimeMessage>>()
        var selectedMessages = 0
        var selectedChars = 0
        var selectedUtf8Bytes = 0
        var selectedHasImage = false
        var selectedAny = false

        fun maxReplayBytes(hasImage: Boolean): Long = minOf(
            budget.maxUtf8Bytes.toLong(),
            GenerationContextPolicy.replayTextBudget(contextWindowTokens, hasImage),
        )

        for (turn in turns.asReversed()) {
            // A stopped/failed generation may leave either a user-only turn or a partial assistant
            // response. Keep both forms visible in history, but never seed LiteRT with unfinished
            // output because the next response must continue from the last fully completed turn.
            if (turn.none { !it.fromUser } || turn.any { it.interrupted }) continue

            val turnChars = turn.sumOf { it.text.length }
            val turnUtf8Bytes = turn.sumOf { it.text.utf8ByteCount() }
            val turnMessages = turn.size
            // File usability is checked later by the native adapter. Reserving for any image path
            // here prevents a usable restored image from exhausting the pending replay context.
            val turnHasImage = turn.any { it.imagePath != null }
            val turnFitsAlone =
                turnChars <= budget.maxChars &&
                    turnUtf8Bytes <= maxReplayBytes(turnHasImage) &&
                    turnMessages <= budget.maxMessages

            if (!turnFitsAlone) {
                // A single pathological latest turn should not make all prior context disappear.
                if (!selectedAny) continue
                break
            }

            if (
                selectedMessages + turnMessages > budget.maxMessages ||
                selectedChars + turnChars > budget.maxChars ||
                selectedUtf8Bytes + turnUtf8Bytes > maxReplayBytes(selectedHasImage || turnHasImage)
            ) {
                break
            }

            selectedNewestFirst += turn
            selectedMessages += turnMessages
            selectedChars += turnChars
            selectedUtf8Bytes += turnUtf8Bytes
            selectedHasImage = selectedHasImage || turnHasImage
            selectedAny = true
        }

        return selectedNewestFirst.asReversed().flatten()
    }

    private fun replayBudget(contextWindowTokens: Int): ReplayBudget {
        val safeContextTokens = contextWindowTokens.coerceAtLeast(MIN_CONTEXT_WINDOW_TOKENS)
        val maxScaledContextTokens = DEFAULT_CONTEXT_WINDOW_TOKENS * MAX_CONTEXT_SCALE
        val budgetContextTokens = safeContextTokens.coerceAtMost(maxScaledContextTokens)

        fun scaled(base: Int): Int =
            ((base.toLong() * budgetContextTokens) / DEFAULT_CONTEXT_WINDOW_TOKENS)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()

        return ReplayBudget(
            // A completed turn requires at least a user+assistant pair. Keeping this floor even at
            // 1K avoids a degenerate budget that could never restore any valid conversation turn.
            maxMessages = scaled(MAX_RESTORED_MESSAGES).coerceIn(2, MAX_SCALED_MESSAGES),
            maxChars = scaled(MAX_RESTORED_CHARS),
            maxUtf8Bytes = scaled(MAX_RESTORED_UTF8_BYTES),
        )
    }

    private data class ReplayBudget(
        val maxMessages: Int,
        val maxChars: Int,
        val maxUtf8Bytes: Int,
    )
}
