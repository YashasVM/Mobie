package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException

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

internal inline fun <T : Any> replaceRuntimeResourceBeforeClosingPrevious(
    previous: T?,
    createReplacement: () -> T,
    installReplacement: (T) -> Unit,
    closePrevious: (T) -> Unit,
) {
    val replacement = createReplacement()
    installReplacement(replacement)
    previous?.let(closePrevious)
}

internal fun rethrowNonRecoverableRuntimeFailure(error: Throwable) {
    if (error is CancellationException) throw error
    rethrowFatalRuntimeFailure(error)
}
