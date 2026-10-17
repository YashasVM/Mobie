package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.data.download.InstalledModelEntry

/** Where the app opens on launch. Pure so the routing rule can be unit tested without Android. */
internal sealed interface LaunchDestination {
    data object EmptyChat : LaunchDestination
    data class Chat(val entry: InstalledModelEntry) : LaunchDestination
}

/** Chooses the last-used installed model for chat; never loads the runtime. */
internal fun initialDestination(
    installed: List<InstalledModelEntry>,
    lastUsedId: String?,
): LaunchDestination {
    val entry = installed.firstOrNull { it.model.id == lastUsedId } ?: installed.firstOrNull()
    return if (entry != null) LaunchDestination.Chat(entry) else LaunchDestination.EmptyChat
}
