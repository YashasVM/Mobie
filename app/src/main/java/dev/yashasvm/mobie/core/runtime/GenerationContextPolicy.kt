package dev.yashasvm.mobie.core.runtime

/**
 * Keeps a generation request away from the native KV-cache boundary.
 *
 * LiteRT-LM defines maxNumTokens as the combined input/output token capacity. The Kotlin API does
 * not expose a cheap tokenizer-count API before generation, so UTF-8 bytes are used as a
 * deliberately conservative upper bound for text input. This avoids handing native inference a
 * request whose restored history + prompt + requested output can obviously exceed the configured
 * context window. A fixed template reserve covers chat-template/special-token overhead; vision
 * requests reserve additional room because image embeddings also consume context on multimodal
 * models.
 */
internal object GenerationContextPolicy {
    private const val TEMPLATE_RESERVE_TOKENS = 512
    private const val VISION_RESERVE_TOKENS = 512
    private const val MIN_USEFUL_OUTPUT_TOKENS = 16

    fun maxOutputTokens(
        contextWindowTokens: Int,
        history: List<RuntimeMessage>,
        prompt: String,
        requestedMaxOutputTokens: Int,
        hasImage: Boolean,
    ): Int {
        require(contextWindowTokens > 0) { "Context window must be positive" }
        require(requestedMaxOutputTokens > 0) { "Output token limit must be positive" }

        val selectedHistory = ConversationHistoryPolicy.select(history, contextWindowTokens)
        val textInputUpperBound = selectedHistory.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } +
            prompt.toByteArray(Charsets.UTF_8).size.toLong()
        val fixedReserve = TEMPLATE_RESERVE_TOKENS + if (hasImage) VISION_RESERVE_TOKENS else 0
        val availableForOutput = contextWindowTokens.toLong() - fixedReserve - textInputUpperBound

        if (availableForOutput < MIN_USEFUL_OUTPUT_TOKENS) {
            throw IllegalStateException(
                "This conversation is too close to the model's ${contextWindowTokens}-token context limit. " +
                    "Start a new chat or shorten the prompt before generating again.",
            )
        }

        return minOf(requestedMaxOutputTokens.toLong(), availableForOutput).toInt()
    }
}
