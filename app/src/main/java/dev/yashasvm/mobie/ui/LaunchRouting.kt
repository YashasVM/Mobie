package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.data.download.InstalledModelEntry

/** Where the app opens on launch. Pure so the routing rule can be unit tested without Android. */
internal sealed interface LaunchDestination {
    data object Welcome : LaunchDestination
    data object Catalog : LaunchDestination
    data class Chat(val entry: InstalledModelEntry) : LaunchDestination
}

/**
 * First run shows the welcome. After that, if any model is installed, open chat on the last-used
 * installed model, falling back to the first entry (callers pass installed models most recent
 * first). With nothing installed, show the catalog. Loading still goes through the runtime's
 * memory admission, so this only chooses the screen, never bypasses a refusal.
 */
internal fun initialDestination(
    welcomeSeen: Boolean,
    installed: List<InstalledModelEntry>,
    lastUsedId: String?,
): LaunchDestination {
    if (!welcomeSeen) return LaunchDestination.Welcome
    val entry = installed.firstOrNull { it.model.id == lastUsedId } ?: installed.firstOrNull()
    return if (entry != null) LaunchDestination.Chat(entry) else LaunchDestination.Catalog
}
