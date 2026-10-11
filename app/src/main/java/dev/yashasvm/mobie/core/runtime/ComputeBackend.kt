package dev.yashasvm.mobie.core.runtime

/** Compute target requested for a LiteRT-LM engine. */
enum class ComputeBackend {
    /**
     * Runtime chooses. Memory admission gates Auto on CPU, the cheapest backend that always ends
     * the attempt list, and admits GPU separately before each GPU attempt.
     */
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
