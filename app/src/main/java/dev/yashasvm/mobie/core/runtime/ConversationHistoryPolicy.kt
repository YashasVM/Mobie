package dev.yashasvm.mobie.core.runtime

/**
 * Keeps restored conversations small enough for predictable prefill and KV-cache use.
 *
 * The UI/history store keeps the full transcript. This policy only controls what is re-prefilled
 * into LiteRT-LM when a conversation is recreated. LiteRT-LM does not currently expose a cheap
 * tokenizer count before conversation creation, so character count is used as a conservative,
 * deterministic guard in addition to the message-count limit.
 */
internal object ConversationHistoryPolicy {
    const val MAX_RESTORED_MESSAGES = 20
    const val MAX_RESTORED_CHARS = 12 * 1024

    fun select(history: List<RuntimeMessage>): List<RuntimeMessage> {
        if (history.isEmpty()) return emptyList()

        val selectedNewestFirst = ArrayList<RuntimeMessage>(MAX_RESTORED_MESSAGES)
        var chars = 0
        for (message in history.asReversed()) {
            if (selectedNewestFirst.size >= MAX_RESTORED_MESSAGES) break
            val text = message.text.trim()
            if (text.isEmpty()) continue
            if (text.length > MAX_RESTORED_CHARS || chars + text.length > MAX_RESTORED_CHARS) break
            selectedNewestFirst += RuntimeMessage(message.fromUser, text)
            chars += text.length
        }

        // A count/size boundary can land between a user message and its assistant response.
        // LiteRT conversation restoration should always start from a user-led turn.
        return selectedNewestFirst.asReversed().dropWhile { !it.fromUser }
    }
}
