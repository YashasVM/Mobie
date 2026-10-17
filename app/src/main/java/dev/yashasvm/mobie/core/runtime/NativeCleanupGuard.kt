package dev.yashasvm.mobie.core.runtime

/** A failed JNI close leaves pointer ownership unknown. Only a process restart can recover safely. */
internal class NativeCleanupGuard {
    @Volatile private var failure: Throwable? = null

    fun ensureSafe() {
        failure?.let { throw NativeCleanupException(it) }
    }

    fun markUncertain(error: Throwable) {
        if (failure == null) failure = error
    }

    fun close(block: () -> Unit) {
        ensureSafe()
        try {
            block()
        } catch (error: Throwable) {
            markUncertain(error)
            if (isFatalRuntimeFailure(error)) throw error
            throw NativeCleanupException(error)
        }
    }
}

internal class NativeCleanupException(cause: Throwable) : IllegalStateException(
    "Restart Mobie before loading or deleting this model. Native cleanup did not finish safely.", cause,
)
