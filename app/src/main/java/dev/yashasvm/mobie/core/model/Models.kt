package dev.yashasvm.mobie.core.model

import kotlin.math.max

enum class ModelFormat { GGUF, LITERT_LM, UNKNOWN }
enum class ModelType { TEXT_GENERATION, EMBEDDING, VISION, AUDIO, UNKNOWN }
enum class Compatibility { COMPATIBLE, WARNING, INCOMPATIBLE, CONVERSION_REQUIRED }
enum class ArtifactExecutionTarget { GENERIC, HARDWARE_SPECIFIC }

data class ModelArtifact(
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
    val format: ModelFormat,
    val quantization: String? = null,
    val executionTarget: ArtifactExecutionTarget = inferArtifactExecutionTarget(fileName),
    val contextWindowTokens: Int? = inferArtifactContextWindow(fileName),
) {
    val runtimeLabel: String
        get() = when (format) {
            ModelFormat.GGUF -> "llama.cpp"
            ModelFormat.LITERT_LM -> "LiteRT-LM"
            ModelFormat.UNKNOWN -> "Unsupported"
        }
}

data class AiModel(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val downloads: Long = 0,
    val gated: Boolean = false,
    val license: String? = null,
    val type: ModelType = ModelType.TEXT_GENERATION,
    val artifacts: List<ModelArtifact> = emptyList(),
    val preferredArtifactFileName: String? = null,
) {
    val supportsVision: Boolean get() = type == ModelType.VISION

    val bestArtifact: ModelArtifact?
        get() = artifacts
            .asSequence()
            .filter { it.executionTarget == ArtifactExecutionTarget.GENERIC }
            .minWithOrNull(
                compareBy<ModelArtifact>(
                    { if (it.fileName == preferredArtifactFileName) 0 else 1 },
                    { if (it.format == ModelFormat.LITERT_LM) 0 else 1 },
                    { if (estimateLiteRtRuntimeMemory(it) != null) 0 else 1 },
                    { estimateLiteRtRuntimeMemory(it)?.estimatedRamBytes ?: Long.MAX_VALUE },
                    { quantizationRank(it.quantization) },
                    { if (it.sizeBytes > 0) 0 else 1 },
                    { it.sizeBytes.takeIf { size -> size > 0 } ?: Long.MAX_VALUE },
                ),
            )

    fun preferArtifact(artifact: ModelArtifact?): AiModel {
        val preferred = artifact?.fileName
        return if (preferredArtifactFileName == preferred) this else copy(preferredArtifactFileName = preferred)
    }

    private fun quantizationRank(quantization: String?): Int {
        val normalized = quantization?.uppercase() ?: return 20
        return when {
            normalized == "Q4_K_M" || normalized == "INT4" -> 0
            normalized == "Q4_K_S" -> 1
            normalized.startsWith("Q4_") || normalized == "Q4" -> 2
            normalized == "Q5_K_M" -> 3
            normalized == "Q5_K_S" -> 4
            normalized == "Q5_0" || normalized.startsWith("Q5_") -> 5
            normalized == "Q4_0" -> 6
            normalized == "Q6_K" || normalized.startsWith("Q6_") -> 7
            normalized == "Q8_0" || normalized.startsWith("Q8_") || normalized == "INT8" -> 8
            else -> 10
        }
    }
}

internal data class LiteRtRuntimeMemoryEstimate(
    val estimatedRamBytes: Long,
    val runtimeOverheadBytes: Long,
    val kvCacheBytes: Long,
    val contextWindowTokens: Int,
)

internal fun estimateLiteRtRuntimeMemory(artifact: ModelArtifact): LiteRtRuntimeMemoryEstimate? {
    if (artifact.format != ModelFormat.LITERT_LM || artifact.sizeBytes <= 0) return null
    val contextWindow = artifact.contextWindowTokens ?: DEFAULT_CONTEXT_TOKENS
    val kvCache = max(
        MIN_KV_CACHE_BYTES,
        DEFAULT_KV_CACHE_BYTES * contextWindow / DEFAULT_CONTEXT_TOKENS,
    )
    val runtimeOverhead = max((artifact.sizeBytes * 0.4).toLong(), 512L * MIB)
    return LiteRtRuntimeMemoryEstimate(
        estimatedRamBytes = artifact.sizeBytes + runtimeOverhead + kvCache,
        runtimeOverheadBytes = runtimeOverhead,
        kvCacheBytes = kvCache,
        contextWindowTokens = contextWindow,
    )
}

