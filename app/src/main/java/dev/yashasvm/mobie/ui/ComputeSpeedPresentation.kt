package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.device.BackendAdmission
import dev.yashasvm.mobie.core.device.DecodeSpeed
import dev.yashasvm.mobie.core.device.DecodeSpeedConfidence
import dev.yashasvm.mobie.core.device.DecodeSpeedSource
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.model.CompatibilityResult
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

internal fun hardwareSummary(hardware: HardwareProfile?): String {
    if (hardware == null) return "Reading this phone's hardware…"
    // These are aliases for reported chip IDs, never guesses from a phone's marketing name.
    val chip = when (hardware.socModel?.uppercase(Locale.US)) {
        "SM8650", "SM8650-AC", "SM8650-AB" -> "Snapdragon 8 Gen 3"
        else -> hardware.socModel ?: hardware.socManufacturer?.let { "$it chip" } ?: "Chip not reported"
    }
    val gpu = hardware.gpu?.renderer?.replace("(TM)", "", ignoreCase = true)?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.let { if (it.endsWith("GPU", ignoreCase = true)) it else "$it GPU" } ?: "GPU not reported"
    val cores = "${hardware.cpuCoreCount} CPU ${if (hardware.cpuCoreCount == 1) "core" else "cores"}"
    return "$chip · $gpu · $cores"
}

internal fun hardwareDetails(hardware: HardwareProfile): String {
    val clusters = hardware.cpuClusters.filter { it.maxFrequencyKhz != null }.joinToString(" + ") {
        String.format(Locale.US, "%d × %.1f GHz", it.coreIds.size, checkNotNull(it.maxFrequencyKhz) / 1_000_000.0)
    }
    return listOfNotNull(clusters.takeIf { it.isNotEmpty() },
        hardware.totalRamBytes.takeIf { it > 0 }?.let { "${formatBytes(it)} RAM" }).joinToString(" · ")
}

/** Round outward for estimates. Measured ranges are the min/max of actual recent decode runs. */
internal fun decodeSpeedLabel(speed: DecodeSpeed): String {
    fun rounded(value: Double, low: Boolean): String {
        val scale = if (value < 1) 10.0 else 1.0
        val rounded = if (speed.source == DecodeSpeedSource.ESTIMATED) {
            (if (low) floor(value * scale) else ceil(value * scale)) / scale
        } else kotlin.math.round(value * scale) / scale
        return if (rounded < 1) String.format(Locale.US, "%.1f", rounded) else rounded.toLong().toString()
    }
    if (speed.highTokensPerSecond < 0.1) return "<0.1 tokens/s"
    val low = rounded(speed.lowTokensPerSecond, low = true)
    val high = rounded(speed.highTokensPerSecond, low = false)
    return if (low == high) "≈$low tokens/s" else "$low–$high tokens/s"
}

internal fun decodeSpeedSourceLabel(speed: DecodeSpeed): String = when (speed.source) {
    DecodeSpeedSource.ESTIMATED -> if (speed.confidence == DecodeSpeedConfidence.LOW) "estimate · lower confidence" else "estimate"
    DecodeSpeedSource.MEASURED -> "measured on this phone · ${if (speed.sampleCount == 1) "last run" else "${speed.sampleCount} recent runs"}"
}

/** One backend row's RAM, e.g. `needs ~3.6 GB RAM · Free memory first`; null when the size is unknown. */
internal fun backendRamLabel(result: CompatibilityResult): String? =
    result.estimatedRamBytes.takeIf { it > 0 }?.let { "needs ~${formatBytes(it)} RAM · ${statusLabel(result.status)}" }

/** Auto's pick, e.g. `Auto uses GPU · needs ~3.6 GB RAM`. */
internal fun autoPickLabel(admission: BackendAdmission): String =
    "Auto uses ${admission.backend.name}" +
        admission.result.estimatedRamBytes.takeIf { it > 0 }?.let { " · needs ~${formatBytes(it)} RAM" }.orEmpty()
