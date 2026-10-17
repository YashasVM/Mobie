package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier

/** Presentation of the shared admission verdict; it never estimates RAM or overrides a block. */
internal data class MemoryReliefDecision(
    val neededBytes: Long,
    val freeNowBytes: Long,
    val reclaimableBytes: Long,
    val showFreeMemory: Boolean,
    val canLoad: Boolean,
    val requiresConfirmation: Boolean,
) {
    companion object {
        fun from(result: CompatibilityResult?, device: DeviceProfile?): MemoryReliefDecision {
            val warning = result?.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM
            val canLoad = result?.status in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) &&
                result?.memoryTier != MemoryAdmissionTier.IMPOSSIBLE
            val now = device?.availableRamBytes ?: 0L
            return MemoryReliefDecision(
                neededBytes = result?.estimatedRamBytes ?: 0L,
                freeNowBytes = now,
                reclaimableBytes = ((device?.availableAfterReclaimBytes ?: now) - now).coerceAtLeast(0L),
                showFreeMemory = warning && canLoad,
                canLoad = canLoad,
                requiresConfirmation = warning && canLoad,
            )
        }
    }
}