internal fun inferArtifactQuantization(fileName: String): String? {
    val ggufStyle = Regex("(?i)(Q[2-8](?:_[A-Z0-9]+)?)").find(fileName)?.value?.uppercase()
    if (ggufStyle != null) return ggufStyle

    val intBits = Regex("(?i)(?:^|[^A-Z0-9])(?:MIXED[_-]?)?INT([248])(?:[^A-Z0-9]|$)")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
    if (intBits != null) return "INT$intBits"

    val weightIntBits = Regex("(?i)(?:^|[^A-Z0-9])WI([248])(?:B[0-9]+)?(?:[^A-Z0-9]|$)")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
    return weightIntBits?.let { "INT$it" }
}

internal fun inferArtifactContextWindow(fileName: String): Int? {
    val normalized = fileName.lowercase()

    val explicit = Regex("(?:^|[._-])(?:e?kv|ctx|context)[_-]?(\\d{3,6})(?:[._-]|$)")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf(::isPlausibleContextWindow)
    if (explicit != null) return explicit

    // LiteRT community artifacts also commonly encode cache/context as c1024, c32k, c64k, etc.
    // Missing these is dangerous: treating c64k as the 4096-token fallback underestimates KV-cache
    // memory by roughly 16x and can make an otherwise unsafe model look runnable on a phone.
    val compactK = Regex("(?:^|[._-])c(\\d{1,3})k(?:[._-]|$)")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { it * 1024 }
        ?.takeIf(::isPlausibleContextWindow)
    if (compactK != null) return compactK

    return Regex("(?:^|[._-])c(\\d{3,6})(?:[._-]|$)")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf(::isPlausibleContextWindow)
}

private fun isPlausibleContextWindow(tokens: Int): Boolean = tokens in 128..131_072

internal fun inferArtifactExecutionTarget(fileName: String): ArtifactExecutionTarget {
    val normalized = fileName.lowercase()
    val hardwareSpecificTokens = listOf(
        "mediatek",
        "qualcomm",
        "npu",
        "gpu",
        "opencl",
        "adreno",
        "qnn",
        "htp",
        "hexagon",
        "google_tensor",
        "google-tensor",
    )
    val hardwareSpecific = hardwareSpecificTokens.any { token ->
        Regex("(?:^|[._-])${Regex.escape(token)}(?:[._-]|$)").containsMatchIn(normalized)
    }
    return if (hardwareSpecific) {
        ArtifactExecutionTarget.HARDWARE_SPECIFIC
    } else {
        ArtifactExecutionTarget.GENERIC
    }
}

data class DeviceProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val availableStorageBytes: Long,
    val supportedAbis: List<String>,
    val sdkInt: Int,
    val releaseVersion: String = "",
    val lowMemoryThresholdBytes: Long = 0,
    val isLowMemory: Boolean = false,
    val isLowRamDevice: Boolean = false,
    val manufacturer: String = "",
    val model: String = "",
    val socManufacturer: String = "",
    val socModel: String = "",
    val mediaPerformanceClass: Int = 0,
    val thermalStatus: Int = 0,
)

data class CompatibilityResult(
    val status: Compatibility,
    val reason: String,
    val estimatedRamBytes: Long,
    val modelWeightsBytes: Long = 0,
    val runtimeOverheadBytes: Long = 0,
    val kvCacheBytes: Long = 0,
    val contextWindowTokens: Int = 0,
    val requiredStorageBytes: Long = 0,
)

enum class ConversionStatus { REQUESTED, REVIEWING, CONVERTING, TESTING, READY, UNSUPPORTED }

private const val MIB = 1024L * 1024L
private const val DEFAULT_CONTEXT_TOKENS = 4_096
private const val DEFAULT_KV_CACHE_BYTES = 256L * MIB
private const val MIN_KV_CACHE_BYTES = 64L * MIB