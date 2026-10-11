package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtCpuThreadPolicyTest {
    private fun layout(vararg frequencies: Int): Map<Int, Int> =
        LiteRtCpuThreadPolicy.parseMaxFrequencies(frequencies.mapIndexed { cpu, khz ->
            "cpu$cpu/cpufreq/cpuinfo_max_freq" to " $khz\n"
        }.toMap())

    @Test fun snapdragon8Gen3CountsPrimeAndBigCores() {
        // 2 little + 2 middle + 3 big + 1 prime.
        assertEquals(6, LiteRtCpuThreadPolicy.threadCount(layout(2265600, 2265600, 2803200, 2803200, 3153600, 3153600, 3153600, 3398400), 8))
    }

    @Test fun tensorG3CountsFourPerformanceCores() {
        assertEquals(4, LiteRtCpuThreadPolicy.threadCount(layout(1704000, 1704000, 1704000, 1704000, 2367000, 2367000, 2367000, 2914000), 8))
    }

    @Test fun dimensityCountsPerformanceCoresAndCapsAtSix() {
        assertEquals(6, LiteRtCpuThreadPolicy.threadCount(layout(2000000, 2000000, 2800000, 2800000, 2800000, 2800000, 3200000, 3400000), 8))
    }

    @Test fun genericFourPlusFourAndFallback() {
        assertEquals(4, LiteRtCpuThreadPolicy.threadCount(layout(1800000, 1800000, 1800000, 1800000, 2600000, 2600000, 2600000, 2600000), 8))
        assertEquals(2, LiteRtCpuThreadPolicy.threadCount(emptyMap(), 8))
        assertEquals(1, LiteRtCpuThreadPolicy.threadCount(emptyMap(), 1))
        assertEquals(2, LiteRtCpuThreadPolicy.threadCount(layout(2000000, 2000000, 2000000, 2000000), 4))
    }

    @Test fun overrideIsBoundedAndInvalidFilesIgnored() {
        assertEquals(1, LiteRtCpuThreadPolicy.threadCount(emptyMap(), 8, 0))
        assertEquals(8, LiteRtCpuThreadPolicy.threadCount(emptyMap(), 8, 99))
        assertEquals(emptyMap<Int, Int>(), LiteRtCpuThreadPolicy.parseMaxFrequencies(mapOf("cpu0/cpufreq/cpuinfo_max_freq" to "bad")))
    }
}
