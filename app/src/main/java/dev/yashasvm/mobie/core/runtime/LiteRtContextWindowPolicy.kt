package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import kotlin.math.max

/** Choose a stable chat context from total RAM. Current pressure affects admission, not context. */
internal object LiteRtContextWindowPolicy {
    const val MIN_USEFUL_CONTEXT_TOKENS = 1_024
    private const val MAX_CHAT_CONTEXT_TOKENS = 4_096

    fun select(
        advertisedContextWindowTokens: Int,
        modelWeightsBytes: Long,
        totalRamBytes: Long,
        availableRamBytes: Long,
        lowMemoryThresholdBytes: Long,
        isLowRamDevice: Boolean,
        backend: ComputeBackend = ComputeBackend.CPU,
    ): Int {
        val advertised = advertisedContextWindowTokens.coerceIn(1, MAX_CHAT_CONTEXT_TOKENS)
        if (modelWeightsBytes <= 0 || totalRamBytes <= 0) return advertised
        val reserve = max(lowMemoryThresholdBytes, totalRamBytes / if (isLowRamDevice) 5 else 10)
        val budget = (totalRamBytes - reserve).coerceAtLeast(0)
        var tokens = advertised / 256 * 256
        while (tokens > MIN_USEFUL_CONTEXT_TOKENS) {
            val artifact = ModelArtifact("model.litertlm", "", modelWeightsBytes,
                format = ModelFormat.LITERT_LM, contextWindowTokens = tokens)
            if (requireNotNull(RuntimeLoadMemoryPolicy.estimate(artifact, backend)).estimatedRamBytes <= budget) return tokens
            tokens -= 256
        }
        return minOf(advertised, MIN_USEFUL_CONTEXT_TOKENS)
    }
}
