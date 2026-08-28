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
)

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
    val bestArtifact: ModelArtifact?
        get() = artifacts.minByOrNull { it.sizeBytes.takeIf { size -> size > 0 } ?: Long.MAX_VALUE }
}

data class DeviceProfile(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val availableStorageBytes: Long,
    val supportedAbis: List<String>,
    val sdkInt: Int,
)

data class CompatibilityResult(
    val status: Compatibility,
    val reason: String,
    val estimatedRamBytes: Long,
)

enum class ConversionStatus { REQUESTED, REVIEWING, CONVERTING, TESTING, READY, UNSUPPORTED }
