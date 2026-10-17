package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.data.history.ChatThread
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Screens of the app. The chat home is always the root; the others are pushed on top of it. */
enum class AppScreen { CHAT, CATALOG, MODEL }

/** Which Models tab to show when the catalog opens. */
enum class CatalogTab { DISCOVER, LIBRARY, SETTINGS }

/** Sending is valid only for the visible chat after its model has finished loading. */
internal val MobieUiState.canSendToRuntime: Boolean
    get() = screen == AppScreen.CHAT && chatting && !chatPending && readOnlyChat == null &&
        selected != null && selectedArtifact != null && downloadedPath != null && runtimeState == RuntimeState.READY

/**
 * Picks the model the app opens its chat home with: the last model the user chatted with if it is
 * still installed, otherwise the model of the most recently updated chat that is still installed,
 * otherwise any installed model. Null means nothing is installed and the home shows its empty state.
 *
 * @param installedModelIds installed models in display order.
 * @param chatModelIdsNewestFirst the model of every saved chat, newest chat first.
 */
internal fun chooseLaunchModel(
    installedModelIds: List<String>,
    lastUsedModelId: String?,
    chatModelIdsNewestFirst: List<String>,
): String? {
    if (installedModelIds.isEmpty()) return null
    val installed = installedModelIds.toSet()
    return lastUsedModelId?.takeIf { it in installed }
        ?: chatModelIdsNewestFirst.firstOrNull { it in installed }
        ?: installedModelIds.first()
}

/** Age buckets used to group the chats list, in display order. */
enum class ChatAge(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    PREVIOUS_7_DAYS("Previous 7 days"),
    PREVIOUS_30_DAYS("Previous 30 days"),
    OLDER("Older"),
}

internal data class ChatGroup(val age: ChatAge, val chats: List<ChatThread>)

/** Calendar-day bucket for [timestamp] relative to [now] in [zone]. Future times count as today. */
internal fun chatAge(timestamp: Long, now: Long, zone: ZoneId): ChatAge {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0 -> ChatAge.TODAY
        daysAgo == 1L -> ChatAge.YESTERDAY
        daysAgo <= 7 -> ChatAge.PREVIOUS_7_DAYS
        daysAgo <= 30 -> ChatAge.PREVIOUS_30_DAYS
        else -> ChatAge.OLDER
    }
}

/** Groups chats newest first under age headings; empty groups are left out. */
internal fun groupChatsByAge(chats: List<ChatThread>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<ChatGroup> =
    chats
        .sortedByDescending { it.session.updatedAt }
        .groupBy { chatAge(it.session.updatedAt, now, zone) }
        .toSortedMap()
        .map { (age, items) -> ChatGroup(age, items) }

/** Case-insensitive match on the chat title or any message text. A blank query keeps everything. */
internal fun filterChats(chats: List<ChatThread>, query: String): List<ChatThread> {
    val needle = query.trim()
    if (needle.isEmpty()) return chats
    return chats.filter { thread ->
        thread.session.title.contains(needle, ignoreCase = true) ||
            thread.session.messages.any { it.text.contains(needle, ignoreCase = true) }
    }
}

/**
 * Short model name for secondary text: the installed or saved title when known, otherwise the
 * repository name without its owner (`litert-community/Gemma3-1B-IT` → `Gemma3-1B-IT`).
 */
internal fun shortModelName(modelId: String, title: String? = null): String =
    title?.takeIf(String::isNotBlank) ?: modelId.substringAfterLast('/').ifBlank { modelId }

/** Returns the stack with [screen] on top, never duplicating the current top. */
internal fun List<AppScreen>.pushScreen(screen: AppScreen): List<AppScreen> {
    if (screen == AppScreen.CHAT) return listOf(AppScreen.CHAT)
    val base = ifEmpty { listOf(AppScreen.CHAT) }
    return if (base.last() == screen) base else base.filterNot { it == screen } + screen
}

/** Returns the stack after a back press; the chat home always stays at the root. */
internal fun List<AppScreen>.popScreen(): List<AppScreen> =
    if (size <= 1) listOf(AppScreen.CHAT) else dropLast(1)
