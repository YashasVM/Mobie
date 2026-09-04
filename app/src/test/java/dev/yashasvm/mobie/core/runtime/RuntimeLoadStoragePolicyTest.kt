package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLoadStoragePolicyTest {
    @Test
    fun blocksWhenMeasuredScaleFirstLoadCacheWouldExhaustFreeStorage() {
        val weights = 2L * GIB
        val required = RuntimeLoadStoragePolicy.requiredColdLoadFreeBytes(weights)

        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = weights,
                availableStorageBytes = required - 1,
            ),
        )
        assertNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = weights,
                availableStorageBytes = required,
            ),
        )
    }

    @Test
    fun validatedWarmCacheNeedsOnlyFilesystemReserve() {
        val weights = 2L * GIB
        val reserve = RuntimeLoadStoragePolicy.filesystemReserveBytes(weights)

        assertEquals(reserve, RuntimeLoadStoragePolicy.requiredLoadFreeBytes(weights, reusableCache = true))
        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = weights,
                availableStorageBytes = reserve - 1,
                reusableCache = true,
            ),
        )
        assertNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = weights,
                availableStorageBytes = reserve,
                reusableCache = true,
            ),
        )
        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = weights,
                availableStorageBytes = reserve,
                reusableCache = false,
            ),
        )
    }

    @Test
    fun reservesModelSizedCachePlusTenPercentSafetyForLargeModels() {
        val weights = 2L * GIB

        assertEquals(weights + weights / 10, RuntimeLoadStoragePolicy.firstLoadCacheHeadroomBytes(weights))
        assertEquals(weights / 20, RuntimeLoadStoragePolicy.filesystemReserveBytes(weights))
    }

    @Test
    fun measuredQwenCacheFitsInsideColdLoadAllowance() {
        val weights = 347_251_840L
        val measuredCache = 339_216_776L

        assertTrue(RuntimeLoadStoragePolicy.firstLoadCacheHeadroomBytes(weights) > measuredCache)
    }

    @Test
    fun reservesMinimumCacheSpaceForSmallModels() {
        assertEquals(256L * MIB, RuntimeLoadStoragePolicy.firstLoadCacheHeadroomBytes(128L * MIB))
        assertEquals(64L * MIB, RuntimeLoadStoragePolicy.filesystemReserveBytes(128L * MIB))
        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 128L * MIB,
                availableStorageBytes = 319L * MIB,
            ),
        )
        assertNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 128L * MIB,
                availableStorageBytes = 320L * MIB,
            ),
        )
    }

    @Test
    fun treatsZeroFreeSpaceAsUnsafeForKnownModel() {
        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 350L * MIB,
                availableStorageBytes = 0,
            ),
        )
    }

    @Test
    fun blocksMissingOrEmptyInstalledModelBeforeNativeInitialization() {
        val reason = RuntimeLoadStoragePolicy.blockReason(
            modelWeightsBytes = 0,
            availableStorageBytes = 2L * GIB,
        )

        assertNotNull(reason)
        assertTrue(reason!!.contains("missing or empty"))
    }

    @Test
    fun blocksWhenFreeStorageCannotBeMeasured() {
        val reason = RuntimeLoadStoragePolicy.blockReason(
            modelWeightsBytes = 350L * MIB,
            availableStorageBytes = -1,
        )

        assertNotNull(reason)
        assertTrue(reason!!.contains("Could not verify free storage"))
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
