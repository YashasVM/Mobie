package dev.yashasvm.mobie.core.runtime

/**
 * Keep CPU inference deliberately conservative on heterogeneous Android SoCs.
 *
 * The real-model CI benchmark showed a >2x decode/prefill gain moving LiteRT-LM from its
 * runtime-default CPU backend to two explicit worker threads. Until representative physical-phone
 * data justifies consuming more cores, cap production inference at two threads to avoid turning a
 * throughput optimization into unnecessary thermal/battery pressure.
 */
internal object LiteRtCpuThreadPolicy {
    private const val MAX_INFERENCE_THREADS = 2

    fun threadCount(availableProcessors: Int = Runtime.getRuntime().availableProcessors()): Int =
        availableProcessors.coerceAtLeast(1).coerceAtMost(MAX_INFERENCE_THREADS)
}
