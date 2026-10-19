package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.BackendAdmission
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.BuildConfig
import java.security.MessageDigest
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeComputeSettings
import dev.yashasvm.mobie.core.runtime.RuntimeConversationReconfigurationPolicy
import dev.yashasvm.mobie.core.runtime.SamplerSettings

/** Persisted options are preferences, never evidence that a configuration works on a phone. */
internal object AdvancedComputeSettingsCodec {
    fun decode(values: Map<String, String?>, coreCount: Int): ComputeOptions {
        fun integer(key: String) = values[key]?.toIntOrNull()
        fun boolean(key: String) = values[key]?.toBooleanStrictOrNull()
        return ComputeOptions(
            backend = values["selected_backend"]?.let { saved -> ComputeBackend.entries.find { it.name == saved } }
                ?.takeUnless { it == ComputeBackend.NPU } ?: ComputeBackend.AUTO,
            cpuThreads = CpuThreadPolicy.clamp(integer("cpu_threads")?.takeUnless { it == 0 }, coreCount),
            contextTokens = integer("context_tokens")?.takeIf { it in 1_024..4_096 },
            maxOutputTokens = (integer("max_output_tokens") ?: 1_024).coerceIn(1, 1_024),
            thinkingEnabled = boolean("thinking_enabled"),
            thinkingTokenBudget = integer("thinking_budget")?.coerceIn(0, 1_024),
            activationType = values["activation_type"]?.let { saved -> ActivationType.entries.find { it.name == saved } }
                ?: ActivationType.AUTO,
            speculativeDecoding = boolean("speculative_decoding"),
            prefillPrefaceOnInit = boolean("prefill_preface") ?: false,
        )
    }

    fun decodeCapabilities(values: Map<String, String?>, identity: String, runtimeVersion: String): ComputeCapabilities? {
        if (values["capability_identity"] != capabilityIdentity(identity, runtimeVersion)) return null
        val maximum = values["capability_max_context"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val dynamic = values["capability_dynamic"]?.toBooleanStrictOrNull() ?: return null
        val thinking = values["capability_thinking"]?.toBooleanStrictOrNull() ?: return null
        val drafter = values["capability_drafter"]?.toBooleanStrictOrNull() ?: return null
        return ComputeCapabilities(maximum, dynamic, thinking, drafter)
    }

    fun encodeCapabilities(capabilities: ComputeCapabilities, identity: String, runtimeVersion: String): Map<String, String> = mapOf(
        "capability_identity" to capabilityIdentity(identity, runtimeVersion),
        "capability_max_context" to capabilities.maxContextTokens.toString(),
        "capability_dynamic" to capabilities.dynamicContext.toString(),
        "capability_thinking" to capabilities.supportsThinking.toString(),
        "capability_drafter" to capabilities.supportsSpeculativeDecoding.toString(),
    )

    private fun capabilityIdentity(identity: String, runtimeVersion: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$runtimeVersion\u0000$identity".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encode(options: ComputeOptions): Map<String, String?> = mapOf(
        "context_tokens" to options.contextTokens?.toString(),
        "max_output_tokens" to options.maxOutputTokens.toString(),
        "thinking_enabled" to options.thinkingEnabled?.toString(),
        "thinking_budget" to options.thinkingTokenBudget?.toString(),
        "activation_type" to options.activationType.name,
        "speculative_decoding" to options.speculativeDecoding?.toString(),
        "prefill_preface" to options.prefillPrefaceOnInit.toString(),
    )
}

internal object AdvancedComputePolicy {
    /** Unknown metadata never enables a context, reasoning or drafter override. */
    fun forArtifact(options: ComputeOptions, coreCount: Int, capabilities: ComputeCapabilities?): ComputeOptions = options.copy(
        backend = options.backend.takeUnless { it == ComputeBackend.NPU } ?: ComputeBackend.AUTO,
        cpuThreads = CpuThreadPolicy.clamp(options.cpuThreads, coreCount),
        contextTokens = options.contextTokens?.takeIf { it in capabilities?.contextChoices().orEmpty() },
        thinkingEnabled = options.thinkingEnabled.takeIf { capabilities?.supportsThinking == true },
        // A budget only applies to explicit On; with Model default or Off it would change nothing truthful.
        thinkingTokenBudget = options.thinkingTokenBudget.takeIf { capabilities?.supportsThinking == true && options.thinkingEnabled == true },
        speculativeDecoding = options.speculativeDecoding.takeIf { capabilities?.supportsSpeculativeDecoding == true },
        artifactIdentity = "",
    )

    /**
     * The explicit context the next load requests for this artifact, or null for Auto. Only metadata
     * that permits the override enables it, so the verdict and the load gate use the same value.
     */
    fun contextOverride(options: ComputeOptions, capabilities: ComputeCapabilities?): Int? =
        options.contextTokens?.takeIf { it in capabilities?.contextChoices().orEmpty() }

    /** Change the RAM estimate only when this exact artifact's metadata permits the override. */
    fun compatibilityArtifact(artifact: ModelArtifact?, options: ComputeOptions, capabilities: ComputeCapabilities?): ModelArtifact? {
        val context = contextOverride(options, capabilities) ?: return artifact
        return artifact?.copy(contextWindowTokens = context)
    }

