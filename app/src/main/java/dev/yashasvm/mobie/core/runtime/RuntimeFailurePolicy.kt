package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException

internal inline fun <T> recoverableRuntimeResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
