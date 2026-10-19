package dev.yashasvm.mobie.core.runtime

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/** A native ADPF-style hint session. Implementations must be safe to close more than once. */
internal interface InferenceHintSession : AutoCloseable {
    /** False when reporting cannot help; callers can skip measuring per-chunk durations. */
    val isAvailable: Boolean get() = true
    fun reportActualDuration(durationNanos: Long)
    override fun close()
}

internal object InferenceHintSessions {
    /** Used below API 31 and whenever the platform declines to create a session. */
    val noOp: InferenceHintSession = object : InferenceHintSession {
        override val isAvailable: Boolean = false
        override fun reportActualDuration(durationNanos: Long) = Unit
        override fun close() = Unit
    }

    fun create(sdkInt: Int, createPlatformSession: () -> InferenceHintSession?): InferenceHintSession =
        if (sdkInt >= Build.VERSION_CODES.S) createPlatformSession() ?: noOp else noOp
}

/**
 * A broken optional hint must not add a platform call, exception and log entry to every token.
 * Disable reports after the first ordinary platform failure, but keep ownership until close.
 * A new generation creates a new session and may try again; cancellation and Errors still escape.
 */
internal class BestEffortInferenceHintSession(
    private val report: (Long) -> Unit,
    private val closeSession: () -> Unit,
    private val reportFailure: (RuntimeException) -> Unit,
) : InferenceHintSession {
    @Volatile private var reportsEnabled = true
    private val closed = AtomicBoolean(false)
    override val isAvailable: Boolean get() = reportsEnabled && !closed.get()

    override fun reportActualDuration(durationNanos: Long) {
        if (!isAvailable) return
        try {
            report(durationNanos)
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            reportsEnabled = false
            reportFailure(error)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) closeSession()
    }
}

/** Reports prefill at the first output and subsequent decode intervals from the native stream. */
internal class InferenceHintTracker(
    private val session: InferenceHintSession,
    private val nowNanos: () -> Long = System::nanoTime,
) : AutoCloseable {
    private var lastReportAt = if (session.isAvailable) nowNanos() else 0L
    private val closed = AtomicBoolean(false)

    fun onOutput() {
        if (closed.get() || !session.isAvailable) return
        val now = nowNanos()
        val elapsed = now - lastReportAt
        if (elapsed > 0) session.reportActualDuration(elapsed)
        lastReportAt = now
    }

    /** Idempotent: generation teardown and unload may both reach here. */
    override fun close() {
        if (closed.compareAndSet(false, true)) session.close()
    }
}

/**
 * Creates the ADPF session for one generation.
 *
 * Known limitation: the session pins [Process.myTid], which is the Kotlin coroutine thread that
 * starts generation. LiteRT-LM decodes on its own native worker threads, so this hint may not
 * cover the threads doing the work. Fixing that needs the native thread IDs, which the SDK does
 * not expose. The benefit of this hint on device is unverified; treat it as best-effort only.
 */
internal class AndroidInferenceHintFactory(private val context: Context) {
    fun create(): InferenceHintSession {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return InferenceHintSessions.noOp
        return createPlatformSession() ?: InferenceHintSessions.noOp
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createPlatformSession(): InferenceHintSession? {
        return try {
            val manager = context.getSystemService(PerformanceHintManager::class.java) ?: return null
            // The target is a planning hint for roughly one decode step; it is not a guaranteed frame budget.
            val session = manager.createHintSession(intArrayOf(Process.myTid()), TARGET_STEP_NANOS) ?: return null
            BestEffortInferenceHintSession(
                report = session::reportActualWorkDuration,
                closeSession = session::close,
                reportFailure = { error -> Log.w(TAG, "Performance hint reports disabled for this generation", error) },
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "Performance hint session unavailable", error)
            null
        }
    }

    private companion object {
        const val TAG = "MobieHints"
        const val TARGET_STEP_NANOS = 50_000_000L
    }
}
