package dev.yashasvm.mobie.data.history

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class HistoryMessage(
    val fromUser: Boolean,
    val text: String,
    val imagePath: String? = null,
    val thinking: String = "",
    val interrupted: Boolean = false,
)

@Serializable
data class ChatHistorySession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<HistoryMessage> = emptyList(),
)

class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(modelId: String): List<HistoryMessage> {
        val activeId = currentId(modelId)
        return sessions(modelId).firstOrNull { activeId == null || it.id == activeId }
            ?.messages.orEmpty()
    }

    fun currentSessionId(modelId: String): String? = currentId(modelId)
        ?: sessions(modelId).firstOrNull()?.id

    fun write(modelId: String, messages: List<HistoryMessage>) {
        val now = System.currentTimeMillis()
        val id = currentId(modelId) ?: UUID.randomUUID().toString()
        val existing = sessions(modelId).filterNot { it.id == id }
        val title = messages.firstOrNull { it.fromUser && it.text.isNotBlank() }
            ?.text
            ?.replace("\n", " ")
            ?.take(TITLE_LENGTH)
            ?: "New local chat"
        val updated = (listOf(ChatHistorySession(id, title, now, messages.takeLast(MAX_MESSAGES))) + existing)
            .take(MAX_SESSIONS)
        preferences.edit()
            .putString(sessionsKey(modelId), json.encodeToString(updated))
            .putString(currentKey(modelId), id)
            .apply()
    }

    fun startNewSession(modelId: String): String {
        val id = UUID.randomUUID().toString()
        val sessions = (listOf(ChatHistorySession(id, "New local chat", System.currentTimeMillis())) + sessions(modelId))
            .take(MAX_SESSIONS)
        preferences.edit()
            .putString(sessionsKey(modelId), json.encodeToString(sessions))
            .putString(currentKey(modelId), id)
            .apply()
        return id
    }

    fun activate(modelId: String, sessionId: String) {
        if (sessions(modelId).any { it.id == sessionId }) {
            preferences.edit().putString(currentKey(modelId), sessionId).apply()
        }
    }

    fun sessions(modelId: String): List<ChatHistorySession> {
        val saved = preferences.getString(sessionsKey(modelId), null)
            ?.let { runCatching { json.decodeFromString<List<ChatHistorySession>>(it) }.getOrNull() }
        if (saved != null) return saved.sortedByDescending { it.updatedAt }

        val legacy = preferences.getString(modelId, null)
            ?.let { runCatching { json.decodeFromString<List<HistoryMessage>>(it) }.getOrNull() }
            .orEmpty()
        return if (legacy.isEmpty()) emptyList() else listOf(
            ChatHistorySession("legacy", "Previous local chat", 0L, legacy),
        )
    }

    fun clear(modelId: String) {
        preferences.edit()
            .remove(modelId)
            .remove(sessionsKey(modelId))
            .remove(currentKey(modelId))
            .apply()
    }

    private fun currentId(modelId: String): String? = preferences.getString(currentKey(modelId), null)

    private fun sessionsKey(modelId: String) = "sessions:$modelId"

    private fun currentKey(modelId: String) = "current:$modelId"

    private companion object {
        const val MAX_MESSAGES = 100
        const val MAX_SESSIONS = 20
        const val TITLE_LENGTH = 48
    }
}
