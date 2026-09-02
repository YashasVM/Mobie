package dev.yashasvm.mobie.core.runtime

import kotlin.math.max

/** Conservative preflight for a real LiteRT model load using the device's current RAM state. */
internal object RuntimeLoadMemoryPolicy {
    fun blockReason(
        modelWeightsBytes: Long,
        totalRamBytes: Long,
        availableRamBytes: Long,
        lowMemoryThresholdBytes: Long,
        isLowMemory: Boolean,
        isLowRamDevice: Boolean,
        thermalStatus: Int = THERMAL_STATUS_NONE,
        contextWindowTokens: Int = DEFAULT_CONTEXT_TOKENS,
    ): String? {
        if (isLowMemory) {
            return "Android reports active memory pressure. Free memory before loading this model."
        }
        if (thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return "Android reports critical thermal pressure. Let the device cool before loading a local model."
        }
        if (modelWeightsBytes <= 0 || totalRamBytes <= 0) return null

        val runtimeOverhead = max((modelWeightsBytes * 0.4).toLong(), 512L * MIB)
        val safeContextWindow = contextWindowTokens.coerceAtLeast(MIN_CONTEXT_TOKENS)
        val kvCacheBytes = max(
            MIN_KV_CACHE_BYTES,
            DEFAULT_KV_CACHE_BYTES * safeContextWindow / DEFAULT_CONTEXT_TOKENS,
        )
        val estimatedRam = modelWeightsBytes + runtimeOverhead + kvCacheBytes
        val totalFraction = if (isLowRamDevice) 0.70 else 0.80
        if (estimatedRam > totalRamBytes * totalFraction) {
            return if (isLowRamDevice) {
                "This model leaves too little RAM for a stable local runtime on this low-RAM device."
            } else {
                "This model is too large for the device's current local-runtime memory budget."
            }
        }

        val reserve = max(lowMemoryThresholdBytes, totalRamBytes / 20)
        val availableFraction = if (isLowRamDevice) 0.75 else 0.85
        val safeAvailable = ((availableRamBytes - reserve).coerceAtLeast(0) * availableFraction).toLong()
        if (estimatedRam > safeAvailable) {
            return "Current free RAM is too close to Android's low-memory limit. Close other apps before loading this model."
        }
        return null
    }

    fun generationBlockReason(
        isLowMemory: Boolean,
        thermalStatus: Int = THERMAL_STATUS_NONE,
        availableRamBytes: Long = 0,
        lowMemoryThresholdBytes: Long = 0,
        totalRamBytes: Long = 0,
    ): String? {
        if (isLowMemory) {
            return "Android reports active memory pressure. Close other apps before starting generation."
        }
        if (thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return "Android reports critical thermal pressure. Let the device cool before starting generation."
        }
        if (availableRamBytes > 0 && lowMemoryThresholdBytes > 0) {
            val proactiveReserve = max(MIN_GENERATION_HEADROOM_BYTES, totalRamBytes / 50)
            if (availableRamBytes <= lowMemoryThresholdBytes + proactiveReserve) {
                return "Free RAM is approaching Android's low-memory threshold. Close other apps before continuing local generation."
            }
        }
        return null
    }

    /** Avoid querying Android system services for every streamed token while reacting quickly to pressure. */
    fun shouldRecheckGenerationMemory(lastCheckAtMs: Long, nowMs: Long): Boolean =
        nowMs - lastCheckAtMs >= GENERATION_MEMORY_RECHECK_MS

    private const val GENERATION_MEMORY_RECHECK_MS = 500L
    private const val MIB = 1024L * 1024L
    private const val MIN_GENERATION_HEADROOM_BYTES = 128L * MIB
    private const val MIN_KV_CACHE_BYTES = 64L * MIB
    private const val DEFAULT_KV_CACHE_BYTES = 256L * MIB
    private const val DEFAULT_CONTEXT_TOKENS = 4_096
    private const val MIN_CONTEXT_TOKENS = 128
    private const val THERMAL_STATUS_NONE = 0
    private const val THERMAL_STATUS_CRITICAL = 4
}
