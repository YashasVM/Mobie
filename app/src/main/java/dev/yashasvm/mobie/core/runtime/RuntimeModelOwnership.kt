package dev.yashasvm.mobie.core.runtime

/**
 * Tracks which model currently owns the native runtime resources.
 *
 * Mobie intentionally keeps this state separate from UI selection: selecting a new model updates
 * UI state before the previous native runtime necessarily finishes unloading. Access is serialized
 * by MobieViewModel's runtime lifecycle mutex.
 */
internal class RuntimeModelOwnership {
    private var loadedModelId: String? = null

    fun markLoaded(modelId: String) {
        loadedModelId = modelId
    }

    fun clear() {
        loadedModelId = null
    }

    fun owns(modelId: String): Boolean = loadedModelId == modelId
}
