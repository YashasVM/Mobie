package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import kotlin.math.max

class CompatibilityResolver {
    fun resolve(artifact: ModelArtifact?, device: DeviceProfile): CompatibilityResult {
        if (artifact == null || artifact.format != ModelFormat.LITERT_LM) {
            return CompatibilityResult(
                Compatibility.CONVERSION_REQUIRED,
                "Mobie v1 runs published LiteRT-LM artifacts only.",
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

        // ponytail: conservative fixed KV allowance until published LiteRT metadata exposes architecture dimensions.
        val kvCache = 256L * MIB
        val runtimeOverhead = max((artifact.sizeBytes * 0.4).toLong(), 512L * MIB)
        val estimatedRam = artifact.sizeBytes + runtimeOverhead + kvCache
        val requiredStorage = artifact.sizeBytes + max(artifact.sizeBytes / 20, 64L * MIB)
        fun result(status: Compatibility, reason: String) = CompatibilityResult(
            status = status,
            reason = reason,
            estimatedRamBytes = estimatedRam,
            modelWeightsBytes = artifact.sizeBytes,
            runtimeOverheadBytes = runtimeOverhead,
            kvCacheBytes = kvCache,
            contextWindowTokens = 4_096,
            requiredStorageBytes = requiredStorage,
        )
        if (requiredStorage > device.availableStorageBytes) {
            return result(Compatibility.INCOMPATIBLE, "Not enough free storage, including download headroom.")
        }
        if (estimatedRam > device.totalRamBytes * 0.8) {
            return result(Compatibility.INCOMPATIBLE, "The model is too large for this device's RAM.")
        }
        if (estimatedRam > device.availableRamBytes * 0.8) {
            return result(Compatibility.WARNING, "It should fit after memory is freed. Close other apps before loading it.")
        }
        return result(Compatibility.COMPATIBLE, "Expected to fit this device. Exact performance still depends on the model.")
    }

    private companion object { const val MIB = 1024L * 1024L }
}
