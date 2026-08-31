package dev.yashasvm.mobie.core.model

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
) {
    val supportsVision: Boolean get() = type == ModelType.VISION

    val bestArtifact: ModelArtifact?
        get() = artifacts
            .asSequence()
            .filter { it.executionTarget == ArtifactExecutionTarget.GENERIC }
            .minWithOrNull(
                compareBy<ModelArtifact>(
                    { if (it.format == ModelFormat.LITERT_LM) 0 else 1 },
                    { quantizationRank(it.quantization) },
                    { if (it.sizeBytes > 0) 0 else 1 },
                    { it.sizeBytes.takeIf { size -> size > 0 } ?: Long.MAX_VALUE },
                ),
            )

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

internal fun inferArtifactExecutionTarget(fileName: String): ArtifactExecutionTarget {
    val normalized = fileName.lowercase()
    val hardwareSpecific =
        Regex("(?:^|[._-])mediatek(?:[._-]|$)").containsMatchIn(normalized) ||
            Regex("(?:^|[._-])qualcomm(?:[._-]|$)").containsMatchIn(normalized) ||
            Regex("(?:^|[._-])npu(?:[._-]|$)").containsMatchIn(normalized)
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
