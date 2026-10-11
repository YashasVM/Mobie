package dev.yashasvm.mobie.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCurrentMathTest {
    @Test fun `normalizes reported milliamp and microamp values with sign`() {
        assertEquals(-500_000L, BatteryCurrentMath.microamps(-500L))
        assertEquals(500_000L, BatteryCurrentMath.microamps(500_000L))
        // Samsung-style milliamp reports under heavy load must not be dropped.
        assertEquals(-1_500_000L, BatteryCurrentMath.microamps(-1_500L))
        assertEquals(9_999_000L, BatteryCurrentMath.microamps(9_999L))
        assertEquals(10_000L, BatteryCurrentMath.microamps(10_000L))
        assertNull(BatteryCurrentMath.microamps(Long.MIN_VALUE))
        assertNull(BatteryCurrentMath.microamps(0L))
    }

    @Test fun `estimates whole device energy per measured decode token`() {
        val energy = BatteryCurrentMath.milliWh(-1_000_000L, 4_000, 1_000)
        assertEquals(1.111111, energy!!, 0.00001)
        assertEquals(0.111111, BatteryCurrentMath.milliWhPerToken(energy, 10)!!, 0.00001)
        assertNull(BatteryCurrentMath.milliWh(null, 4_000, 1_000))
        assertNull(BatteryCurrentMath.milliWh(-1_000_000L, 0, 1_000))
        assertNull(BatteryCurrentMath.milliWhPerToken(energy, 0))
    }
}
