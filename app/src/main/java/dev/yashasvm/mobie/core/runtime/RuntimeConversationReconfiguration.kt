package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Conservative engine-reuse admission. Compare requested load settings, not an Auto fallback's
 * selected backend. A missing artifact identity cannot prove that the initialized weights match.
 * Speculative decoding and prefill are conversation settings in the pinned LiteRT SDK.
 */
object RuntimeConversationReconfigurationPolicy {
    fun canRetainEngine(loaded: ComputeOptions, requested: ComputeOptions): Boolean =
        loaded.artifactIdentity.isNotBlank() &&
            // Only these known conversation controls may differ. New ComputeOptions fields
            // conservatively require a reload until explicitly classified here.
            loaded.copy(
                maxOutputTokens = requested.maxOutputTokens,
                thinkingEnabled = requested.thinkingEnabled,
                thinkingTokenBudget = requested.thinkingTokenBudget,
                speculativeDecoding = requested.speculativeDecoding,
                prefillPrefaceOnInit = requested.prefillPrefaceOnInit,
            ) == requested
}

/**
 * Stop and await generation before admitting a replacement. Unlike a history-only reset, failed
 * cancellation must not install new settings. Invalidate before admission so every failure leaves
 * the old conversation unavailable, including a close failure that retains native ownership.
 * [invalidateConversation] must be idempotent and thread-safe: it also runs before waiting, so
 * caller cancellation or a native cancellation exception cannot reopen the previous conversation.
 */
internal suspend fun reconfigureRuntimeConversation(
    cancellationState: RuntimeCancellationState,
    generation: Mutex,
    lifecycle: Mutex,
    requestCancellation: () -> Exception?,
    invalidateConversation: () -> Unit,
    replaceConversation: () -> Unit,
): Result<Unit> {
    val transition = cancellationState.beginLifecycleTransition()
    try {
        invalidateConversation()
        val cancellationFailure = requestCancellation()
        return generation.withLock {
            lifecycle.withLock {
                recoverableRuntimeResult {
                    invalidateConversation()
                    if (cancellationFailure != null) throw cancellationFailure
                    replaceConversation()
                }
            }
        }
    } finally {
        cancellationState.endLifecycleTransition(transition)
    }
}
