package dev.yashasvm.mobie.core.device

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ArtifactExecutionTarget
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.LiteRtContextWindowPolicy
import dev.yashasvm.mobie.core.runtime.RuntimeLoadMemoryPolicy
import dev.yashasvm.mobie.core.runtime.RuntimeLoadStoragePolicy
import java.util.Locale
import kotlin.math.max

/** The backend a load would run, with its admission. For AUTO, this is the backend Auto would pick. */
data class BackendAdmission(val backend: ComputeBackend, val result: CompatibilityResult)

class CompatibilityResolver {
    fun selectBestArtifact(model: AiModel, device: DeviceProfile): ModelArtifact? = model.artifacts
        .asSequence()
        .filter {
            it.format == ModelFormat.LITERT_LM &&
                it.executionTarget == ArtifactExecutionTarget.GENERIC &&
                // Automatic recommendations must be measurable. A missing publisher size means
                // Mobie cannot estimate RAM, storage, or first-load cache headroom safely; keep the
                // artifact visible as a warning for manual inspection, but never label it the best
                // download for this device until Hugging Face returns a concrete size.
                it.sizeBytes > 0
        }
        // Rank with the backend Auto would pick, so the recommendation matches the Auto line in the compute panel.
        .map { artifact -> artifact to resolveBackend(artifact, device, model.supportsVision, ComputeBackend.AUTO).result }
        .filter { (_, result) ->
            result.status == Compatibility.COMPATIBLE || result.status == Compatibility.WARNING
        }
        .minWithOrNull(
            compareBy<Pair<ModelArtifact, CompatibilityResult>>(
                { (_, result) -> if (result.status == Compatibility.COMPATIBLE) 0 else 1 },
                // Prefer enough context for normal multi-turn chat before optimizing the last bit
                // of RAM. Cap the ranking benefit at 4K so a huge 64K package does not outrank a
                // lighter 4K package merely for exposing a larger cache.
                { (_, result) -> -minOf(result.contextWindowTokens.coerceAtLeast(0), PREFERRED_CONTEXT_TOKENS) },
                { (_, result) -> result.estimatedRamBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
                { (_, result) -> result.requiredStorageBytes.takeIf { it > 0 } ?: Long.MAX_VALUE },
                { (artifact, _) -> artifact.sizeBytes },
            ),
        )
        ?.first

    fun resolve(
        artifact: ModelArtifact?,
        device: DeviceProfile,
        visionEncoderLoaded: Boolean = false,
        backend: ComputeBackend = ComputeBackend.CPU,
        allowReclaim: Boolean = false,
        contextTokens: Int? = null,
        capabilities: ComputeCapabilities? = null,
    ): CompatibilityResult = resolveBackend(artifact, device, visionEncoderLoaded, backend, allowReclaim, contextTokens, capabilities).result

    /**
     * Admission for the backend a load would run. Explicit CPU and GPU are judged as requested.
     * AUTO mirrors the native load gate: GPU runs when the device may try it and GPU is admitted
     * now (or after reclaim, when the user has accepted it). Otherwise CPU runs and the reason says
     * why GPU was skipped. Total RAM decides whether a backend can ever fit; current RAM decides
     * whether it needs reclaim first.
     */
    fun resolveBackend(
        artifact: ModelArtifact?,
        device: DeviceProfile,
        visionEncoderLoaded: Boolean = false,
        backend: ComputeBackend = ComputeBackend.CPU,
        allowReclaim: Boolean = false,
        contextTokens: Int? = null,
        capabilities: ComputeCapabilities? = null,
    ): BackendAdmission {
        if (backend != ComputeBackend.AUTO) {
            return BackendAdmission(backend, resolveFor(artifact, device, visionEncoderLoaded, backend, contextTokens, capabilities))
        }
        val cpu = resolveFor(artifact, device, visionEncoderLoaded, ComputeBackend.CPU, contextTokens, capabilities)
        if (!device.gpuAttemptable || cpu.status == Compatibility.INCOMPATIBLE) {
            return BackendAdmission(ComputeBackend.CPU, cpu)
        }
        val gpu = resolveFor(artifact, device, visionEncoderLoaded, ComputeBackend.GPU, contextTokens, capabilities)
        val gpuAdmitted = gpu.status != Compatibility.INCOMPATIBLE && when (gpu.memoryTier) {
            MemoryAdmissionTier.FITS_NOW -> true
            MemoryAdmissionTier.FITS_AFTER_RECLAIM -> allowReclaim
            MemoryAdmissionTier.IMPOSSIBLE, null -> false
        }
        if (gpuAdmitted) return BackendAdmission(ComputeBackend.GPU, gpu)
        val gpuNeed = String.format(Locale.US, "%.1f GB", gpu.estimatedRamBytes.toDouble() / GIB)
        val note = if (gpu.memoryTier == MemoryAdmissionTier.IMPOSSIBLE) {
            "GPU needs about $gpuNeed, more than this phone can provide safely, so Auto uses CPU."
        } else {
            "GPU needs about $gpuNeed, more than is free now, so Auto uses CPU."
        }
        return BackendAdmission(ComputeBackend.CPU, cpu.copy(reason = "$note ${cpu.reason}"))
    }

    private fun resolveFor(
        artifact: ModelArtifact?,
        device: DeviceProfile,
        visionEncoderLoaded: Boolean,
        backend: ComputeBackend,
        contextTokens: Int?,
        capabilities: ComputeCapabilities?,
    ): CompatibilityResult {
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
                "The publisher did not provide an artifact size, so Mobie cannot safely estimate RAM or storage for an automatic recommendation.",
                0,
            )
        }

