package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryReliefDecisionTest {
    private val resolver = CompatibilityResolver()
    private val artifact = ModelArtifact("small.litertlm", "", GIB, format = ModelFormat.LITERT_LM)

    @Test
    fun fitsWithoutWarningWhenMemoryIsAvailable() {
        val device = device(now = 4 * GIB, after = 4 * GIB)
        val result = resolver.resolve(artifact, device)
        val decision = MemoryReliefDecision.from(result, device)
        assertEquals(MemoryAdmissionTier.FITS_NOW, result.memoryTier)
        assertTrue(decision.canLoad)
        assertFalse(decision.requiresConfirmation)
        assertFalse(decision.showFreeMemory)
        assertEquals(1280 * MIB, decision.neededBytes)
    }

    @Test
    fun plausibleShortfallRetainsUserOverrideAndShowsRelief() {
        val device = device(now = 512 * MIB, after = 2 * GIB)
        val result = resolver.resolve(artifact, device)
        val decision = MemoryReliefDecision.from(result, device)
        assertEquals(MemoryAdmissionTier.FITS_AFTER_RECLAIM, result.memoryTier)
        assertTrue(decision.canLoad)
        assertTrue(decision.requiresConfirmation)
        assertTrue(decision.showFreeMemory)
        assertEquals(1536 * MIB, decision.reclaimableBytes)
    }

    @Test
    fun impossibleModelCannotBeOverriddenByFreeingMemory() {
        val device = device(now = 512 * MIB, after = 2 * GIB).copy(totalRamBytes = 2 * GIB)
        val result = resolver.resolve(artifact.copy(sizeBytes = 3 * GIB), device)
        val decision = MemoryReliefDecision.from(result, device)
        assertEquals(MemoryAdmissionTier.IMPOSSIBLE, result.memoryTier)
        assertFalse(decision.canLoad)
        assertFalse(decision.requiresConfirmation)
        assertFalse(decision.showFreeMemory)
    }

    @Test
    fun freshSnapshotAfterReturnRemovesMemoryConfirmationAsSoonAsItFits() {
        val before = device(now = 512 * MIB, after = 2 * GIB)
        val after = before.copy(availableRamBytes = 3 * GIB, availableAfterReclaimBytes = 3 * GIB)
        assertTrue(MemoryReliefDecision.from(resolver.resolve(artifact, before), before).requiresConfirmation)
        val improved = MemoryReliefDecision.from(resolver.resolve(artifact, after), after)
        assertTrue(improved.canLoad)
        assertFalse(improved.requiresConfirmation)
        assertFalse(improved.showFreeMemory)
        assertEquals(3 * GIB, improved.freeNowBytes)
    }

    @Test
    fun storageBlockRemainsBlockedEvenIfRamImproves() {
        val device = device(now = 4 * GIB, after = 4 * GIB).copy(availableStorageBytes = 0)
        assertFalse(MemoryReliefDecision.from(resolver.resolve(artifact, device), device).canLoad)
    }

    @Test
    fun pressureStillWarnsAndMissingSnapshotNeverEnablesLoad() {
        val device = device(now = 4 * GIB, after = 3 * GIB).copy(isLowMemory = true)
        val decision = MemoryReliefDecision.from(resolver.resolve(artifact, device), device)
        assertTrue(decision.requiresConfirmation)
        assertEquals(0L, decision.reclaimableBytes)
        assertFalse(MemoryReliefDecision.from(null, null).canLoad)
    }

    private fun device(now: Long, after: Long) = DeviceProfile(
        totalRamBytes = 8 * GIB,
        availableRamBytes = now,
        availableAfterReclaimBytes = after,
        availableStorageBytes = 20 * GIB,
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
        lowMemoryThresholdBytes = 256 * MIB,
    )

    private companion object {
        const val MIB = 1024L * 1024
        const val GIB = 1024L * MIB
    }
}
