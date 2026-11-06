package dev.yashasvm.mobie.core.runtime

/**
 * When to run LiteRT-LM's multi-token-prediction (MTP) drafter. The drafter proposes several
 * tokens and the main model checks them in one step, keeping a drafted token only while it equals
 * the main model's own choice.
 *
 * In the pinned LiteRT-LM 0.18.0 that choice is greedy: both drafter and verifier use a fixed
 * top-k 1 sampler (`runtime/executor/llm_litert_mtp_drafter.cc`, `CreateGreedySampler`). While
 * the drafter runs, temperature, top-k and top-p shape only the first token after the prompt, and
 * replies follow the model's most likely words. Gallery behaves the same way when its switch is on.
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
     * The conversation-level request, always explicit when the artifact has a drafter. The SDK's
     * null means "use the drafter if one is loaded", and a drafter loaded lazily for an earlier
     * explicit On stays loaded, so null would let Auto on CPU keep using it. True also loads the
     * drafter lazily when the engine did not build one (for example LiteRT-LM's dynamic executor).
     */
    fun conversationRequest(requested: Boolean?, artifactHasDrafter: Boolean, backend: ComputeBackend): Boolean? = when {
        !artifactHasDrafter -> null
        requested != null -> requested
        else -> backend == ComputeBackend.GPU
    }

    /** Whether the conversation asks LiteRT-LM to decode with the drafter. */
    fun conversationUses(requested: Boolean?, artifactHasDrafter: Boolean, backend: ComputeBackend): Boolean =
        conversationRequest(requested, artifactHasDrafter, backend) == true

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

/**
 * LiteRT-LM reads `ExperimentalFlags` (a process-wide singleton) inside `Engine.initialize()`.
 * Every engine initialization in the process goes through this lock so one load's drafter or
 * benchmark request can never leak into another engine, and the previous values are restored.
 * Accessors are injected so the ordering can be tested without loading the SDK on the host JVM.
 */
internal object LiteRtEngineInitialization {
    private val lock = Any()

    fun <T> withFlags(
        speculation: Boolean?,
        benchmark: Boolean,
        readSpeculation: () -> Boolean?,
        writeSpeculation: (Boolean?) -> Unit,
        readBenchmark: () -> Boolean,
        writeBenchmark: (Boolean) -> Unit,
        initialize: () -> T,
    ): T = synchronized(lock) {
        val previousSpeculation = readSpeculation()
        val previousBenchmark = readBenchmark()
        writeSpeculation(speculation)
        writeBenchmark(benchmark)
        try {
            initialize()
        } finally {
            writeSpeculation(previousSpeculation)
            writeBenchmark(previousBenchmark)
        }
    }
}