        // Only the exact package metadata establishes whether its context may shrink. Until
        // first load, a shrunken catalog estimate is provisional and cannot claim compatibility.
        val advertisedContext = artifact.contextWindowTokens ?: DEFAULT_CONTEXT_TOKENS
        val selectedContext = try {
            LiteRtContextWindowPolicy.contextFor(
                maxContextTokens = capabilities?.maxContextTokens ?: advertisedContext,
                dynamicContext = capabilities?.dynamicContext ?: true,
                requestedContextTokens = contextTokens,
                advertisedContextWindowTokens = advertisedContext,
                modelWeightsBytes = artifact.sizeBytes,
                totalRamBytes = device.totalRamBytes,
                availableRamBytes = device.availableRamBytes,
                lowMemoryThresholdBytes = device.lowMemoryThresholdBytes,
                isLowRamDevice = device.isLowRamDevice,
                backend = backend,
            )
        } catch (error: IllegalArgumentException) {
            // The load would refuse this request with the same message, so the verdict says so up front.
            return CompatibilityResult(
                status = Compatibility.INCOMPATIBLE,
                reason = error.message.orEmpty(),
                estimatedRamBytes = 0,
                modelWeightsBytes = artifact.sizeBytes,
                contextWindowTokens = advertisedContext,
            )
        }
        if (selectedContext < LiteRtContextWindowPolicy.MIN_USEFUL_CONTEXT_TOKENS) {
            return CompatibilityResult(
                status = Compatibility.INCOMPATIBLE,
                reason = "This LiteRT package exposes only $selectedContext context tokens, below Mobie's 1,024-token minimum for useful local chat.",
                estimatedRamBytes = 0,
                modelWeightsBytes = artifact.sizeBytes,
                contextWindowTokens = selectedContext,
            )
        }
        // Same estimator as the native load gate, so pre-download, compute-panel and pre-load RAM agree.
        val memoryEstimate = requireNotNull(
            RuntimeLoadMemoryPolicy.estimate(artifact.copy(contextWindowTokens = selectedContext), backend, visionEncoderLoaded),
        )
        val estimatedRam = memoryEstimate.estimatedRamBytes
        val requiredStorage = requiredStorageBytes(artifact.sizeBytes)
        val admission = RuntimeLoadMemoryPolicy.admit(memoryEstimate, device.totalRamBytes,
            device.availableRamBytes, device.availableAfterReclaimBytes,
            device.lowMemoryThresholdBytes, device.isLowMemory, device.isLowRamDevice)
        fun result(status: Compatibility, reason: String) = CompatibilityResult(
            status = status,
            reason = reason,
            estimatedRamBytes = estimatedRam,
            modelWeightsBytes = artifact.sizeBytes,
            runtimeOverheadBytes = memoryEstimate.runtimeOverheadBytes,
            kvCacheBytes = memoryEstimate.kvCacheBytes,
            contextWindowTokens = memoryEstimate.contextWindowTokens,
            requiredStorageBytes = requiredStorage,
            memoryTier = admission.tier,
            anonymousBytes = memoryEstimate.anonymousBytes,
            residentWeightBudgetBytes = memoryEstimate.residentWeightBudgetBytes,
        )
        if (requiredStorage > device.availableStorageBytes) {
            return result(
                Compatibility.INCOMPATIBLE,
                "Not enough free storage for the model, download headroom, and LiteRT's first-load optimized cache.",
            )
        }
        if (admission.tier == MemoryAdmissionTier.IMPOSSIBLE) return result(Compatibility.INCOMPATIBLE, admission.reason)
        if (device.thermalStatus >= THERMAL_STATUS_CRITICAL) {
            return result(
                Compatibility.WARNING,
                "Android reports critical thermal pressure. Mobie will block local model loading until the device cools.",
            )
        }
        if (device.thermalStatus >= THERMAL_STATUS_SEVERE) {
            return result(
                Compatibility.WARNING,
                "Android reports severe thermal pressure. The model can still run, but Mobie will cap new responses at 256 tokens until the device cools.",
            )
        }
        if (admission.tier == MemoryAdmissionTier.FITS_AFTER_RECLAIM) return result(Compatibility.WARNING, admission.reason)
        if (capabilities == null && selectedContext < advertisedContext) {
            val fixedBytes = requireNotNull(RuntimeLoadMemoryPolicy.estimate(
                artifact.copy(contextWindowTokens = advertisedContext), backend, visionEncoderLoaded,
            )).estimatedRamBytes
            val fixedRam = String.format(Locale.US, "%.1f GB", fixedBytes.toDouble() / GIB)
            return result(Compatibility.WARNING,
                "Package context limits are unknown until first load. This RAM estimate assumes a dynamic $selectedContext-token context; a fixed $advertisedContext-token package would need about $fixedRam. The load checks package limits before allocating.")
        }
        return result(Compatibility.COMPATIBLE, admission.reason)
    }

    /**
     * LiteRT-LM optimizes model weights for the current device on first load and stores those
     * artifacts in the configured cache directory. The real Qwen E2E cache reached ~97.7% of the
     * model artifact size, so recommendations share the runtime's measured cold-load allowance
     * rather than the old 30% estimate.
     */
    internal fun requiredStorageBytes(modelWeightsBytes: Long): Long =
        modelWeightsBytes + RuntimeLoadStoragePolicy.requiredColdLoadFreeBytes(modelWeightsBytes)

    private companion object {
        const val DEFAULT_CONTEXT_TOKENS = 4_096
        const val PREFERRED_CONTEXT_TOKENS = 4_096
        const val THERMAL_STATUS_SEVERE = 3
        const val THERMAL_STATUS_CRITICAL = 4
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}
