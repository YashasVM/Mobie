package dev.yashasvm.mobie.data.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
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

/**
 * Local chat history persisted in SharedPreferences.
 *
 * All public functions are suspend functions. Their work runs on [Dispatchers.IO] and is serialized
 * by one [Mutex], so reads and writes never interleave and the in-memory cache is only touched while
 * the lock is held. The lock is taken before hopping to IO, and Mutex serves waiters first-in
 * first-out, so callers that reach the lock in order also take effect in that order.
 */
class ChatHistoryStore private constructor(openPreferences: () -> SharedPreferences) {
    internal constructor(preferences: SharedPreferences) : this({ preferences })

    constructor(context: Context) : this({
        context.applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    })

    // First access, including opening the preferences file, happens under the IO lock.
    private val preferences by lazy(openPreferences)

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    // In-memory cache keyed by modelId. Guarded by [lock]. Avoids re-decoding the JSON payload on every call.
    private val sessionsCache = HashMap<String, List<ChatHistorySession>>()

    suspend fun read(modelId: String): List<HistoryMessage> = locked {
        val available = sessionsLocked(modelId)
        val activeId = resolvedSessionId(modelId, available)
        available.firstOrNull { it.id == activeId }?.messages.orEmpty()
    }

    suspend fun currentSessionId(modelId: String): String? = locked {
        resolvedSessionId(modelId, sessionsLocked(modelId))
    }

    /**
     * Saves [messages] into the active session. When the active session already holds exactly these
     * messages the write is skipped, so repeated saves of an unchanged transcript (for example a stop
     * followed by completion) cost no encoding and no disk work. An invoked save finishes even if
     * its caller is cancelled while waiting for IO. A successful return means the preferences
     * commit finished; killing the process before that point can still lose the in-flight save.
     */
    suspend fun write(modelId: String, messages: List<HistoryMessage>) = persist(modelId, messages, null)

    /** Saves a captured transcript without changing a different active chat selected since capture. */
    suspend fun writeSession(modelId: String, sessionId: String?, messages: List<HistoryMessage>): String {
        val id = sessionId ?: UUID.randomUUID().toString()
        persist(modelId, messages, id)
        return id
    }

    private suspend fun persist(modelId: String, messages: List<HistoryMessage>, sessionId: String?) = withContext(NonCancellable) {
        locked {
            val trimmed = messages.takeLast(MAX_MESSAGES)
            val sessions = sessionsLocked(modelId)
            // Reuse restored legacy chats and recover missing or stale active-session pointers.
            val current = resolvedSessionId(modelId, sessions)
            val id = sessionId ?: current ?: UUID.randomUUID().toString()
            val activeId = if (sessionId == null) id else current ?: id
            if (currentId(modelId) == activeId && sessions.any { it.id == id && it.messages == trimmed }) return@locked
            val existing = sessions.filterNot { it.id == id }
            val title = messages.firstOrNull { it.fromUser && it.text.isNotBlank() }
                ?.text
                ?.replace("\n", " ")
                ?.take(TITLE_LENGTH)
                ?: "New local chat"
            val updated = (listOf(ChatHistorySession(id, title, System.currentTimeMillis(), trimmed)) + existing)
                .take(MAX_SESSIONS)
            saveSessionsLocked(modelId, updated, activeId)
        }
    }

    suspend fun startNewSession(modelId: String, id: String = UUID.randomUUID().toString()): String = mutate {
        val sessions = (listOf(ChatHistorySession(id, "New local chat", System.currentTimeMillis())) + sessionsLocked(modelId))
            .take(MAX_SESSIONS)
        saveSessionsLocked(modelId, sessions, id)
        id
    }

    suspend fun activate(modelId: String, sessionId: String) = mutate {
        if (sessionsLocked(modelId).any { it.id == sessionId }) {
            commit(preferences.edit().putString(currentKey(modelId), sessionId))
        }
    }

    suspend fun sessions(modelId: String): List<ChatHistorySession> = locked { sessionsLocked(modelId) }

    suspend fun clear(modelId: String): Unit = mutate {
        commit(
            preferences.edit()
                .remove(modelId)
                .remove(sessionsKey(modelId))
                .remove(currentKey(modelId)),
        )
        sessionsCache.remove(modelId)
    }

    /** Holds [lock] while [block] runs on IO. The lock is taken first so queued callers keep their order. */
    private suspend fun <T> locked(block: () -> T): T = lock.withLock {
        withContext(Dispatchers.IO) { block() }
    }

    private suspend fun <T> mutate(block: () -> T): T = withContext(NonCancellable) { locked(block) }

    private fun commit(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Local chat history could not be saved.")
    }

    private fun saveSessionsLocked(modelId: String, sessions: List<ChatHistorySession>, activeId: String) {
        val payload = json.encodeToString(sessions)
        commit(
            preferences.edit()
                .putString(sessionsKey(modelId), payload)
                .putString(currentKey(modelId), activeId),
        )
        sessionsCache[modelId] = sessions
    }

    private fun sessionsLocked(modelId: String): List<ChatHistorySession> {
        sessionsCache[modelId]?.let { return it }
        return loadSessions(modelId).also { sessionsCache[modelId] = it }
    }

    private fun loadSessions(modelId: String): List<ChatHistorySession> {
        val saved = preferences.getString(sessionsKey(modelId), null)
            ?.let { decodeSaved<List<ChatHistorySession>>(it) }
        if (saved != null) return saved.sortedByDescending { it.updatedAt }

        val legacy = preferences.getString(modelId, null)
            ?.let { decodeSaved<List<HistoryMessage>>(it) }
            .orEmpty()
        return if (legacy.isEmpty()) emptyList() else listOf(
            ChatHistorySession("legacy", "Previous local chat", 0L, legacy),
        )
    }

    private inline fun <reified T> decodeSaved(payload: String): T? = try {
        json.decodeFromString<T>(payload)
    } catch (_: IllegalArgumentException) {
        // Do not log the payload or exception message, which can contain conversation text.
        java.util.logging.Logger.getLogger(ChatHistoryStore::class.java.name)
            .warning("Saved chat history could not be decoded; trying the legacy history if available.")
        null
    }

    private fun resolvedSessionId(modelId: String, available: List<ChatHistorySession>): String? =
        currentId(modelId)?.takeIf { id -> available.any { it.id == id } }
            ?: available.firstOrNull()?.id

    private fun currentId(modelId: String): String? = preferences.getString(currentKey(modelId), null)

    private fun sessionsKey(modelId: String) = "sessions:$modelId"

    private fun currentKey(modelId: String) = "current:$modelId"

    private companion object {
        const val MAX_MESSAGES = 100
        const val MAX_SESSIONS = 20
        const val TITLE_LENGTH = 48
    }
}
