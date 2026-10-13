package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeComputeSettings
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
        cpuThreads = CpuThreadPolicy.clamp(options.cpuThreads, coreCount),
        contextTokens = options.contextTokens?.takeIf { it in capabilities?.contextChoices().orEmpty() },
        thinkingEnabled = options.thinkingEnabled.takeIf { capabilities?.supportsThinking == true },
        thinkingTokenBudget = options.thinkingTokenBudget.takeIf { capabilities?.supportsThinking == true },
        speculativeDecoding = options.speculativeDecoding.takeIf { capabilities?.supportsSpeculativeDecoding == true },
        artifactIdentity = "",
    )

    fun sampler(settings: SamplerSettings): SamplerSettings = SamplerSettings(
        temperature = settings.temperature.takeIf { it.isFinite() }?.coerceIn(0.0, 2.0) ?: 0.7,
        topK = settings.topK.coerceIn(1, 64),
        topP = settings.topP.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1.0) ?: 0.95,
        seed = settings.seed,
    )

    fun canApply(state: RuntimeState, downloaded: Boolean): Boolean = downloaded &&
        state !in setOf(RuntimeState.GENERATING, RuntimeState.LOADING, RuntimeState.STOPPING)

    fun needsApply(options: ComputeOptions, sampler: SamplerSettings, applied: ComputeOptions?, appliedSampler: SamplerSettings?): Boolean =
        options.copy(artifactIdentity = "") != (applied ?: ComputeOptions()).copy(artifactIdentity = "") ||
            sampler != (appliedSampler ?: SamplerSettings())
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
