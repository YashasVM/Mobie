package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.data.download.InstalledModelEntry

/**
 * Picks the installed model the app opens its chat with: the last model the user chatted with if
 * it is still installed, otherwise the first installed model. Null means nothing is installed, so
 * the app opens the model catalog instead.
 */
internal fun chooseLaunchModel(installed: List<InstalledModelEntry>, lastUsedModelId: String?): InstalledModelEntry? =
    installed.firstOrNull { it.model.id == lastUsedModelId } ?: installed.firstOrNull()
