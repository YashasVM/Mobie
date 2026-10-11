package dev.yashasvm.mobie.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySnapshotTest {
    @Test fun parsesSamsungStyleMeminfo() {
        val parsed = requireNotNull(ProcMemInfoParser.parse(SAMSUNG))
        assertEquals(11_674_512L * KIB, parsed.totalBytes)
        assertEquals(2_105_648L * KIB, parsed.availableBytes)
        assertEquals(4_210_344L * KIB, parsed.cachedBytes)
        assertEquals(3_043_492L * KIB, parsed.inactiveFileBytes)
        assertEquals(1_929_284L * KIB, parsed.swapFreeBytes)
        assertEquals(35_212L * KIB, parsed.shmemBytes)
    }

    @Test fun parsesPixelStyleMeminfoWithMissingOptionalFields() {
        val parsed = requireNotNull(ProcMemInfoParser.parse(PIXEL))
        assertEquals(7_891_216L * KIB, parsed.totalBytes)
        assertEquals(3_006_712L * KIB, parsed.availableBytes)
        assertNull(parsed.mlockedBytes)
        assertNull(parsed.activeFileBytes)
    }

    @Test fun rejectsMalformedOrIncompleteSnapshot() {
        assertNull(ProcMemInfoParser.parse(SAMSUNG.replace("MemAvailable:  2105648 kB", "MemAvailable: unknown")))
        assertNull(ProcMemInfoParser.parse(PIXEL.replace("SwapFree:       728800 kB", "")))
        assertNull(ProcMemInfoParser.parse(PIXEL.replace("MemTotal:      7891216 kB", "MemTotal: 1 kB")))
    }

    @Test fun reclaimCountsOnlyBoundedCacheAndSwapAndRespectsSystemReserve() {
        val proc = requireNotNull(ProcMemInfoParser.parse(SAMSUNG))
        val after = ReclaimableMemory.afterReclaim(proc, proc.totalBytes, proc.availableBytes,
            512L * MIB, false)
        assertTrue(after > proc.availableBytes)
        assertTrue(after < proc.availableBytes + 2L * GIB)
        assertTrue(after <= proc.totalBytes - proc.totalBytes / 10)
        val pressured = ReclaimableMemory.afterReclaim(proc, 3L * GIB, 2L * GIB, 2L * GIB, true)
        assertTrue(pressured <= 768L * MIB)
    }

    @Test fun reclaimMatchesDocumentedFormulaOnSamsungSample() {
        val proc = requireNotNull(ProcMemInfoParser.parse(SAMSUNG))
        val after = ReclaimableMemory.afterReclaim(proc, proc.totalBytes, proc.availableBytes,
            512L * MIB, false)
        // extraFile = (min(cache, inactive + active/4) - available) / 4 = 274_763 KiB
        // extraSwap = swapFree / 4 = 482_321 KiB (below the 512 MiB cap)
        assertEquals((2_105_648L + 274_763L + 482_321L) * KIB, after)
    }

    @Test fun fallbackNeverInventsReclaimableMemory() {
        assertEquals(2L * GIB, ReclaimableMemory.afterReclaim(null, 8L * GIB, 2L * GIB, 512L * MIB, false))
    }

    private companion object {
        const val KIB = 1024L
        const val MIB = 1024L * KIB
        const val GIB = 1024L * MIB
        // Representative procfs layouts; values are sample fixtures, not device measurements.
        const val SAMSUNG = """MemTotal:     11674512 kB
MemFree:        512024 kB
MemAvailable:  2105648 kB
Buffers:        153600 kB
Cached:        4210344 kB
SwapCached:     104012 kB
Active(file):   644832 kB
Inactive(file): 3043492 kB
Shmem:          35212 kB
Mlocked:          8192 kB
SwapTotal:     4194304 kB
SwapFree:      1929284 kB"""
        const val PIXEL = """MemTotal:      7891216 kB
MemFree:        360104 kB
MemAvailable:  3006712 kB
Buffers:         70548 kB
Cached:        2577316 kB
Shmem:          151436 kB
SwapTotal:     2097152 kB
SwapFree:       728800 kB"""
    }
}
