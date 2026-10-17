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
    /** True once the user renamed the chat, so later writes keep their title. */
    val customTitle: Boolean = false,
    /** The model's display name when the chat was last saved; shown even after the model is deleted. */
    val modelTitle: String? = null,
)

/** One saved chat together with the model it belongs to, for the cross-model chats list. */
data class ChatThread(val modelId: String, val session: ChatHistorySession)

/**
 * Local chat history in app-private SharedPreferences. Sessions are stored per model under
 * `sessions:<modelId>` with the active pointer at `current:<modelId>`; installs older than sessions
 * kept one conversation under the bare model id, which is still read as a "legacy" session.
 *
 * Every method parses or writes JSON, so callers should use it off the main thread. Methods are
 * synchronized so concurrent IO callers see a consistent cache.
 */
class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    // In-memory cache keyed by modelId. Eliminates redundant JSON deserialization when the ViewModel
    // calls sessions() immediately after write() within the same event handler.
    private val sessionsCache = HashMap<String, List<ChatHistorySession>>()

    @Synchronized
    fun read(modelId: String): List<HistoryMessage> {
        val available = sessions(modelId)
        val activeId = resolvedSessionId(modelId, available)
        return available.firstOrNull { it.id == activeId }?.messages.orEmpty()
    }

    @Synchronized
    fun currentSessionId(modelId: String): String? = resolvedSessionId(modelId, sessions(modelId))

    /** Writes into the active session for [modelId], creating one if none exists. */
    @Synchronized
    fun write(modelId: String, messages: List<HistoryMessage>) {
        // Older installs had no active-session key. Reuse their restored conversation rather
        // than creating a duplicate when the user sends the next message.
        writeSession(modelId, resolvedSessionId(modelId, sessions(modelId)), messages)
    }

    /**
     * Saves [messages] into [sessionId] (a new session when null or unknown), makes it the active
     * session for [modelId], and returns its id. Empty conversations are never persisted.
     */
    @Synchronized
    fun writeSession(
        modelId: String,
        sessionId: String?,
        messages: List<HistoryMessage>,
        modelTitle: String? = null,
    ): String {
        val id = sessionId ?: UUID.randomUUID().toString()
        if (messages.isEmpty()) return id
        val available = sessions(modelId)
        val previous = available.firstOrNull { it.id == id }
        val existing = available.filterNot { it.id == id }
        val title = previous?.takeIf { it.customTitle }?.title ?: titleFor(messages)
        val session = ChatHistorySession(
            id = id,
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = messages.takeLast(MAX_MESSAGES),
            customTitle = previous?.customTitle == true,
            modelTitle = modelTitle ?: previous?.modelTitle,
        )
        save(modelId, (listOf(session) + existing).take(MAX_SESSIONS), currentId = id)
        return id
    }

    /** Kept for older callers. Prefer leaving a new chat unsaved until its first message. */
    @Synchronized
    fun startNewSession(modelId: String): String {
        val id = UUID.randomUUID().toString()
        val sessions = (listOf(ChatHistorySession(id, DEFAULT_TITLE, System.currentTimeMillis())) + sessions(modelId))
            .take(MAX_SESSIONS)
        save(modelId, sessions, currentId = id)
        return id
    }

    @Synchronized
    fun activate(modelId: String, sessionId: String) {
        if (sessions(modelId).any { it.id == sessionId }) {
            preferences.edit().putString(currentKey(modelId), sessionId).apply()
        }
    }

    @Synchronized
    fun sessions(modelId: String): List<ChatHistorySession> {
        sessionsCache[modelId]?.let { return it }
        return loadSessions(modelId).also { sessionsCache[modelId] = it }
    }

    @Synchronized
    fun session(modelId: String, sessionId: String): ChatHistorySession? =
        sessions(modelId).firstOrNull { it.id == sessionId }

    /** Every saved chat with at least one message, across all models, newest first. */
    @Synchronized
    fun allSessions(): List<ChatThread> = modelIds()
        .flatMap { modelId -> sessions(modelId).map { ChatThread(modelId, it) } }
        .filter { it.session.messages.isNotEmpty() }
        .sortedByDescending { it.session.updatedAt }

    /** Renames a chat; a blank title is ignored. Returns true when a session was renamed. */
    @Synchronized
    fun rename(modelId: String, sessionId: String, title: String): Boolean {
        val cleaned = title.replace("\n", " ").trim().take(MAX_TITLE_LENGTH)
        if (cleaned.isEmpty()) return false
        val available = sessions(modelId)
        if (available.none { it.id == sessionId }) return false
        val updated = available.map { if (it.id == sessionId) it.copy(title = cleaned, customTitle = true) else it }
        save(modelId, updated, currentId = currentId(modelId))
        return true
    }

    /** Deletes one chat. The active pointer is cleared when it pointed at the deleted chat. */
    @Synchronized
    fun delete(modelId: String, sessionId: String): Boolean {
        val available = sessions(modelId)
        if (available.none { it.id == sessionId }) return false
        val remaining = available.filterNot { it.id == sessionId }
        val pointer = currentId(modelId)?.takeIf { it != sessionId }
        // Writing the sessions key (even as an empty list) also retires the legacy single-chat key.
        save(modelId, remaining, currentId = pointer, dropLegacy = true)
        return true
    }

    @Synchronized
    fun lastModelId(): String? = preferences.getString(LAST_MODEL_KEY, null)

    @Synchronized
    fun setLastModelId(modelId: String) {
        if (preferences.getString(LAST_MODEL_KEY, null) != modelId) {
            preferences.edit().putString(LAST_MODEL_KEY, modelId).apply()
        }
    }

    @Synchronized
    fun clear(modelId: String) {
        sessionsCache.remove(modelId)
        preferences.edit()
            .remove(modelId)
            .remove(sessionsKey(modelId))
            .remove(currentKey(modelId))
            .apply()
    }

    private fun save(modelId: String, sessions: List<ChatHistorySession>, currentId: String?, dropLegacy: Boolean = false) {
        sessionsCache[modelId] = sessions
        preferences.edit().apply {
            putString(sessionsKey(modelId), json.encodeToString(sessions))
            if (currentId != null) putString(currentKey(modelId), currentId) else remove(currentKey(modelId))
            if (dropLegacy) remove(modelId)
        }.apply()
    }

    /** Model ids that have saved chats, from both the session keys and the legacy bare-id keys. */
    private fun modelIds(): Set<String> = historyModelIds(preferences.all.keys)

    private fun loadSessions(modelId: String): List<ChatHistorySession> {
        val saved = preferences.getString(sessionsKey(modelId), null)
            ?.let { runCatching { json.decodeFromString<List<ChatHistorySession>>(it) }.getOrNull() }
        if (saved != null) return saved.sortedByDescending { it.updatedAt }

        val legacy = preferences.getString(modelId, null)
            ?.let { runCatching { json.decodeFromString<List<HistoryMessage>>(it) }.getOrNull() }
            .orEmpty()
        return if (legacy.isEmpty()) emptyList() else listOf(
            ChatHistorySession(LEGACY_SESSION_ID, "Previous local chat", 0L, legacy),
        )
    }

    private fun resolvedSessionId(modelId: String, available: List<ChatHistorySession>): String? =
        currentId(modelId)?.takeIf { id -> available.any { it.id == id } }
            ?: available.firstOrNull()?.id

    private fun currentId(modelId: String): String? = preferences.getString(currentKey(modelId), null)

    private fun sessionsKey(modelId: String) = "$SESSIONS_PREFIX$modelId"

    private fun currentKey(modelId: String) = "$CURRENT_PREFIX$modelId"

    companion object {
        const val LEGACY_SESSION_ID = "legacy"
        private const val DEFAULT_TITLE = "New chat"
        private const val SESSIONS_PREFIX = "sessions:"
        private const val CURRENT_PREFIX = "current:"
        private const val META_PREFIX = "meta:"
        private const val LAST_MODEL_KEY = "${META_PREFIX}last_model"
        private const val MAX_MESSAGES = 100
        private const val MAX_SESSIONS = 20
        private const val TITLE_LENGTH = 48
        private const val MAX_TITLE_LENGTH = 80

        /** The automatic title: the first user prompt on one line, or a plain default. */
        fun titleFor(messages: List<HistoryMessage>): String =
            messages.firstOrNull { it.fromUser && it.text.isNotBlank() }
                ?.text
                ?.replace("\n", " ")
                ?.trim()
                ?.take(TITLE_LENGTH)
                ?: DEFAULT_TITLE

        /**
         * Model ids referenced by stored keys. `sessions:<id>` keys name a model directly; any key
         * without a known prefix is a legacy single-conversation entry keyed by the bare model id.
         */
        fun historyModelIds(keys: Collection<String>): Set<String> = keys.mapNotNullTo(LinkedHashSet()) { key ->
            when {
                key.startsWith(SESSIONS_PREFIX) -> key.removePrefix(SESSIONS_PREFIX).takeIf(String::isNotEmpty)
                key.startsWith(CURRENT_PREFIX) || key.startsWith(META_PREFIX) -> null
                else -> key.takeIf(String::isNotEmpty)
            }
        }
    }
}
