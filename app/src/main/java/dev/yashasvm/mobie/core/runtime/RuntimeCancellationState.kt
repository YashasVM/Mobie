package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException

/**
 * Tracks native cancellation state for the current generation. Cancellation requests are ignored
 * while no generation is active, preventing idle load/reset/unload operations from making an
 * irrelevant cancelProcess() JNI call. attempt() still serializes the state check and JNI call so
 * concurrent Stop/lifecycle/unwind paths cannot issue duplicate successful cancellation requests.
 */
internal class RuntimeCancellationState {
    @Volatile
    private var generationActive = false

    @Volatile
    private var outcome = Outcome.NOT_REQUESTED

    @Synchronized
    fun beginGeneration() {
        generationActive = true
        outcome = Outcome.NOT_REQUESTED
    }

    @Synchronized
    fun endGeneration() {
        generationActive = false
        outcome = Outcome.NOT_REQUESTED
    }

    @Synchronized
    fun reset() {
        generationActive = false
        outcome = Outcome.NOT_REQUESTED
    }

    fun isGenerationActive(): Boolean = generationActive

    fun shouldAttemptCleanup(): Boolean =
        generationActive && (outcome == Outcome.NOT_REQUESTED || outcome == Outcome.RECOVERABLE_FAILURE)

    @Synchronized
    fun attempt(block: () -> Unit): Exception? {
        if (!generationActive) return null
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
