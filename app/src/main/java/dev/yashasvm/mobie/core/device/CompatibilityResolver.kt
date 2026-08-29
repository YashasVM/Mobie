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

        val estimatedRam = max((artifact.sizeBytes * 1.4).toLong(), artifact.sizeBytes + 768L * MIB)
        if (artifact.sizeBytes > device.availableStorageBytes) {
            return CompatibilityResult(Compatibility.INCOMPATIBLE, "Not enough free storage.", estimatedRam)
        }
        if (estimatedRam > device.totalRamBytes * 0.8) {
            return CompatibilityResult(Compatibility.INCOMPATIBLE, "The model is too large for this device's RAM.", estimatedRam)
        }
        if (estimatedRam > device.availableRamBytes * 0.8) {
            return CompatibilityResult(
                Compatibility.WARNING,
                "It should fit, but close other apps before loading it.",
                estimatedRam,
            )
        }
        return CompatibilityResult(Compatibility.COMPATIBLE, "Expected to run on this device.", estimatedRam)
    }

    private companion object { const val MIB = 1024L * 1024L }
}
