package dev.yashasvm.mobie.core.device

/** The MVP offers at most eight workers, bounded by the cores Android actually reports. */
object CpuThreadPolicy {
    fun limit(coreCount: Int): Int = coreCount.coerceIn(1, 8)

    /** Null preserves automatic selection; restored and submitted explicit values are bounded. */
    fun clamp(threads: Int?, coreCount: Int): Int? = threads?.coerceIn(1, limit(coreCount))
}
