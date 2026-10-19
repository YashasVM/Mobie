package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.CancellationException

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

    @Test fun noOpSessionSkipsClockForEveryOutputIncludingInitialization() {
        var clockReads = 0
        val tracker = InferenceHintTracker(InferenceHintSessions.noOp) { clockReads++; clockReads.toLong() }
        repeat(1_000) { tracker.onOutput() }
        tracker.close()
        tracker.close()
        assertEquals(0, clockReads)
    }

    @Test fun declinedPlatformSessionAlsoSkipsClockWork() {
        var creations = 0
        var clockReads = 0
        val session = InferenceHintSessions.create(31) { creations++; null }
        val tracker = InferenceHintTracker(session) { clockReads++; clockReads.toLong() }
        repeat(1_000) { tracker.onOutput() }
        tracker.close()
        assertEquals(1, creations)
        assertEquals(0, clockReads)
    }

    @Test fun failedSessionStopsPlatformCallsLogsAndClockReadsButStillCloses() {
        var reports = 0
        var clockReads = 0
        var closes = 0
        val failures = mutableListOf<RuntimeException>()
        val failure = IllegalStateException("Platform hint unavailable")
        val session = BestEffortInferenceHintSession(
            report = { reports++; throw failure },
            closeSession = { closes++ },
            reportFailure = { failures += it },
        )
        val tracker = InferenceHintTracker(session) { clockReads++; clockReads.toLong() }
        repeat(1_000) { tracker.onOutput() }
        tracker.close()
        tracker.close()
        session.close()
        assertEquals(1, reports)
        assertEquals(listOf(failure), failures)
        assertEquals(2, clockReads) // Initialization and the first failing report only.
        assertEquals(1, closes)
        assertEquals(false, session.isAvailable)
    }

    @Test fun healthyPlatformSessionKeepsEveryMeasuredInterval() {
        var clockReads = 0
        var closes = 0
        val durations = mutableListOf<Long>()
        val session = BestEffortInferenceHintSession(
            report = { durations += it },
            closeSession = { closes++ },
            reportFailure = { throw AssertionError("Healthy session must not fail", it) },
        )
        val tracker = InferenceHintTracker(session) { clockReads++; clockReads * 50L }
        repeat(1_000) { tracker.onOutput() }
        tracker.close()
        tracker.onOutput()
        assertEquals(List(1_000) { 50L }, durations)
        assertEquals(1_001, clockReads)
        assertEquals(1, closes)
    }

    @Test fun failedSessionDoesNotDisableTheNextGenerationsSession() {
        var successfulReports = 0
        val failed = BestEffortInferenceHintSession({ throw IllegalStateException("Unavailable") }, {}, {})
        val next = BestEffortInferenceHintSession({ successfulReports++ }, {}, {})
        failed.reportActualDuration(10)
        failed.close()
        next.reportActualDuration(10)
        next.close()
        assertEquals(1, successfulReports)
    }

    @Test fun reportCancellationEscapesWithoutBeingLoggedAsAnOptionalFailure() {
        val cancellation = CancellationException("Cancelled")
        var failures = 0
        var closes = 0
        val session = BestEffortInferenceHintSession(
            report = { throw cancellation },
            closeSession = { closes++ },
            reportFailure = { failures++ },
        )
        assertSame(cancellation, assertThrows(CancellationException::class.java) { session.reportActualDuration(1) })
        session.close()
        assertEquals(0, failures)
        assertEquals(1, closes)
    }

    @Test fun reportErrorsAndCleanupFailuresAreNotSwallowed() {
        val fatal = AssertionError("Platform Error")
        val cleanup = IllegalStateException("Close failed")
        var failures = 0
        var closes = 0
        val session = BestEffortInferenceHintSession(
            report = { throw fatal },
            closeSession = { closes++; throw cleanup },
            reportFailure = { failures++ },
        )
        assertSame(fatal, assertThrows(AssertionError::class.java) { session.reportActualDuration(1) })
        assertSame(cleanup, assertThrows(IllegalStateException::class.java) { session.close() })
        session.close()
        assertEquals(0, failures)
        assertEquals(1, closes)
    }
}
