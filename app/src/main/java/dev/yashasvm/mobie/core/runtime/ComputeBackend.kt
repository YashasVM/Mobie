package dev.yashasvm.mobie.core.runtime

/** Compute target requested for a LiteRT-LM engine. */
enum class ComputeBackend {
    /** Runtime chooses; memory admission assumes the most expensive (private-copy) case. */
    AUTO,
    CPU,
    GPU,
    NPU;

    /**
     * CPU maps LiteRT weights directly. GPU, NPU, and AUTO may keep an additional private copy of
     * the weights, so admission counts the full artifact size as anonymous memory for them.
     */
    val privateWeightCopy: Boolean get() = this != CPU
}
