package dev.yashasvm.mobie.core.runtime

import kotlin.math.max

/**
 * Re-check persistent storage immediately before entering LiteRT initialization.
 *
 * Real Qwen3-0.6B E2E measurements showed LiteRT's cold optimized cache can be nearly as large as
 * the published model artifact itself (339,216,776 bytes of cache for 347,251,840 bytes of model
 * weights), while a validated unload/reload added 0 bytes. Cold loads therefore reserve a full
 * model-sized cache plus safety margin. Warm loads may use only the filesystem reserve, but only
 * after LiteRtCacheState proves the existing cache belongs to the exact installed artifact/runtime.
 */
internal object RuntimeLoadStoragePolicy {
    fun blockReason(
        modelWeightsBytes: Long,
        availableStorageBytes: Long,
        reusableCache: Boolean = false,
    ): String? {
        if (modelWeightsBytes <= 0) {
            return "The installed model file is missing or empty. Verify or download the model again before loading it."
        }
        if (availableStorageBytes < 0) {
            return "Could not verify free storage for LiteRT initialization. Check model storage access and try again."
        }

        val requiredFreeBytes = requiredLoadFreeBytes(modelWeightsBytes, reusableCache)
        if (availableStorageBytes < requiredFreeBytes) {
            return if (reusableCache) {
                "Not enough free storage to safely reload this model with its validated LiteRT cache. Free storage and try again."
            } else {
                "Not enough free storage to safely initialize this model and build LiteRT's optimized cache. Free storage and try again."
            }
        }
        return null
    }

    internal fun requiredLoadFreeBytes(modelWeightsBytes: Long, reusableCache: Boolean): Long =
        if (reusableCache) filesystemReserveBytes(modelWeightsBytes) else requiredColdLoadFreeBytes(modelWeightsBytes)

    internal fun requiredColdLoadFreeBytes(modelWeightsBytes: Long): Long =
        firstLoadCacheHeadroomBytes(modelWeightsBytes) + filesystemReserveBytes(modelWeightsBytes)

    internal fun firstLoadCacheHeadroomBytes(modelWeightsBytes: Long): Long =
        max(modelWeightsBytes + modelWeightsBytes / 10, 256L * MIB)

    internal fun filesystemReserveBytes(modelWeightsBytes: Long): Long =
        max(modelWeightsBytes / 20, 64L * MIB)

    private const val MIB = 1024L * 1024L
}
