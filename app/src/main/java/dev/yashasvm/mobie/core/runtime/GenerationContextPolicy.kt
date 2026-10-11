package dev.yashasvm.mobie.core.runtime

/**
 * Keeps a generation request away from the native KV-cache boundary.
 *
 * LiteRT-LM defines maxNumTokens as the combined input/output token capacity. The Kotlin API
 * exposes the occupied KV-cache token count, but cannot tokenize an unsent prompt. UTF-8 bytes are
 * therefore a conservative upper bound for new input and lazily restored history. This avoids a
 * request whose restored history + prompt + requested output can obviously exceed the configured
 * context window. A fixed template reserve covers chat-template/special-token overhead; vision
 * requests reserve additional room because image embeddings also consume context on multimodal
 * models. A restored image needs that reserve only while its preface is pending; an image already
 * in the live cache is included in the measured occupancy.
 */
internal object GenerationContextPolicy {
    private const val TEMPLATE_RESERVE_TOKENS = 512
    private const val VISION_RESERVE_TOKENS = 512
    private const val MIN_USEFUL_OUTPUT_TOKENS = 16
    private const val NEXT_PROMPT_RESERVE_TOKENS = 128

    /** Retention must leave room for the same template and output reserves as admission. */
    fun liveCacheBudget(contextWindowTokens: Int): Long = minOf(
        contextWindowTokens.toLong() * 3 / 4,
        replayTextBudget(contextWindowTokens, hasImage = false),
    )

    /** Pending replay must admit a 128-byte next prompt and at least a short answer. */
    fun replayTextBudget(contextWindowTokens: Int, hasImage: Boolean): Long =
        (contextWindowTokens.toLong() - TEMPLATE_RESERVE_TOKENS - NEXT_PROMPT_RESERVE_TOKENS -
            MIN_USEFUL_OUTPUT_TOKENS - if (hasImage) VISION_RESERVE_TOKENS else 0).coerceAtLeast(0)

    fun maxOutputTokens(
        contextWindowTokens: Int,
        history: List<RuntimeMessage>,
        prompt: String,
        requestedMaxOutputTokens: Int,
        hasImage: Boolean,
        historyHasImage: Boolean = false,
        cachedTokenCount: Int? = null,
    ): Int {
        require(contextWindowTokens > 0) { "Context window must be positive" }
        require(requestedMaxOutputTokens > 0) { "Output token limit must be positive" }
        require(cachedTokenCount == null || cachedTokenCount >= 0) { "Cached token count must not be negative" }

        // Native occupancy includes template, reasoning, and image tokens that are absent from the
        // visible replay transcript. A freshly created conversation may still have a lazy preface;
        // callers must pass null until that history has actually entered the KV cache.
        val historyTokenBudget = cachedTokenCount?.toLong() ?: history.sumOf { it.text.utf8ByteCount().toLong() }
        val textInputUpperBound = historyTokenBudget +
            prompt.utf8ByteCount().toLong()
        val fixedReserve = TEMPLATE_RESERVE_TOKENS +
            if (hasImage || (historyHasImage && cachedTokenCount == null)) VISION_RESERVE_TOKENS else 0
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
