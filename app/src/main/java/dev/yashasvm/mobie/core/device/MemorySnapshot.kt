package dev.yashasvm.mobie.core.device

import android.app.ActivityManager
import android.os.Process
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** Values from /proc/meminfo, in bytes. Optional kernel fields remain null when absent. */
data class ProcMemInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val cachedBytes: Long,
    val buffersBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val activeFileBytes: Long?,
    val inactiveFileBytes: Long?,
    val shmemBytes: Long?,
    val mlockedBytes: Long?,
)

/** Strictly accepts kB fields; malformed or incomplete snapshots trigger the Android fallback. */
object ProcMemInfoParser {
    fun parse(contents: String): ProcMemInfo? {
        val fields = mutableMapOf<String, Long>()
        for (line in contents.lineSequence()) {
            val match = FIELD.matchEntire(line.trim()) ?: continue
            val kb = match.groupValues[2].toLongOrNull() ?: return null
            if (kb > Long.MAX_VALUE / 1024) return null
            fields[match.groupValues[1]] = kb * 1024
        }
        fun required(name: String) = fields[name]
        val total = required("MemTotal") ?: return null
        val available = required("MemAvailable") ?: return null
        val cached = required("Cached") ?: return null
        val buffers = required("Buffers") ?: return null
        val swapTotal = required("SwapTotal") ?: return null
        val swapFree = required("SwapFree") ?: return null
        if (total <= 0 || available !in 0..total || swapFree !in 0..swapTotal) return null
        return ProcMemInfo(total, available, cached, buffers, swapTotal, swapFree,
            fields["Active(file)"], fields["Inactive(file)"], fields["Shmem"], fields["Mlocked"])
    }

    private val FIELD = Regex("^([A-Za-z_()]+):\\s*([0-9]+)\\s+kB$")
}

data class MemorySnapshot(
    val totalBytes: Long,
    val availableNowBytes: Long,
    val availableAfterReclaimBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val appPssBytes: Long,
    val thresholdBytes: Long,
    val isLowMemory: Boolean,
    val proc: ProcMemInfo?,
)

/** Conservative admission headroom; never treats all file cache or compressed swap as free RAM. */
object ReclaimableMemory {
    fun afterReclaim(proc: ProcMemInfo?, total: Long, now: Long, threshold: Long, lowMemory: Boolean): Long {
        val reserve = max(max(threshold, total / 10), if (lowMemory) threshold + 256L * MIB else 0L)
        val ceiling = (total - reserve).coerceAtLeast(0)
        if (proc == null) return min(now, ceiling)
        // MemAvailable already includes much of the page cache. Only count cache above that
        // reported amount, then allow 25% of it (at most 1.5 GiB) for uncertain reclaimability.
        val fileCache = (proc.cachedBytes + proc.buffersBytes - (proc.shmemBytes ?: 0L) -
            (proc.mlockedBytes ?: 0L)).coerceAtLeast(0)
        val coolFile = proc.inactiveFileBytes?.let { inactive ->
            inactive + (proc.activeFileBytes ?: 0L) / 4
        } ?: fileCache
        val extraFile = min((min(fileCache, coolFile) - proc.availableBytes).coerceAtLeast(0) / 4, 1536L * MIB)
        // zRAM is compressed and consumes physical pages. Credit only 25% of free swap, capped
        // at 512 MiB. This is pressure headroom, not a substitute for anonymous working memory.
        val extraSwap = min(proc.swapFreeBytes / 4, 512L * MIB)
        return min(ceiling, (now + extraFile + extraSwap).coerceAtLeast(now))
    }

    private const val MIB = 1024L * 1024L
}

/**
 * Reads /proc/meminfo (a kernel memory view of a few KB, not storage I/O) plus ActivityManager.
 * ActivityManager remains authoritative for low-memory state. When procfs is unreadable or
 * malformed, every value falls back to ActivityManager and no reclaim headroom is invented.
 */
class MemorySnapshotReader(private val manager: ActivityManager) {
    fun read(): MemorySnapshot {
        val android = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        // Fallback is deliberate: SELinux or OEM builds may deny /proc/meminfo on some devices.
        val proc = try {
            ProcMemInfoParser.parse(File("/proc/meminfo").readText())
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        val total = proc?.totalBytes ?: android.totalMem
        val now = proc?.availableBytes ?: android.availMem
        val pss = manager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
            ?.totalPss?.toLong()?.times(1024) ?: 0L
        return MemorySnapshot(total, now,
            ReclaimableMemory.afterReclaim(proc, total, now, android.threshold, android.lowMemory),
            proc?.swapTotalBytes ?: 0L, proc?.swapFreeBytes ?: 0L, pss,
            android.threshold, android.lowMemory, proc)
    }
}
