package dev.yashasvm.mobie.data.history

/** Empty drafts do not consume saved-conversation slots; keep only the newest draft. */
internal fun retainedChatSessions(
    newestFirst: List<ChatHistorySession>,
    maxSavedSessions: Int,
): List<ChatHistorySession> {
    require(maxSavedSessions >= 0)
    var saved = 0
    var keptDraft = false
    return newestFirst.filter { session ->
        if (session.messages.isEmpty()) {
            if (keptDraft) false else {
                keptDraft = true
                true
            }
        } else {
            saved++ < maxSavedSessions
        }
    }
}
