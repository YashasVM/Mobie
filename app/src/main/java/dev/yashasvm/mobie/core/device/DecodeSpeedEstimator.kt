package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.runtime.ComputeBackend

enum class DecodeSpeedSource { ESTIMATED, MEASURED }
enum class DecodeSpeedConfidence { STANDARD, LOW }

data class DecodeSpeed(
    val lowTokensPerSecond: Double,
    val highTokensPerSecond: Double,
    val source: DecodeSpeedSource,
    val sampleCount: Int = 0,
    val confidence: DecodeSpeedConfidence = DecodeSpeedConfidence.STANDARD,
)

data class DecodeSpeedPrediction(val speed: DecodeSpeed?, val unavailableReason: String? = null)

/**
 * A decode roofline heuristic, not a benchmark or a compatibility verdict.
 *
 * One decoded token reads approximately one model's weights. Package size is our weight proxy;
 * assume 1.2–1.5 passes to allow for KV reads, packing and other traffic. Vision assets and long
 * contexts can make that proxy inaccurate. Small models can instead be compute bound.
 *
 * Device classes below assume shared mobile LPDDR with 32–64 data bits and roughly 3.2–10.6
 * GT/s, giving broad theoretical bandwidths of 12–85 GB/s. CPU maximum clock, cluster count and
 * physical RAM identify only a likely class; they do NOT measure DRAM speed. CPU decode is
 * assumed to sustain 5–6% of that bandwidth, GPU 20–22%. Those effective fractions include
 * dequantization, kernel and dispatch overhead; they are not measured DRAM utilization.
 * They are calibrated against supplied dynamic INT4 LiteRT-LM 0.18 decode measurements of
 * about 6–8 CPU and 30 GPU tokens/s for a 344,671,744-byte artifact on an 8-core, 12-GiB,
 * 3.39-GHz device. There is no device/model throughput lookup. Missing clocks widen both
 * bandwidth and efficiency bounds and lower confidence. Heat, context and quantization can
 * change the result. Real SDK decode measurements always replace the heuristic.
 */
object DecodeSpeedEstimator {
    fun predict(
        hardware: HardwareProfile?,
        artifactSizeBytes: Long,
        backend: ComputeBackend,
        measured: DecodeSpeed? = null,
    ): DecodeSpeedPrediction {
        if (backend != ComputeBackend.CPU && backend != ComputeBackend.GPU) {
            return DecodeSpeedPrediction(null, "Choose CPU or GPU to compare speeds.")
        }
        if (measured?.source == DecodeSpeedSource.MEASURED &&
            measured.lowTokensPerSecond.isFinite() && measured.lowTokensPerSecond > 0 &&
            measured.highTokensPerSecond.isFinite() && measured.highTokensPerSecond >= measured.lowTokensPerSecond
        ) return DecodeSpeedPrediction(measured)
        if (artifactSizeBytes <= 0) return DecodeSpeedPrediction(null, "Model size is unknown.")
        if (hardware == null) return DecodeSpeedPrediction(null, "Reading this phone's hardware.")
        if (backend == ComputeBackend.GPU) {
            if (hardware.gpu == null) return DecodeSpeedPrediction(null, "GPU information is unavailable.")
            if (hardware.gpu.isSoftwareRenderer) return DecodeSpeedPrediction(null, "Android reports a software GPU.")
            if (!hardware.openClLibraryPresent) return DecodeSpeedPrediction(null, "OpenCL driver was not found. Run GPU to measure support and speed.")
        }
        val bandwidth = bandwidthGbPerSecond(hardware)
        val lowerConfidence = hardware.cpuClusters.none { it.maxFrequencyKhz != null } ||
            hardware.totalRamBytes <= 0 || hardware.cpuCoreCount <= 0
        val efficiency = when {
            backend == ComputeBackend.CPU && lowerConfidence -> 0.035 to 0.08
            backend == ComputeBackend.CPU -> 0.05 to 0.06
            lowerConfidence -> 0.12 to 0.30
            else -> 0.20 to 0.22
        }
        return DecodeSpeedPrediction(DecodeSpeed(
            lowTokensPerSecond = bandwidth.first * 1_000_000_000.0 * efficiency.first / (artifactSizeBytes * 1.5),
            highTokensPerSecond = bandwidth.second * 1_000_000_000.0 * efficiency.second / (artifactSizeBytes * 1.2),
            source = DecodeSpeedSource.ESTIMATED,
            confidence = if (lowerConfidence) DecodeSpeedConfidence.LOW else DecodeSpeedConfidence.STANDARD,
        ))
    }

    private fun bandwidthGbPerSecond(hardware: HardwareProfile): Pair<Double, Double> {
        val maxKhz = hardware.cpuClusters.mapNotNull { it.maxFrequencyKhz }.maxOrNull()
        if (maxKhz == null) {
            // A reported SoC plus RAM/core count supports a mobile-memory class, not a clock guess.
            val socReported = HardwareProfileParser.reportedName(hardware.socModel) != null
            return when {
                hardware.cpuCoreCount >= 8 && hardware.totalRamBytes >= 6L * GIB && socReported -> 25.0 to 80.0
                hardware.cpuCoreCount >= 6 && hardware.totalRamBytes >= 4L * GIB -> 12.0 to 65.0
                else -> 8.0 to 35.0
            }
        }
        // This is class inference from observed topology, never a model/phone throughput table.
        // Missing physical RAM or very few cores widen the low-end assumption conservatively.
        return when {
            hardware.cpuCoreCount >= 8 && maxKhz >= 3_000_000 && hardware.totalRamBytes >= 6L * GIB -> 55.0 to 80.0
            hardware.cpuCoreCount >= 6 && maxKhz >= 2_400_000 && hardware.totalRamBytes >= 4L * GIB -> 25.0 to 60.0
            else -> 12.0 to 35.0
        }
    }

    private const val GIB = 1024L * 1024 * 1024
}