    /**
     * What each backend choice would run and its RAM, through the same resolver as the load gate.
     * Empty until the device and an artifact are known.
     */
    fun backendAdmissions(
        device: DeviceProfile?,
        artifact: ModelArtifact?,
        visionEncoderLoaded: Boolean,
        options: ComputeOptions,
        capabilities: ComputeCapabilities?,
        resolver: CompatibilityResolver,
    ): Map<ComputeBackend, BackendAdmission> {
        val target = device ?: return emptyMap()
        val sized = compatibilityArtifact(artifact, options, capabilities) ?: return emptyMap()
        val context = contextOverride(options, capabilities)
        return listOf(ComputeBackend.AUTO, ComputeBackend.CPU, ComputeBackend.GPU).associateWith { backend ->
            resolver.resolveBackend(sized, target, visionEncoderLoaded, backend, contextTokens = context)
        }
    }

    fun sampler(settings: SamplerSettings): SamplerSettings = SamplerSettings(
        temperature = settings.temperature.takeIf { it.isFinite() }?.coerceIn(0.0, 2.0) ?: 0.7,
        topK = settings.topK.coerceIn(1, 64),
        topP = settings.topP.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1.0) ?: 0.95,
        seed = settings.seed,
    )

    fun canApply(state: RuntimeState, downloaded: Boolean): Boolean = downloaded &&
        state !in setOf(RuntimeState.GENERATING, RuntimeState.LOADING, RuntimeState.STOPPING)

    /** Pending changes only exist against a loaded engine. With nothing loaded, saved settings wait for the next load. */
    fun needsApply(options: ComputeOptions, sampler: SamplerSettings, applied: ComputeOptions?, appliedSampler: SamplerSettings?): Boolean {
        if (applied == null) return false
        return options.copy(artifactIdentity = "") != applied.copy(artifactIdentity = "") ||
            sampler != (appliedSampler ?: SamplerSettings())
    }

    /**
     * False when only conversation settings or the sampler differ from the loaded engine, so Apply
     * can keep the model loaded. Both sides describe the selected artifact; the ViewModel and
     * runtime still verify its exact identity and fall back to a full reload when unproven.
     */
    fun changesNeedReload(options: ComputeOptions, applied: ComputeOptions?): Boolean = applied == null ||
        !RuntimeConversationReconfigurationPolicy.canRetainEngine(
            applied.copy(artifactIdentity = SELECTED_ARTIFACT), options.copy(artifactIdentity = SELECTED_ARTIFACT))

    private const val SELECTED_ARTIFACT = "selected"
}

/**
 * Plain copy for a reload that was refused or failed. When the previous engine is still running,
 * the message names it, so the user knows what is answering.
 */
internal fun keptRunningMessage(keptEngine: ActiveCompute?, reason: String): String =
    keptEngine?.let { "Kept running on ${it.backend.name}. $reason" } ?: reason

/**
 * The state after a refused reload: the runtime fields go back to what they were before Apply, so
 * chat and runtime state agree with the engine that is still loaded (or with nothing loaded).
 */
internal fun MobieUiState.afterRefusedReload(previous: MobieUiState, keptEngine: ActiveCompute?, reason: String): MobieUiState = copy(
    chatting = previous.chatting,
    runtimeState = previous.runtimeState,
    activeCompute = previous.activeCompute,
    stats = previous.stats,
    telemetry = previous.telemetry,
    error = keptRunningMessage(keptEngine, reason),
)

/** A failed replacement must never enable prompts against a different conversation. */
internal fun MobieUiState.afterFailedLoad(
    previousEngine: ActiveCompute?,
    preserveLoadedEngineOnFailure: Boolean,
    reason: String,
): MobieUiState {
    val keptEngine = previousEngine.takeIf { preserveLoadedEngineOnFailure }
    return if (keptEngine != null) {
        copy(runtimeState = RuntimeState.READY, activeCompute = keptEngine,
            error = keptRunningMessage(keptEngine, reason))
    } else {
        copy(runtimeState = RuntimeState.ERROR, activeCompute = null, imageInputReady = false,
            appliedComputeOptions = null, appliedSampler = null, error = reason)
    }
}

/** Uses the existing runtime_compute preferences, including its typed backend/thread keys. */
internal fun RuntimeComputeSettings.readAdvancedOptions(coreCount: Int): ComputeOptions {
    val values = preferences.all.mapValues { it.value?.toString() }
    return AdvancedComputeSettingsCodec.decode(values, coreCount).also { writeAdvancedOptions(it, coreCount) }
}

internal fun RuntimeComputeSettings.writeAdvancedOptions(options: ComputeOptions, coreCount: Int) {
    val editor = preferences.edit()
        .putString("selected_backend", options.backend.name)
        .putInt("cpu_threads", CpuThreadPolicy.clamp(options.cpuThreads, coreCount) ?: 0)
    AdvancedComputeSettingsCodec.encode(options).forEach { (key, value) ->
        if (value == null) editor.remove(key) else editor.putString(key, value)
    }
    editor.apply()
}

/** One bounded metadata cache entry, qualified only as header metadata for this exact artifact. */
internal fun RuntimeComputeSettings.readCachedCapabilities(identity: String): ComputeCapabilities? =
    AdvancedComputeSettingsCodec.decodeCapabilities(preferences.all.mapValues { it.value?.toString() }, identity, BuildConfig.LITERT_LM_VERSION)

internal fun RuntimeComputeSettings.writeCachedCapabilities(identity: String, capabilities: ComputeCapabilities) {
    val editor = preferences.edit()
    AdvancedComputeSettingsCodec.encodeCapabilities(capabilities, identity, BuildConfig.LITERT_LM_VERSION).forEach { (key, value) -> editor.putString(key, value) }
    editor.apply()
}
