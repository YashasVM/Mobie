package dev.yashasvm.mobie.core.runtime

import java.io.File
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import java.io.IOException
import kotlin.math.max

/** Selects the faster CPU cluster without assuming a particular vendor's core numbering. */
internal object LiteRtCpuThreadPolicy {
    private const val AUTO_MAX_THREADS = 4
    private val frequencyFile = Regex("cpu(\\d+)/cpufreq/cpuinfo_max_freq$")

    /**
     * Returns the CPU worker count for text inference.
     *
     * An explicit [override] is bounded to 1..8. Otherwise the count is the number of performance
     * cores (cores at or above the largest frequency gap, which separates little cores), clamped to
     * 2..4 and never above the online processor count. Decode is bound by memory bandwidth, so
     * threads past four mostly add heat and contention with the UI thread; users can still pick
     * up to eight to measure their own device. If frequencies are unreadable or show no
     * clear cluster split, it falls back to a conservative 2 threads.
     */
    fun threadCount(
        frequenciesKhz: Map<Int, Int> = readMaxFrequencies(),
        availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
        override: Int? = null,
    ): Int {
        if (override != null) return requireNotNull(CpuThreadPolicy.clamp(override, availableProcessors))
        val online = availableProcessors.coerceAtLeast(1)
        val measured = frequenciesKhz.filterKeys { it < online }.values.filter { it > 0 }
        if (measured.size < 2) return minOf(2, online)
        val levels = measured.distinct().sorted()
        if (levels.size < 2) return minOf(2, online)
        // A gap of at least 15% identifies the little cluster. Use the largest gap so
        // prime, big and middle cores all count as performance cores on 1+3+2+2 SoCs.
        val gap = levels.zipWithNext().maxByOrNull { (low, high) -> high.toDouble() / low }!!
        if (gap.second.toDouble() / gap.first < 1.15) return minOf(2, online)
        return max(2, measured.count { it >= gap.second }).coerceAtMost(minOf(AUTO_MAX_THREADS, online))
    }

    fun parseMaxFrequencies(entries: Map<String, String>): Map<Int, Int> = buildMap {
        entries.forEach { (path, contents) ->
            val cpu = frequencyFile.find(path)?.groupValues?.get(1)?.toIntOrNull()
            val frequency = contents.trim().toIntOrNull()
            if (cpu != null && frequency != null && frequency > 0) put(cpu, frequency)
        }
    }

    private fun readMaxFrequencies(): Map<Int, Int> {
        val root = File("/sys/devices/system/cpu")
        val entries = root.listFiles { file -> file.isDirectory && file.name.matches(Regex("cpu\\d+")) }
            ?: return emptyMap()
        return parseMaxFrequencies(entries.mapNotNull { cpu ->
            val file = File(cpu, "cpufreq/cpuinfo_max_freq")
            try {
                file.relativeTo(root).invariantSeparatorsPath to file.readText()
            } catch (_: IOException) {
                // Some kernels hide cpufreq for offline or restricted cores. Missing data falls back to the conservative default.
                null
            }
        }.toMap())
    }
}
