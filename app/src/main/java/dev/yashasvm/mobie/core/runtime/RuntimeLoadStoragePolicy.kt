package dev.yashasvm.mobie.core.runtime

import kotlin.math.max

/**
 * Re-check persistent storage immediately before entering LiteRT initialization.
 *
 * Real Qwen3-0.6B E2E measurements showed LiteRT's cold optimized cache can be nearly as large as
 * the published model artifact itself (339,216,776 bytes of cache for 347,251,840 bytes of model
 * weights). Reserve a full model-sized cache plus a safety margin instead of the previous 30%
 * estimate so first inference does not fail deep in native initialization on storage-constrained
 * devices.
 */
internal object RuntimeLoadStoragePolicy {
    fun blockReason(modelWeightsBytes: Long, availableStorageBytes: Long): String? {
        if (modelWeightsBytes <= 0) {
            return "The installed model file is missing or empty. Verify or download the model again before loading it."
        }
        if (availableStorageBytes < 0) {
            return "Could not verify free storage for LiteRT initialization. Check model storage access and try again."
        }

        val requiredFreeBytes = requiredColdLoadFreeBytes(modelWeightsBytes)
        if (availableStorageBytes < requiredFreeBytes) {
            return "Not enough free storage to safely initialize this model and build LiteRT's optimized cache. Free storage and try again."
        }
        return null
    }

    internal fun requiredColdLoadFreeBytes(modelWeightsBytes: Long): Long =
        firstLoadCacheHeadroomBytes(modelWeightsBytes) + filesystemReserveBytes(modelWeightsBytes)

    internal fun firstLoadCacheHeadroomBytes(modelWeightsBytes: Long): Long =
        max(modelWeightsBytes + modelWeightsBytes / 10, 256L * MIB)

    internal fun filesystemReserveBytes(modelWeightsBytes: Long): Long =
        max(modelWeightsBytes / 20, 64L * MIB)

    private const val MIB = 1024L * 1024L
}
