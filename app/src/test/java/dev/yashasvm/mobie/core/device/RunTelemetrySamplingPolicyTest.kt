package dev.yashasvm.mobie.core.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTelemetrySamplingPolicyTest {
    @Test fun `first read refreshes at boot and after a long uptime`() {
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(null, 0))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(null, 123_456_789))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(null, Long.MAX_VALUE))
    }

    @Test fun `subsequent reads refresh only at the interval boundary`() {
        assertFalse(RunTelemetrySamplingPolicy.shouldRefresh(100, 100))
        assertFalse(RunTelemetrySamplingPolicy.shouldRefresh(100, 1_099))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(100, 1_100))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(100, 2_100))
    }

    @Test fun `clock reset and obsolete negative sentinel force a fresh sample`() {
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(10_000, 10))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(Long.MIN_VALUE, 10))
    }

    @Test fun `large valid timestamps do not overflow the interval calculation`() {
        assertFalse(RunTelemetrySamplingPolicy.shouldRefresh(Long.MAX_VALUE - 999, Long.MAX_VALUE))
        assertTrue(RunTelemetrySamplingPolicy.shouldRefresh(Long.MAX_VALUE - 1_000, Long.MAX_VALUE))
    }
}
