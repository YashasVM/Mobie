package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeConversationReconfigurationPolicy
import dev.yashasvm.mobie.core.runtime.SamplerSettings

/**
 * What the loaded native engine was started (or last reconfigured) with. [options] carry the
 * artifact identity and are normalized for [coreCount] the same way the runtime normalizes them,
 * so comparisons here agree with the runtime's own admission. MobieViewModel only writes it under
 * its runtime lifecycle lock and clears it whenever the engine's state becomes uncertain.
 */
internal data class LoadedEngine(
    val modelId: String,
    val path: String,
    val vision: Boolean,
    val options: ComputeOptions,
    val sampler: SamplerSettings,
)

/**
 * Proofs for keeping initialized weights instead of reloading. Every check fails closed: a missing
 * record, a different artifact, file or vision mode, or any unclassified setting needs a full load.
 */
internal object LoadedEnginePolicy {
    /** Normalizes [options] as the runtime does for this load, with the exact artifact identity. */
    fun requested(options: ComputeOptions, artifactIdentity: String, coreCount: Int): ComputeOptions =
        options.copy(artifactIdentity = artifactIdentity).forCoreCount(coreCount)

    /**
     * Only conversation settings and the sampler differ, so Apply can replace the conversation and
     * keep the weights. A refused runtime reconfigure still breaks the conversation, so this must
     * never admit what [RuntimeConversationReconfigurationPolicy] would refuse.
     */
    fun canReconfigure(
        loaded: LoadedEngine?,
        modelId: String,
        path: String,
        vision: Boolean,
        requested: ComputeOptions,
        runtimeState: RuntimeState,
        activeCompute: ActiveCompute?,
    ): Boolean = runtimeState == RuntimeState.READY && activeCompute != null && loaded != null &&
        loaded.sameEngineFile(modelId, path, vision) &&
        RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded.options, requested)

    /** Exactly the loaded settings, so only the history needs replacing. */
    fun canReuse(
        loaded: LoadedEngine?,
        modelId: String,
        path: String,
        vision: Boolean,
        requested: ComputeOptions,
        sampler: SamplerSettings,
    ): Boolean = loaded != null && loaded.sameEngineFile(modelId, path, vision) &&
        loaded.options == requested && loaded.sampler == sampler

    private fun LoadedEngine.sameEngineFile(modelId: String, path: String, vision: Boolean): Boolean =
        options.artifactIdentity.isNotBlank() && this.modelId == modelId && this.path == path && this.vision == vision
}

/** The loaded engine kept its weights and now answers with [options] and [sampler]. */
internal fun MobieUiState.afterReconfigured(
    options: ComputeOptions,
    sampler: SamplerSettings,
    activeCompute: ActiveCompute?,
    imageInputReady: Boolean,
): MobieUiState = copy(
    runtimeState = RuntimeState.READY,
    activeCompute = activeCompute,
    imageInputReady = imageInputReady,
    appliedComputeOptions = options.copy(artifactIdentity = ""),
    appliedSampler = sampler,
    error = null,
)

/**
 * A failed conversation replacement leaves no usable conversation, and the engine was unloaded to
 * recover. Nothing is applied any more, so chat cannot send until the model is opened again.
 */
internal fun MobieUiState.afterFailedReconfigure(reason: String?, unloadFailure: String?): MobieUiState = copy(
    runtimeState = RuntimeState.ERROR,
    activeCompute = null,
    imageInputReady = false,
    appliedComputeOptions = null,
    appliedSampler = null,
    error = reconfigureFailureMessage(reason, unloadFailure),
)

internal fun reconfigureFailureMessage(reason: String?, unloadFailure: String?): String = buildString {
    append("Couldn't apply these settings")
    reason?.takeIf { it.isNotBlank() }?.let { append(": ").append(it.trimEnd('.')) }
    append(". ")
    append(unloadFailure?.takeIf { it.isNotBlank() }
        ?: "The model was unloaded. Go back and open this chat again to reload it.")
}
