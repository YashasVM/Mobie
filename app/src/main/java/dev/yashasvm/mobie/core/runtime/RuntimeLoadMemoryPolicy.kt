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
    ): String? {
        if (isLowMemory) {
            return "Android reports active memory pressure. Free memory before loading this model."
        }
        if (thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return "Android reports critical thermal pressure. Let the device cool before loading a local model."
        }
        if (modelWeightsBytes <= 0 || totalRamBytes <= 0) return null

        val runtimeOverhead = max((modelWeightsBytes * 0.4).toLong(), 512L * MIB)
        val estimatedRam = modelWeightsBytes + runtimeOverhead + 256L * MIB
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
    ): String? = when {
        isLowMemory -> "Android reports active memory pressure. Close other apps before starting generation."
        thermalStatus >= THERMAL_STATUS_CRITICAL ->
            "Android reports critical thermal pressure. Let the device cool before starting generation."
        else -> null
    }

    /** Avoid querying Android system services for every streamed token while reacting quickly to pressure. */
    fun shouldRecheckGenerationMemory(lastCheckAtMs: Long, nowMs: Long): Boolean =
        nowMs - lastCheckAtMs >= GENERATION_MEMORY_RECHECK_MS

    private const val GENERATION_MEMORY_RECHECK_MS = 500L
    private const val MIB = 1024L * 1024L
    private const val THERMAL_STATUS_NONE = 0
    private const val THERMAL_STATUS_CRITICAL = 4
}