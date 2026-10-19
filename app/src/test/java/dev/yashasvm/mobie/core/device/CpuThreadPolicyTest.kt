package dev.yashasvm.mobie.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuThreadPolicyTest {
    @Test fun `explicit settings cannot exceed detected cores`() {
        assertEquals(4, CpuThreadPolicy.clamp(8, 4))
        assertEquals(1, CpuThreadPolicy.clamp(8, 1))
        assertEquals(8, CpuThreadPolicy.clamp(64, 16))
        assertEquals(2, CpuThreadPolicy.clamp(2, 4))
        assertEquals(1, CpuThreadPolicy.clamp(-1, 4))
        assertNull(CpuThreadPolicy.clamp(null, 1))
    }

    @Test fun `picker limits cover small and malformed core counts`() {
        assertEquals(1, CpuThreadPolicy.limit(1))
        assertEquals(4, CpuThreadPolicy.limit(4))
        assertEquals(8, CpuThreadPolicy.limit(16))
        assertEquals(1, CpuThreadPolicy.limit(0))
        assertEquals(1, CpuThreadPolicy.limit(-1))
    }
}
