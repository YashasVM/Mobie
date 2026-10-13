package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.device.CpuThreadPolicy

/** Activation overrides require exact-artifact device testing. AUTO preserves SDK defaults. */
enum class ActivationType { AUTO, FP16, FP32 }

/** Controls for the next load. Thinking and visible tokens share the SDK's output cap. */
data class ComputeOptions(
    val backend: ComputeBackend = ComputeBackend.AUTO,
    /** Null uses topology-aware CPU selection. */
    val cpuThreads: Int? = null,
    val artifactIdentity: String = "",
    /** Null uses the existing memory policy. Explicit sizes require artifact metadata. */
    val contextTokens: Int? = null,
    val maxOutputTokens: Int = 1_024,
    /** Null preserves the artifact default or configureThinking override. */
    val thinkingEnabled: Boolean? = null,
    /** Null preserves unlimited reasoning within the shared output cap. */
    val thinkingTokenBudget: Int? = null,
    val activationType: ActivationType = ActivationType.AUTO,
    /** True requires an artifact that advertises a drafter. */
    val speculativeDecoding: Boolean? = null,
    val prefillPrefaceOnInit: Boolean = false,
) {
    init {
        require(cpuThreads == null || cpuThreads in 1..8) { "CPU threads must be Auto or 1 to 8." }
        require(contextTokens == null || contextTokens in 1_024..4_096) { "Context must be 1024 to 4096 tokens." }
        require(maxOutputTokens in 1..1_024) { "Output limit must be 1 to 1024 tokens." }
        require(thinkingTokenBudget == null || thinkingTokenBudget in 0..1_024) { "Thinking budget must be 0 to 1024 tokens or model default." }
    }

    fun forCoreCount(coreCount: Int): ComputeOptions = copy(cpuThreads = CpuThreadPolicy.clamp(cpuThreads, coreCount))
}

/** Metadata describes allowed experiments, not proof of native compatibility. */
data class ComputeCapabilities(
    val maxContextTokens: Int,
    val dynamicContext: Boolean,
    val supportsThinking: Boolean,
    val supportsSpeculativeDecoding: Boolean,
) {
    fun contextChoices(): List<Int> = if (dynamicContext) listOf(1_024, 2_048, 4_096).filter { it <= maxContextTokens }
        else listOf(maxContextTokens).filter { it in 1_024..4_096 }

    internal fun resolveContext(requested: Int?, automatic: Int): Int {
        require(maxContextTokens > 0) { "The artifact has no known context limit." }
        if (!dynamicContext) {
            require(requested == null || requested == maxContextTokens) { "This artifact requires a fixed $maxContextTokens-token context." }
            return maxContextTokens
        }
        require(requested == null || requested <= maxContextTokens) { "The requested context exceeds the artifact's $maxContextTokens-token limit." }
        return requested ?: minOf(automatic, maxContextTokens)
    }

    internal fun validate(options: ComputeOptions) {
        require(options.speculativeDecoding != true || supportsSpeculativeDecoding) { "This artifact does not support speculative decoding." }
        require((options.thinkingEnabled != true && options.thinkingTokenBudget == null) || supportsThinking) { "This artifact does not support a thinking budget." }
    }
}
