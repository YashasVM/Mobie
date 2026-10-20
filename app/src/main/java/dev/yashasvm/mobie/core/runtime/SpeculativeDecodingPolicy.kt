package dev.yashasvm.mobie.core.runtime

/**
 * When to run LiteRT-LM's multi-token-prediction (MTP) drafter. The drafter proposes several
 * tokens and the main model scores them in one step. A drafted token is kept only while it equals
 * the main model's own sample at that position, so every emitted token is the main model's
 * choice: the drafter changes speed, not the reply's distribution. (Greedy text can still differ
 * by floating-point rounding between batched verification and single-step decode.)
 *
 * Auto turns it on for GPU only. Google's Gemma 4 E2B model card (S26 Ultra, LiteRT-LM) shows
 * GPU decode rising from 51.5 to 66.5-91.7 tokens/s with the drafter, while CPU ranged from
 * 36.3 to 47.5 against a 40.7 baseline: slower for code. Explicit On or Off is always honored.
 * Google AI Edge Gallery leaves the drafter off unless the user enables it.
 */
internal object SpeculativeDecodingPolicy {
    /**
     * The engine-level request for one backend attempt: true builds the drafter during engine
     * initialization, so the first reply does not pay to load it. Null keeps the SDK default
     * (no drafter) and is used whenever the artifact has none, because true would fail there.
     */
    fun engineRequest(requested: Boolean?, artifactHasDrafter: Boolean, backend: ComputeBackend): Boolean? = when {
        !artifactHasDrafter -> null
        requested != null -> requested
        backend == ComputeBackend.GPU -> true
        else -> null
    }

    fun buildsDrafter(requested: Boolean?, artifactHasDrafter: Boolean, backend: ComputeBackend): Boolean =
        engineRequest(requested, artifactHasDrafter, backend) == true

    /**
     * Whether the conversation decodes with the drafter. A conversation-level request overrides
     * the engine; null inherits whatever the engine built.
     */
    fun conversationUses(requested: Boolean?, engineBuiltDrafter: Boolean): Boolean = requested ?: engineBuiltDrafter

    /**
     * Only an Auto request may be retried without the drafter. An explicit On that fails must
     * surface its error instead of silently measuring something else.
     */
    fun mayRetryWithoutDrafter(requested: Boolean?, engineRequest: Boolean?): Boolean =
        requested == null && engineRequest == true

    /**
     * RAM to reserve for a built drafter. The same model card reports CPU memory up to 220 MB
     * above baseline with the drafter (GPU: within 26 MB), so reserve 256 MiB on any backend.
     * A conservative admission allowance, not a measurement on this device.
     */
    const val DRAFTER_RAM_ALLOWANCE_BYTES: Long = 256L * 1024L * 1024L
}
