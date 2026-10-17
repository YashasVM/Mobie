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
 * Local chat history persisted in app-private SharedPreferences.
 *
 * All public operations run on [Dispatchers.IO] under one [Mutex]. The lock is taken before
 * dispatching to IO, so queued callers take effect in lock arrival order. Mutations finish even
 * if their caller is cancelled and return only after the disk commit completes.
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

    // Guarded by [lock]; avoids decoding unchanged transcripts on each read.
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
     * Saves into the active session, reusing restored legacy history. Identical transcripts skip
     * encoding and disk work. Empty transcripts are ignored. Successful return means the disk
     * commit finished; process death before then can still lose the in-flight save.
     */
    suspend fun write(modelId: String, messages: List<HistoryMessage>) {
        persist(modelId, messages, sessionId = null, useActiveSession = true)
    }

    /**
     * Saves a captured transcript and returns its id. An explicit [sessionId] never replaces a
     * different active chat selected since capture. A null id creates and selects a new chat.
     * Empty transcripts return an id without saving or changing the active chat.
     */
    suspend fun writeSession(modelId: String, sessionId: String?, messages: List<HistoryMessage>): String =
        persist(modelId, messages, sessionId, useActiveSession = false)

    private suspend fun persist(
        modelId: String,
        messages: List<HistoryMessage>,
        sessionId: String?,
        useActiveSession: Boolean,
    ): String = mutate {
        if (messages.isEmpty()) return@mutate sessionId ?: UUID.randomUUID().toString()
        val trimmed = messages.takeLast(MAX_MESSAGES)
        val available = sessionsLocked(modelId)
        val current = resolvedSessionId(modelId, available)
        // Older installs had no active-session key. Reuse their restored conversation rather
        // than creating a duplicate when the user sends the next message.
        val id = sessionId ?: current?.takeIf { useActiveSession } ?: UUID.randomUUID().toString()
        val activeId = if (useActiveSession || sessionId == null) id else current ?: id
        val previous = available.firstOrNull { it.id == id }
        if (previous != null && currentId(modelId) == activeId && previous.messages == trimmed) {
            return@mutate id
        }
        val session = ChatHistorySession(id, titleFor(messages), System.currentTimeMillis(), trimmed)
        val updated = (listOf(session) + available.filterNot { it.id == id }).take(MAX_SESSIONS)
        saveSessionsLocked(modelId, updated, activeId)
        id
    }

    /** Creates and selects an empty chat. Reusing an existing [id] keeps its messages. */
    suspend fun startNewSession(modelId: String, id: String = UUID.randomUUID().toString()): String = mutate {
        val available = sessionsLocked(modelId)
        val session = available.firstOrNull { it.id == id }
            ?: ChatHistorySession(id, DEFAULT_TITLE, System.currentTimeMillis())
        val updated = (listOf(session) + available.filterNot { it.id == id }).take(MAX_SESSIONS)
        saveSessionsLocked(modelId, updated, id)
        id
    }

    /** The model the user last opened a chat with; the app reopens it at launch. */
    suspend fun lastModelId(): String? = locked { preferences.getString(LAST_MODEL_KEY, null) }

    suspend fun setLastModelId(modelId: String) = mutate {
        if (preferences.getString(LAST_MODEL_KEY, null) != modelId) {
            commit(preferences.edit().putString(LAST_MODEL_KEY, modelId))
        }
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

    /** Takes the lock before dispatching to IO so queued callers keep their order. */
    private suspend fun <T> locked(block: () -> T): T = lock.withLock {
        withContext(Dispatchers.IO) { block() }
    }

    private suspend fun <T> mutate(block: () -> T): T = withContext(NonCancellable) { locked(block) }

    private fun commit(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("Local chat history could not be saved.")
    }

    private fun saveSessionsLocked(modelId: String, sessions: List<ChatHistorySession>, activeId: String) {
        commit(
            preferences.edit()
                .putString(sessionsKey(modelId), json.encodeToString(sessions))
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
            ChatHistorySession(LEGACY_SESSION_ID, "Previous local chat", 0L, legacy),
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

    companion object {
        const val LEGACY_SESSION_ID = "legacy"
        // Model ids contain a slash and never a colon, so this key cannot collide with a legacy history key.
        private const val LAST_MODEL_KEY = "meta:last_model"
        private const val DEFAULT_TITLE = "New local chat"
        private const val MAX_MESSAGES = 100
        private const val MAX_SESSIONS = 20
        private const val TITLE_LENGTH = 48

        /** The automatic title: the first user prompt on one line, or a plain default. */
        fun titleFor(messages: List<HistoryMessage>): String =
            messages.firstOrNull { it.fromUser && it.text.isNotBlank() }
                ?.text
                ?.replace("\n", " ")
                ?.take(TITLE_LENGTH)
                ?: DEFAULT_TITLE
    }
}
