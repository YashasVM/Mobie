package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cancel and await generation before releasing its conversation or allocating a new session. */
internal suspend fun resetRuntimeConversation(
    cancellationState: RuntimeCancellationState,
    generation: Mutex,
    lifecycle: Mutex,
    requestCancellation: () -> Exception?,
    replaceConversation: () -> Unit,
): Result<Unit> {
    val transition = cancellationState.beginLifecycleTransition()
    try {
        val cancellationFailure = requestCancellation()
        return generation.withLock {
            lifecycle.withLock {
                recoverableRuntimeResult {
                    if (cancellationFailure != null) {
                        rethrowAfterRuntimeCleanup(cancellationFailure, replaceConversation)
                    }
                    replaceConversation()
                }
            }
        }
    } finally {
        cancellationState.endLifecycleTransition(transition)
    }
}
