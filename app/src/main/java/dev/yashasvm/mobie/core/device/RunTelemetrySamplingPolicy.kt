package dev.yashasvm.mobie.core.device

/** Pure elapsed-time gate, shared by the expensive PSS and battery-state reads. */
internal object RunTelemetrySamplingPolicy {
    const val REFRESH_INTERVAL_MS = 1_000L

    fun shouldRefresh(previousAtMs: Long?, nowMs: Long): Boolean =
        // An explicit unset value avoids overflowing `now - Long.MIN_VALUE` on first use.
        previousAtMs == null || previousAtMs < 0 || nowMs < previousAtMs ||
            nowMs - previousAtMs >= REFRESH_INTERVAL_MS
}
