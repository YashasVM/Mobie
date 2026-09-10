package dev.yashasvm.mobie.core.runtime

/**
 * Conservatively tracks which model may still own native runtime resources.
 *
 * UI selection changes before native lifecycle transitions necessarily finish, so deletion cannot
 * use selected-model state as a proxy for open files. When ownership becomes uncertain (for example
 * while replacing a native model), [owns] intentionally returns true for any model until a load
 * succeeds and establishes a new owner. Access is serialized by MobieViewModel's runtime mutex.
 */
internal class RuntimeModelOwnership {
    private var loadedModelId: String? = null
    private var ownershipKnown = true

    fun markLoaded(modelId: String) {
        loadedModelId = modelId
        ownershipKnown = true
    }

    fun clear() {
        loadedModelId = null
        ownershipKnown = false
    }

    fun owns(modelId: String): Boolean = !ownershipKnown || loadedModelId == modelId
}
