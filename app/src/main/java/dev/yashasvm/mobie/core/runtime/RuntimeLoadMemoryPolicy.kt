package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.LiteRtRuntimeMemoryEstimate
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.estimateLiteRtRuntimeMemory
import kotlin.math.max

internal data class MemoryAdmission(val tier: MemoryAdmissionTier, val reason: String, val bytesToFree: Long = 0) {
    fun blockReason(allowReclaim: Boolean): String? = when (tier) {
        MemoryAdmissionTier.FITS_NOW -> null
        MemoryAdmissionTier.FITS_AFTER_RECLAIM -> if (allowReclaim) null else reason
        MemoryAdmissionTier.IMPOSSIBLE -> reason
    }
}

/** One admission policy for catalog and native load. All quantities are physical RAM estimates. */
internal object RuntimeLoadMemoryPolicy {
    fun admit(
        estimate: LiteRtRuntimeMemoryEstimate,
        totalRamBytes: Long,
        availableRamBytes: Long,
        availableAfterReclaimBytes: Long,
        lowMemoryThresholdBytes: Long,
        isLowMemory: Boolean,
        isLowRamDevice: Boolean,
    ): MemoryAdmission {
        val reserve = max(lowMemoryThresholdBytes, totalRamBytes / if (isLowRamDevice) 5 else 10)
        val totalBudget = (totalRamBytes - reserve).coerceAtLeast(0)
        // An almost RAM-sized file can cause sustained page fault thrash even if only half of its
        // pages are resident at one instant. Keep a separate 80% file-size ceiling.
        if (estimate.estimatedRamBytes > totalBudget || estimate.fileBackedWeightsBytes > totalRamBytes * 4 / 5) {
            return MemoryAdmission(MemoryAdmissionTier.IMPOSSIBLE,
                "This model needs more RAM than this phone can provide safely.")
        }
        val nowBudget = (availableRamBytes - lowMemoryThresholdBytes).coerceAtLeast(0)
        if (!isLowMemory && estimate.estimatedRamBytes <= nowBudget) {
            return MemoryAdmission(MemoryAdmissionTier.FITS_NOW,
                "Expected to fit in memory available now.")
        }
        val afterBudget = (availableAfterReclaimBytes - lowMemoryThresholdBytes).coerceAtLeast(0)
        val toFree = (estimate.estimatedRamBytes - nowBudget).coerceAtLeast(0)
        val gib = String.format(java.util.Locale.US, "%.1f", toFree.toDouble() / GIB)
        val reason = if (isLowMemory && toFree == 0L) {
            "Android reports active memory pressure. Close background apps before loading."
        } else if (estimate.estimatedRamBytes <= afterBudget) {
            "Android will need to close background apps to free about $gib GB."
        } else {
            "Close other apps to free about $gib GB before loading. Android may also reclaim cached memory."
        }
        return MemoryAdmission(MemoryAdmissionTier.FITS_AFTER_RECLAIM, reason, toFree)
    }

    fun blockReason(
        modelWeightsBytes: Long,
        totalRamBytes: Long,
        availableRamBytes: Long,
        lowMemoryThresholdBytes: Long,
        isLowMemory: Boolean,
        isLowRamDevice: Boolean,
        thermalStatus: Int = 0,
        contextWindowTokens: Int = 4_096,
        availableAfterReclaimBytes: Long = availableRamBytes,
        allowReclaim: Boolean = false,
        backend: ComputeBackend = ComputeBackend.CPU,
        visionEncoderLoaded: Boolean = false,
    ): String? {
        if (thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return "Android reports critical thermal pressure. Let the device cool before loading a local model."
        }
        if (contextWindowTokens < LiteRtContextWindowPolicy.MIN_USEFUL_CONTEXT_TOKENS) {
            return "This LiteRT package exposes only $contextWindowTokens context tokens, below Mobie's 1,024-token minimum for useful local chat."
        }
        if (modelWeightsBytes <= 0 || totalRamBytes <= 0) return "Model size or device RAM is unknown."
        val estimate = estimateLiteRtRuntimeMemory(ModelArtifact("model.litertlm", "", modelWeightsBytes,
            format = ModelFormat.LITERT_LM, contextWindowTokens = contextWindowTokens),
            backend.privateWeightCopy, visionEncoderLoaded)
            ?: return "Model memory could not be estimated."
        return admit(estimate, totalRamBytes, availableRamBytes, availableAfterReclaimBytes,
            lowMemoryThresholdBytes, isLowMemory, isLowRamDevice).blockReason(allowReclaim)
    }

    fun generationBlockReason(
        isLowMemory: Boolean,
        thermalStatus: Int = 0,
        availableRamBytes: Long = 0,
        lowMemoryThresholdBytes: Long = 0,
        totalRamBytes: Long = 0,
    ): String? {
        if (isLowMemory) return "Android reports active memory pressure. Close other apps before starting generation."
        if (thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return "Android reports critical thermal pressure. Let the device cool before continuing local generation."
        }
        if (availableRamBytes > 0 && lowMemoryThresholdBytes > 0) {
            val proactiveReserve = max(128L * MIB, totalRamBytes / 50)
            if (availableRamBytes <= lowMemoryThresholdBytes + proactiveReserve) {
                return "Free RAM is approaching Android's low-memory threshold. Close other apps before continuing local generation."
            }
        }
        return null
    }

    fun shouldRecheckGenerationMemory(lastCheckAtMs: Long, nowMs: Long): Boolean =
        nowMs - lastCheckAtMs >= 500L

    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB
    private const val THERMAL_STATUS_CRITICAL = 4
}
