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
    cleanup()
}

internal fun rethrowNonRecoverableRuntimeFailure(error: Throwable) {
    if (error is CancellationException) throw error
    rethrowFatalRuntimeFailure(error)
}
