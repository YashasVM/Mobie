package dev.yashasvm.mobie.core.runtime

/**
 * Keeps restored conversations small enough for predictable prefill and KV-cache use.
 *
 * The UI/history store keeps the full transcript. This policy only controls what is re-prefilled
 * into LiteRT-LM when a conversation is recreated. LiteRT-LM does not currently expose a cheap
 * tokenizer count before conversation creation, so we bound both characters and UTF-8 bytes.
 * The default byte cap is deliberately conservative for a 4K runtime context: byte-fallback
 * tokenizers cannot require more tokens than the number of input bytes, leaving roughly 1K tokens
 * for the next prompt, output budget, and prompt-template overhead even for token-dense Unicode.
 * Runtime contexts smaller than 4K receive proportionally smaller replay budgets, while larger
 * explicitly configured contexts receive proportionally larger budgets capped to avoid turning
 * conversation restoration into an unbounded prefill/TTFT penalty.
 *
 * Selection is turn-aware: a restored history never starts with an orphan assistant message,
 * never keeps only half of an older completed turn, and never replays an interrupted turn. User-only
 * or partial-assistant turns remain visible in the persisted UI transcript, but replaying them into
 * LiteRT would make cancelled/failed output silently influence the next response. If the newest
 * complete turn alone is too large to fit, it is skipped so an oversized prompt cannot erase all
 * otherwise-restorable context after a cancellation/rebuild.
 */
internal object ConversationHistoryPolicy {
    const val MAX_RESTORED_MESSAGES = 20
    const val MAX_RESTORED_CHARS = 12 * 1024
    const val MAX_RESTORED_UTF8_BYTES = 3 * 1024

    private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 4_096
    private const val MIN_CONTEXT_WINDOW_TOKENS = 1_024
    private const val MAX_CONTEXT_SCALE = 8
    private const val MAX_SCALED_MESSAGES = 64
    private const val MAX_LIVE_UTF8_BYTES = 96 * 1024

    data class CompletedTurnReplay(
        val history: List<RuntimeMessage>,
        val nativeConversationMustRebuild: Boolean,
    )

    fun select(
        history: List<RuntimeMessage>,
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<RuntimeMessage> {
        if (history.isEmpty()) return emptyList()

        val budget = replayBudget(contextWindowTokens)
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

        for (turn in newestTurns(history)) {
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

    /** Avoid coroutine-iterator setup for ordinary short chats; scan long histories lazily. */
    private fun newestTurns(history: List<RuntimeMessage>): Iterable<List<RuntimeMessage>> {
        if (history.size <= MAX_SCALED_MESSAGES) {
            // Preserve the original eager grouping path for short chats. Lazy traversal pays off
            // only when it can avoid an older prefix; tiny inputs need no sequence state machine.
            val turns = mutableListOf<MutableList<RuntimeMessage>>()
            for (message in history) {
                val text = message.text.trim()
                if (text.isEmpty()) continue
                if (message.fromUser) {
                    turns += mutableListOf(RuntimeMessage(true, text, message.interrupted, message.imagePath))
                } else {
                    turns.lastOrNull()?.add(RuntimeMessage(false, text, message.interrupted))
                }
            }
            return turns.asReversed()
        }
        return newestLongHistoryTurns(history).asIterable()
    }

    /** Scan only the suffix needed by replay; never normalize/copy an unused older prefix. */
    private fun newestLongHistoryTurns(history: List<RuntimeMessage>): Sequence<List<RuntimeMessage>> = sequence {
        val iterator = history.listIterator(history.size)
        var reversedTurn = mutableListOf<RuntimeMessage>()
        while (iterator.hasPrevious()) {
            val message = iterator.previous()
            val text = message.text.trim()
            if (text.isEmpty()) continue
            reversedTurn += RuntimeMessage(
                fromUser = message.fromUser,
                text = text,
                interrupted = message.interrupted,
                imagePath = if (message.fromUser) message.imagePath else null,
            )
            if (message.fromUser) {
                yield(reversedTurn.asReversed())
                // Do not mutate a yielded list: the selector may retain it in the result.
                reversedTurn = mutableListOf()
            }
        }
        // Any remaining assistant-only prefix has no user turn and is not replayable.
    }

    /**
     * Commits a successful turn to the retained history and reports whether the already-live
     * native LiteRT conversation must be rebuilt before the next prompt. LiteRT keeps every turn
     * sent to a Conversation; if replay selection evicts older turns but the native Conversation is
     * left alive, later context admission would budget against less history than native inference
     * actually retains. Measured cache occupancy lets a live conversation exceed the conservative
     * replay byte cap; rebuilding near capacity keeps the next restored context bounded.
     */
    fun afterCompletedTurn(
        committedHistory: List<RuntimeMessage>,
        prompt: String,
        answer: String,
        imagePath: String? = null,
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
        cachedTokenCount: Int? = null,
    ): CompletedTurnReplay {
        val extended = committedHistory +
            RuntimeMessage(fromUser = true, text = prompt, imagePath = imagePath) +
            RuntimeMessage(fromUser = false, text = answer)
        // Replay byte budgets protect unknown tokenizer density after restoration. Applying those
        // bounds to a live cache needlessly discards context and repeats prefill. Measured native
        // occupancy includes hidden reasoning and images, so retain the cache while one quarter of
        // its context remains free. Keep independent application-memory and message-count bounds.
        val liveTokenBudget = GenerationContextPolicy.liveCacheBudget(contextWindowTokens)
        val canRetainLiveCache = cachedTokenCount != null &&
            cachedTokenCount > 0 &&
            cachedTokenCount.toLong() <= liveTokenBudget &&
            extended.size <= MAX_SCALED_MESSAGES &&
            extended.sumOf { it.text.utf8ByteCount().toLong() } <= MAX_LIVE_UTF8_BYTES
        if (canRetainLiveCache) {
            return CompletedTurnReplay(history = extended, nativeConversationMustRebuild = false)
        }
        val selected = select(extended, contextWindowTokens)
        return CompletedTurnReplay(
            history = selected,
            nativeConversationMustRebuild = selected != extended ||
                (cachedTokenCount != null && cachedTokenCount > liveTokenBudget),
        )
    }

    /**
     * Records a failed/cancelled generation for runtime recovery without accidentally committing
     * its prompt or partial assistant output as valid native context. The UI persists the visible
     * interrupted turn separately; this helper only determines the in-memory LiteRT replay state.
     */
    fun afterInterruptedTurn(
        committedHistory: List<RuntimeMessage>,
        prompt: String,
        partialAnswer: String?,
        imagePath: String? = null,
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<RuntimeMessage> {
        val interruptedTurn = buildList {
            add(RuntimeMessage(fromUser = true, text = prompt, interrupted = true, imagePath = imagePath))
            if (!partialAnswer.isNullOrBlank()) {
                add(RuntimeMessage(fromUser = false, text = partialAnswer, interrupted = true))
            }
        }
        return select(committedHistory + interruptedTurn, contextWindowTokens)
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
