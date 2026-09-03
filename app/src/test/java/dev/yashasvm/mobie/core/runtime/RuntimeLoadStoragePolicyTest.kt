package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLoadStoragePolicyTest {
    @Test
    fun blocksWhenFirstLoadCacheWouldExhaustFreeStorage() {
        val reason = RuntimeLoadStoragePolicy.blockReason(
            modelWeightsBytes = 2L * GIB,
            availableStorageBytes = 500L * MIB,
        )

        assertNotNull(reason)
        assertTrue(reason!!.contains("optimized cache"))
    }

    @Test
    fun allowsLoadWhenCacheAndFilesystemReserveFit() {
        assertNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 2L * GIB,
                availableStorageBytes = 900L * MIB,
            ),
        )
    }

    @Test
    fun reservesMinimumCacheSpaceForSmallModels() {
        assertNotNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 128L * MIB,
                availableStorageBytes = 300L * MIB,
            ),
        )
        assertNull(
            RuntimeLoadStoragePolicy.blockReason(
                modelWeightsBytes = 128L * MIB,
                availableStorageBytes = 321L * MIB,
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

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}
