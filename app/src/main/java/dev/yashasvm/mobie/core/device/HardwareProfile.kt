package dev.yashasvm.mobie.core.device

/** Static hardware facts. Null means Android or the kernel did not expose the value. */
data class HardwareProfile(
    val socManufacturer: String?,
    val socModel: String?,
    val cpuCoreCount: Int,
    val cpuClusters: List<CpuCluster>,
    val gpu: GpuHardware?,
    /** Presence of a vendor library is a hint, not proof that the app can load OpenCL. */
    val openClLibraryPresent: Boolean,
    val totalRamBytes: Long,
    val detectionNotes: List<String> = emptyList(),
)

data class CpuCluster(val coreIds: List<Int>, val maxFrequencyKhz: Int?)

data class GpuHardware(val renderer: String, val vendor: String?) {
    val isSoftwareRenderer: Boolean
        get() = listOf("swiftshader", "llvmpipe", "softpipe", "software", "swrast", "lavapipe")
            .any { renderer.contains(it, ignoreCase = true) }
}

/** Parsing is separate from Android/file access so restricted and older kernels can be tested. */
object HardwareProfileParser {
    fun reportedName(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) && it != "0"
    }

    fun socModel(platformModel: String?, cpuInfo: String): String? =
        reportedName(platformModel) ?: cpuInfo.lineSequence().mapNotNull { line ->
            val pieces = line.split(':', limit = 2)
            if (pieces.size == 2 && pieces[0].trim().equals("Hardware", ignoreCase = true)) {
                reportedName(pieces[1])
            } else null
        }.firstOrNull()

    /** Kernel CPU lists may use either whitespace (related_cpus) or ranges (possible). */
    fun cpuIds(contents: String): List<Int> {
        val ids = sortedSetOf<Int>()
        for (item in contents.trim().split(Regex("[\\s,]+"))) {
            if (item.isEmpty()) continue
            val bounds = item.split('-')
            val start = bounds.first().toIntOrNull() ?: return emptyList()
            val end = if (bounds.size == 1) start else bounds.getOrNull(1)?.toIntOrNull() ?: return emptyList()
            // Bound ranges before expansion. A corrupt sysfs file must not allocate without limit.
            if (bounds.size > 2 || start !in 0..255 || end !in start..255) return emptyList()
            ids.addAll(start..end)
        }
        return ids.toList()
    }

    fun maxFrequencyKhz(contents: String?): Int? =
        contents?.trim()?.toIntOrNull()?.takeIf { it in 100_000..10_000_000 }

    /** Prefer frequency-policy membership; otherwise group equal maximum frequencies. */
    fun cpuClusters(
        coreIds: List<Int>,
        frequencyContents: Map<Int, String>,
        relatedCpuContents: Map<Int, String>,
    ): List<CpuCluster> {
        val cores = coreIds.distinct().sorted()
        val frequencies = frequencyContents.mapValues { maxFrequencyKhz(it.value) }
        return cores.groupBy { core ->
            val related = relatedCpuContents[core]?.let(::cpuIds).orEmpty().filter { it in cores }
            if (core in related) "policy:${related.joinToString(",")}" else "frequency:${frequencies[core]}"
        }.values.map { members ->
            CpuCluster(members, members.mapNotNull { frequencies[it] }.maxOrNull())
        }.sortedBy { it.coreIds.first() }
    }
}
