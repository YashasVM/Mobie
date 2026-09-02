package dev.yashasvm.mobie.core.runtime

/**
 * Keeps restored conversations small enough for predictable prefill and KV-cache use.
 *
 * The UI/history store keeps the full transcript. This policy only controls what is re-prefilled
 * into LiteRT-LM when a conversation is recreated. LiteRT-LM does not currently expose a cheap
 * tokenizer count before conversation creation, so character count is used as a conservative,
 * deterministic guard in addition to the message-count limit.
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

    fun select(history: List<RuntimeMessage>): List<RuntimeMessage> {
        if (history.isEmpty()) return emptyList()

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
        var selectedAny = false

        for (turn in turns.asReversed()) {
            // A stopped/failed generation may leave either a user-only turn or a partial assistant
            // response. Keep both forms visible in history, but never seed LiteRT with unfinished
            // output because the next response must continue from the last fully completed turn.
            if (turn.none { !it.fromUser } || turn.any { it.interrupted }) continue

            val turnChars = turn.sumOf { it.text.length }
            val turnMessages = turn.size
            val turnFitsAlone = turnChars <= MAX_RESTORED_CHARS && turnMessages <= MAX_RESTORED_MESSAGES

            if (!turnFitsAlone) {
                // A single pathological latest turn should not make all prior context disappear.
                if (!selectedAny) continue
                break
            }

            if (
                selectedMessages + turnMessages > MAX_RESTORED_MESSAGES ||
                selectedChars + turnChars > MAX_RESTORED_CHARS
            ) {
                break
            }

            selectedNewestFirst += turn
            selectedMessages += turnMessages
            selectedChars += turnChars
            selectedAny = true
        }

        return selectedNewestFirst.asReversed().flatten()
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
    ): List<RuntimeMessage> {
        val interruptedTurn = buildList {
            add(RuntimeMessage(fromUser = true, text = prompt, interrupted = true))
            if (!partialAnswer.isNullOrBlank()) {
                add(RuntimeMessage(fromUser = false, text = partialAnswer, interrupted = true))
            }
        }
        return select(committedHistory + interruptedTurn)
    }
}
