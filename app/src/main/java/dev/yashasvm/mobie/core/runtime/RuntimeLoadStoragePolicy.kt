package dev.yashasvm.mobie.core.runtime

import kotlin.math.max

/**
 * Re-check persistent storage immediately before entering LiteRT initialization.
 *
 * Model downloads reserve first-load cache space up front, but users can consume that free space
 * after a download completes. LiteRT writes optimized artifacts beside the model on first load, so
 * loading without a fresh check can fail deep in native initialization or leave the filesystem
 * critically full.
 */
internal object RuntimeLoadStoragePolicy {
    fun blockReason(modelWeightsBytes: Long, availableStorageBytes: Long): String? {
        if (modelWeightsBytes <= 0 || availableStorageBytes < 0) return null

        val optimizedCacheHeadroom = max(modelWeightsBytes / 10 * 3, 256L * MIB)
        val filesystemReserve = max(modelWeightsBytes / 20, 64L * MIB)
        val requiredFreeBytes = optimizedCacheHeadroom + filesystemReserve
        if (availableStorageBytes < requiredFreeBytes) {
            return "Not enough free storage to safely initialize this model and build LiteRT's optimized cache. Free storage and try again."
        }
        return null
    }

    private const val MIB = 1024L * 1024L
}
