package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class InferencePerformanceHintsTest {
    @Test fun belowApi31DoesNotOpenPlatformSession() {
        var creations = 0
        val session = InferenceHintSessions.create(30) {
            creations++
            error("Platform API must not be called")
        }
        session.reportActualDuration(100)
        session.close()
        assertEquals(0, creations)
    }

    @Test fun unavailableHintsDoNotReadClockForAnyOutput() {
        var clockReads = 0
        val tracker = InferenceHintTracker(InferenceHintSessions.noOp) { clockReads++; clockReads.toLong() }
        repeat(1_000) { tracker.onOutput() }
        tracker.close()
        tracker.onOutput()
        assertEquals(0, clockReads)
    }

    @Test fun reportsMeasuredPrefillAndDecodeIntervalsAndCloses() {
        val durations = mutableListOf<Long>()
        var closed = false
        var clock = 0L
        val session = object : InferenceHintSession {
            override fun reportActualDuration(durationNanos: Long) { durations += durationNanos }
            override fun close() { closed = true }
        }
        val tracker = InferenceHintTracker(session) { clock }
        clock = 80L
        tracker.onOutput()
        clock = 100L
        tracker.onOutput()
        tracker.close()
        assertEquals(listOf(80L, 20L), durations)
        assertEquals(true, closed)
    }
}
