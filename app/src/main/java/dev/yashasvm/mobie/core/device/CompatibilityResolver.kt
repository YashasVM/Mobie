package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.estimateLiteRtRuntimeMemory
import kotlin.math.max

class CompatibilityResolver {
    fun selectBestArtifact(model: AiModel, device: DeviceProfile): ModelArtifact? = model.artifacts
        .asSequence()
        .filter {
            it.format == ModelFormat.LITERT_LM &&
                it.executionTarget == ArtifactExecutionTarget.GENERIC
        }
        .map { artifact -> artifact to resolve(artifact, device) }
        .filter { (_, result) ->
            result.status == Compatibility.COMPATIBLE || result.status == Compatibility.WARNING
        }
        .minWithOrNull(
            compareBy<Pair<ModelArtifact, CompatibilityResult>>(
                { (_, result) -> if (result.status == Compatibility.COMPATIBLE) 0 else 1 },
                { (_, result) -> result.estimatedRamBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
                { (_, result) -> result.requiredStorageBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
                { (artifact, _) -> artifact.sizeBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
            ),
        )
        ?.first

    fun resolve(artifact: ModelArtifact?, device: DeviceProfile): CompatibilityResult {
        if (artifact == null || artifact.format != ModelFormat.LITERT_LM) {
            return CompatibilityResult(
                Compatibility.CONVERSION_REQUIRED,
                "Mobie v1 runs published LiteRT-LM artifacts only.",
                0,
            )
        }
        if (artifact.executionTarget != ArtifactExecutionTarget.GENERIC) {
            return CompatibilityResult(
                Compatibility.INCOMPATIBLE,
                "This package targets device-specific acceleration that Mobie's current runtime does not support yet.",
                0,
            )
        }
        if (device.supportedAbis.none { it == "arm64-v8a" || it == "x86_64" }) {
            return CompatibilityResult(Compatibility.INCOMPATIBLE, "LiteRT-LM requires a supported 64-bit device.", 0)
        }
        if (artifact.sizeBytes <= 0) {
            return CompatibilityResult(
                Compatibility.WARNING,
                "The publisher did not provide an artifact size. Check storage before downloading.",
                0,
            )
        }

        val memoryEstimate = requireNotNull(estimateLiteRtRuntimeMemory(artifact))
        val estimatedRam = memoryEstimate.estimatedRamBytes
        val requiredStorage = requiredStorageBytes(artifact.sizeBytes)
        val memoryReserve = max(device.lowMemoryThresholdBytes, device.totalRamBytes / 20)
        val availableFraction = if (device.isLowRamDevice) 0.75 else 0.85
        val totalFraction = if (device.isLowRamDevice) 0.70 else 0.80
        val safeAvailableRam = ((device.availableRamBytes - memoryReserve).coerceAtLeast(0) * availableFraction).toLong()
        fun result(status: Compatibility, reason: String) = CompatibilityResult(
            status = status,
            reason = reason,
            estimatedRamBytes = estimatedRam,
            modelWeightsBytes = artifact.sizeBytes,
            runtimeOverheadBytes = memoryEstimate.runtimeOverheadBytes,
            kvCacheBytes = memoryEstimate.kvCacheBytes,
            contextWindowTokens = memoryEstimate.contextWindowTokens,
            requiredStorageBytes = requiredStorage,
        )
        if (requiredStorage > device.availableStorageBytes) {
            return result(
                Compatibility.INCOMPATIBLE,
                "Not enough free storage for the model, download headroom, and LiteRT's first-load optimized cache.",
            )
        }
        if (estimatedRam > device.totalRamBytes * totalFraction) {
            val reason = if (device.isLowRamDevice) {
                "Android classifies this as a low-RAM device; the model leaves too little memory for a stable local runtime."
            } else {
                "The model is too large for this device's RAM."
            }
            return result(Compatibility.INCOMPATIBLE, reason)
        }
        if (device.isLowMemory) {
            return result(
                Compatibility.WARNING,
                "Android reports active memory pressure. Free memory before loading this model.",
            )
        }
        if (device.thermalStatus >= THERMAL_STATUS_SEVERE) {
            return result(
                Compatibility.WARNING,
                "Android reports severe thermal pressure. Let the device cool before loading a local model.",
            )
        }
        if (estimatedRam > safeAvailableRam) {
            val reason = if (device.isLowRamDevice) {
                "The model may fit, but this low-RAM device needs extra Android memory headroom. Close other apps first."
            } else {
                "The model may fit, but current free RAM is too close to Android's low-memory limit. Close other apps first."
            }
            return result(Compatibility.WARNING, reason)
        }
        return result(Compatibility.COMPATIBLE, "Expected to fit this device with Android memory headroom reserved.")
    }

    /**
     * LiteRT-LM optimizes model weights for the current device on first load and stores those
     * artifacts in the configured cache directory. Reserve persistent cache space in addition to
     * the model and download margin so a download is not recommended when first inference is
     * likely to exhaust storage. The cache allowance is intentionally conservative until Mobie can
     * measure per-model cache growth on representative physical devices.
     */
    internal fun requiredStorageBytes(modelWeightsBytes: Long): Long {
        val downloadHeadroom = max(modelWeightsBytes / 20, 64L * MIB)
        val optimizedCacheHeadroom = max(modelWeightsBytes * 3 / 10, 256L * MIB)
        return modelWeightsBytes + downloadHeadroom + optimizedCacheHeadroom
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val THERMAL_STATUS_SEVERE = 3
    }
}
