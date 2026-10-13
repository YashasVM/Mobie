package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

internal inline fun <T> recoverableRuntimeResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal fun rethrowFatalRuntimeFailure(error: Throwable) {
    if (error !is Exception) throw error
}

internal inline fun runRuntimeCleanupUnlessFatal(error: Throwable, cleanup: () -> Unit) {
    rethrowFatalRuntimeFailure(error)
    try {
        cleanup()
    } catch (cleanupError: Throwable) {
        rethrowFatalRuntimeFailure(cleanupError)
        error.addSuppressed(cleanupError)
    }
}

internal inline fun runRuntimeCleanupPreservingPrimary(primary: Throwable, cleanup: () -> Unit) {
    rethrowFatalRuntimeFailure(primary)
    try {
        cleanup()
    } catch (cleanupError: Throwable) {
        rethrowFatalRuntimeFailure(cleanupError)
        primary.addSuppressed(cleanupError)
    }
}

internal inline fun captureRecoverableRuntimeFailure(block: () -> Unit): Exception? = try {
    block()
    null
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    error
}

internal inline fun rethrowAfterRuntimeCleanup(primary: Exception, cleanup: () -> Unit): Nothing {
    try {
        cleanup()
    } catch (cleanupError: Throwable) {
        rethrowFatalRuntimeFailure(cleanupError)
        primary.addSuppressed(cleanupError as Exception)
    }
    throw primary
}

internal fun runAllRuntimeCleanup(vararg cleanup: () -> Unit) {
    var firstFailure: Exception? = null
    for (action in cleanup) {
        try {
            action()
        } catch (error: Throwable) {
            rethrowFatalRuntimeFailure(error)
            val recoverable = error as Exception
            val previous = firstFailure
            if (previous == null) {
                firstFailure = recoverable
            } else {
                previous.addSuppressed(recoverable)
            }
        }
    }
    firstFailure?.let { throw it }
}

/** Release the old native session before allocating another; failed creation leaves no session. */
internal inline fun <T : Any> replaceRuntimeResourceAfterClosingPrevious(
    previous: T?,
    clearPrevious: () -> Unit,
    createReplacement: () -> T,
    installReplacement: (T) -> Unit,
    closePrevious: (T) -> Unit,
) {
    // Keep ownership if close fails, so no replacement can overlap an unreleased session.
    previous?.let(closePrevious)
    clearPrevious()
    val replacement = createReplacement()
    installReplacement(replacement)
}

/**
 * Reload order: admission (validation, memory, storage, thermal) runs while the previous runtime is
 * still loaded. [release] runs only after admission succeeds, so a refused reload leaves the previous
 * runtime loaded and usable. A refusal thrown by [admit] never reaches [release].
 */
internal inline fun <A> admitThenReleaseRuntime(admit: () -> A, release: () -> Unit): A {
    val admitted = admit()
    release()
    return admitted
}

/**
 * Only truly fatal VM states propagate. OutOfMemoryError, linkage failures and other Errors thrown
 * by native code are reported as a runtime failure that needs a reload, so they cannot crash the app.
 */
internal fun isFatalRuntimeFailure(error: Throwable): Boolean =
    error is VirtualMachineError && error !is OutOfMemoryError

/** Plain-language event for a failure that escaped the native call. Errors leave native state uncertain. */
internal fun runtimeFailureEvent(error: Throwable): InferenceEvent.Error =
    InferenceEvent.Error(error.message ?: "Inference failed", requiresReload = error !is Exception)

internal fun rethrowNonRecoverableRuntimeFailure(error: Throwable) {
    if (error is CancellationException) throw error
    if (isFatalRuntimeFailure(error)) throw error
}

/**
 * A generation flow whose failures become one terminal [InferenceEvent.Error]. The failure is reported
 * only by the outer `catch`, which never handles a failure thrown by the downstream collector, so a
 * collector that throws during an emit (or cancels) reaches its caller unchanged. The [body] must not
 * emit from a `catch` of its own; rethrow there and let this flow report it.
 */
internal fun generationEventFlow(body: suspend FlowCollector<InferenceEvent>.() -> Unit): Flow<InferenceEvent> =
    flow(body).catch { error ->
        rethrowNonRecoverableRuntimeFailure(error)
        emit(runtimeFailureEvent(error))
    }
