package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException

/**
 * Tracks native cancellation state for the current generation and lifecycle-transition epochs.
 * Cancellation requests are ignored while no generation is active, preventing idle
 * load/reset/unload operations from making an irrelevant cancelProcess() JNI call. attempt() still
 * serializes the state check and JNI call so concurrent Stop/lifecycle/unwind paths cannot issue
 * duplicate successful cancellation requests.
 *
 * Generation permits make lifecycle admission monotonic: a generation captured before or during a
 * load/reset/unload transition stays stale even if that transition clears cancelRequested before
 * the generation eventually acquires the generation mutex.
 */
internal class RuntimeCancellationState {
    @Volatile
    private var generationActive = false

    @Volatile
    private var outcome = Outcome.NOT_REQUESTED

    private var lifecycleEpoch = 0L
    private val activeLifecycleTransitions = mutableSetOf<Long>()

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

    /** Resets generation cancellation state without invalidating an enclosing lifecycle transition. */
    @Synchronized
    fun reset() {
        generationActive = false
        outcome = Outcome.NOT_REQUESTED
    }

    @Synchronized
    fun beginLifecycleTransition(): Long {
        lifecycleEpoch += 1
        val token = lifecycleEpoch
        activeLifecycleTransitions += token
        return token
    }

    @Synchronized
    fun endLifecycleTransition(token: Long) {
        activeLifecycleTransitions -= token
    }

    @Synchronized
    fun captureGenerationPermit(): GenerationPermit = GenerationPermit(
        lifecycleEpoch = lifecycleEpoch,
        capturedDuringLifecycleTransition = activeLifecycleTransitions.isNotEmpty(),
    )

    @Synchronized
    fun isGenerationPermitValid(permit: GenerationPermit): Boolean =
        !permit.capturedDuringLifecycleTransition &&
            activeLifecycleTransitions.isEmpty() &&
            permit.lifecycleEpoch == lifecycleEpoch

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

    internal data class GenerationPermit(
        val lifecycleEpoch: Long,
        val capturedDuringLifecycleTransition: Boolean,
    )

    private enum class Outcome {
        NOT_REQUESTED,
        SUCCEEDED,
        RECOVERABLE_FAILURE,
        NON_RECOVERABLE_FAILURE,
    }
}
