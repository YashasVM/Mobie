package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtCpuThreadPolicyTest {
    @Test
    fun usesOneThreadOnSingleCoreDevice() {
        assertEquals(1, LiteRtCpuThreadPolicy.threadCount(1))
    }

    @Test
    fun usesTwoThreadsWhenAtLeastTwoProcessorsAreAvailable() {
        assertEquals(2, LiteRtCpuThreadPolicy.threadCount(2))
        assertEquals(2, LiteRtCpuThreadPolicy.threadCount(8))
    }

    @Test
    fun invalidProcessorCountStillFallsBackToOneThread() {
        assertEquals(1, LiteRtCpuThreadPolicy.threadCount(0))
    }
}
