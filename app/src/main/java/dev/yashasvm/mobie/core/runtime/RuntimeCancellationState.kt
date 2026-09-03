package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException

/**
 * Tracks whether LiteRT cancellation has already reached JNI successfully for the current
 * generation/lifecycle transition. This avoids calling cancelProcess() a second time while the
 * cancelled generation unwinds, while still allowing one cleanup retry after a recoverable JNI
 * failure.
 *
 * attempt() serializes the state check and JNI call so explicit Stop/lifecycle cancellation cannot
 * race generation-unwind cleanup into issuing duplicate native cancellation requests.
 */
internal class RuntimeCancellationState {
    @Volatile
    private var outcome = Outcome.NOT_REQUESTED

    @Synchronized
    fun reset() {
        outcome = Outcome.NOT_REQUESTED
    }

    fun shouldAttemptCleanup(): Boolean =
        outcome == Outcome.NOT_REQUESTED || outcome == Outcome.RECOVERABLE_FAILURE

    @Synchronized
    fun attempt(block: () -> Unit): Exception? {
        if (outcome == Outcome.SUCCEEDED || outcome == Outcome.NON_RECOVERABLE_FAILURE) return null

        return try {
            block()
            outcome = Outcome.SUCCEEDED
            null
        } catch (error: CancellationException) {
            outcome = Outcome.NON_RECOVERABLE_FAILURE
            throw error
        } catch (error: Exception) {
            outcome = Outcome.RECOVERABLE_FAILURE
            error
        } catch (error: Throwable) {
            outcome = Outcome.NON_RECOVERABLE_FAILURE
            throw error
        }
    }

    private enum class Outcome {
        NOT_REQUESTED,
        SUCCEEDED,
        RECOVERABLE_FAILURE,
        NON_RECOVERABLE_FAILURE,
    }
}
