package dev.yashasvm.mobie.core.model

enum class ModelFormat { GGUF, LITERT_LM, UNKNOWN }
enum class ModelType { TEXT_GENERATION, EMBEDDING, VISION, AUDIO, UNKNOWN }
enum class Compatibility { COMPATIBLE, WARNING, INCOMPATIBLE, CONVERSION_REQUIRED }

data class ModelArtifact(
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
    val format: ModelFormat,
    val quantization: String? = null,
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
        get() = artifacts.minWithOrNull(
            compareBy<ModelArtifact>(
                { if (it.format == ModelFormat.LITERT_LM) 0 else 1 },
                { quantizationRank(it.quantization) },
                { if (it.sizeBytes > 0) 0 else 1 },
                { it.sizeBytes.takeIf { size -> size > 0 } ?: Long.MAX_VALUE },
            ),
        )

    private fun quantizationRank(quantization: String?): Int = when (quantization?.uppercase()) {
        null -> 20
        "Q4_K_M" -> 0
        "Q4_K_S" -> 1
        "Q5_K_M" -> 2
        "Q5_K_S" -> 3
        "Q4_0" -> 4
        "Q5_0" -> 5
        "Q6_K" -> 6
        "Q8_0" -> 7
        else -> 10
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
