package dev.yashasvm.mobie.core.runtime

import kotlin.math.max
import kotlin.math.min

/**
 * Chooses the largest LiteRT KV/context allocation that fits the same conservative RAM envelope
 * used by runtime admission. Extended-context artifacts should not reserve their entire advertised
 * KV cache when the current device cannot safely sustain it.
 *
 * This policy is intentionally pure so its memory assumptions can be regression-tested before it
 * is wired into native EngineConfig.
 */
internal object LiteRtContextWindowPolicy {
    fun select(
        advertisedContextWindowTokens: Int,
        modelWeightsBytes: Long,
        totalRamBytes: Long,
        availableRamBytes: Long,
        lowMemoryThresholdBytes: Long,
        isLowRamDevice: Boolean,
    ): Int {
        val advertised = advertisedContextWindowTokens.coerceAtLeast(MIN_CONTEXT_TOKENS)
        if (modelWeightsBytes <= 0 || totalRamBytes <= 0 || availableRamBytes <= 0) return advertised

        val runtimeOverheadBytes = max((modelWeightsBytes * 0.4).toLong(), MIN_RUNTIME_OVERHEAD_BYTES)
        val totalFraction = if (isLowRamDevice) 0.70 else 0.80
        val totalRuntimeBudget = (totalRamBytes * totalFraction).toLong()

        val reserve = max(lowMemoryThresholdBytes, totalRamBytes / 20)
        val availableFraction = if (isLowRamDevice) 0.75 else 0.85
        val availableRuntimeBudget = ((availableRamBytes - reserve).coerceAtLeast(0) * availableFraction).toLong()

        val kvBudgetBytes = min(totalRuntimeBudget, availableRuntimeBudget) - modelWeightsBytes - runtimeOverheadBytes
        val budgetedTokens = if (kvBudgetBytes <= MIN_KV_CACHE_BYTES) {
            MIN_CONTEXT_TOKENS
        } else {
            (kvBudgetBytes / KV_BYTES_PER_TOKEN)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(MIN_CONTEXT_TOKENS)
        }

        val selected = min(advertised, budgetedTokens)
        if (selected <= MIN_CONTEXT_TOKENS) return MIN_CONTEXT_TOKENS

        // Keep allocations stable instead of changing by a handful of tokens as Android's free-RAM
        // reading fluctuates. 256-token steps are small relative to phone chat contexts but avoid
        // unnecessary engine/cache churn when this policy is integrated with runtime loading.
        return (selected / CONTEXT_ALIGNMENT_TOKENS * CONTEXT_ALIGNMENT_TOKENS)
            .coerceAtLeast(MIN_CONTEXT_TOKENS)
    }

    private const val MIB = 1024L * 1024L
    private const val MIN_RUNTIME_OVERHEAD_BYTES = 512L * MIB
    private const val MIN_KV_CACHE_BYTES = 64L * MIB
    private const val DEFAULT_KV_CACHE_BYTES = 256L * MIB
    private const val DEFAULT_CONTEXT_TOKENS = 4_096L
    private const val KV_BYTES_PER_TOKEN = DEFAULT_KV_CACHE_BYTES / DEFAULT_CONTEXT_TOKENS
    private const val MIN_CONTEXT_TOKENS = 1_024
    private const val CONTEXT_ALIGNMENT_TOKENS = 256
}