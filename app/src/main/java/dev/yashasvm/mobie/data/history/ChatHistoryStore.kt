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
    /** True once the user renamed the chat, so later writes keep their title. */
    val customTitle: Boolean = false,
    /** The model's display name when the chat was last saved; shown even after the model is deleted. */
    val modelTitle: String? = null,
)

/** One saved chat together with the model it belongs to, for the cross-model chats list. */
data class ChatThread(val modelId: String, val session: ChatHistorySession)

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
        persist(modelId, messages, sessionId = null, modelTitle = null, useActiveSession = true)
    }

    /**
     * Saves a captured transcript and returns its id. An explicit [sessionId] never replaces a
     * different active chat selected since capture. A null id creates and selects a new chat.
     * Empty transcripts return an id without saving or changing the active chat.
     */
    suspend fun writeSession(
        modelId: String,
        sessionId: String?,
        messages: List<HistoryMessage>,
        modelTitle: String? = null,
    ): String = persist(modelId, messages, sessionId, modelTitle, useActiveSession = false)

    private suspend fun persist(
        modelId: String,
        messages: List<HistoryMessage>,
        sessionId: String?,
        modelTitle: String?,
        useActiveSession: Boolean,
    ): String = mutate {
        if (messages.isEmpty()) return@mutate sessionId ?: UUID.randomUUID().toString()
        val trimmed = messages.takeLast(MAX_MESSAGES)
        val available = sessionsLocked(modelId)
        val current = resolvedSessionId(modelId, available)
        val id = sessionId ?: current?.takeIf { useActiveSession } ?: UUID.randomUUID().toString()
        val activeId = if (useActiveSession || sessionId == null) id else current ?: id
        val previous = available.firstOrNull { it.id == id }
        val savedModelTitle = modelTitle ?: previous?.modelTitle
        if (previous != null && currentId(modelId) == activeId && previous.messages == trimmed && previous.modelTitle == savedModelTitle) {
            return@mutate id
        }
        val session = ChatHistorySession(
            id = id,
            title = previous?.takeIf { it.customTitle }?.title ?: titleFor(messages),
            updatedAt = System.currentTimeMillis(),
            messages = trimmed,
            customTitle = previous?.customTitle == true,
            modelTitle = savedModelTitle,
        )
        val updated = (listOf(session) + available.filterNot { it.id == id }).take(MAX_SESSIONS)
        saveSessionsLocked(modelId, updated, activeId)
        id
    }

    /** Kept for older callers. New draft chats should wait for their first message before saving. */
    suspend fun startNewSession(modelId: String, id: String = UUID.randomUUID().toString()): String = mutate {
        val available = sessionsLocked(modelId)
        val session = available.firstOrNull { it.id == id }
            ?: ChatHistorySession(id, DEFAULT_TITLE, System.currentTimeMillis())
        val updated = (listOf(session) + available.filterNot { it.id == id }).take(MAX_SESSIONS)
        saveSessionsLocked(modelId, updated, id)
        id
    }

    suspend fun activate(modelId: String, sessionId: String) = mutate {
        if (sessionsLocked(modelId).any { it.id == sessionId }) {
            commit(preferences.edit().putString(currentKey(modelId), sessionId))
        }
    }

    suspend fun sessions(modelId: String): List<ChatHistorySession> = locked { sessionsLocked(modelId) }

    suspend fun session(modelId: String, sessionId: String): ChatHistorySession? = locked {
        sessionsLocked(modelId).firstOrNull { it.id == sessionId }
    }

    /** Every saved chat with at least one message, across all models, newest first. */
    suspend fun allSessions(): List<ChatThread> = locked {
        historyModelIds(preferences.all.keys)
            .flatMap { modelId -> sessionsLocked(modelId).map { ChatThread(modelId, it) } }
            .filter { it.session.messages.isNotEmpty() }
            .sortedByDescending { it.session.updatedAt }
    }

    /** Renames a chat; a blank title is ignored. Returns true when a session was renamed. */
    suspend fun rename(modelId: String, sessionId: String, title: String): Boolean = mutate {
        val cleaned = title.replace("\n", " ").trim().take(MAX_TITLE_LENGTH)
        if (cleaned.isEmpty()) return@mutate false
        val available = sessionsLocked(modelId)
        if (available.none { it.id == sessionId }) return@mutate false
        val updated = available.map { if (it.id == sessionId) it.copy(title = cleaned, customTitle = true) else it }
        saveSessionsLocked(modelId, updated, currentId(modelId))
        true
    }

    /** Deletes one chat, clears its active pointer, and retires any legacy single-chat payload. */
    suspend fun delete(modelId: String, sessionId: String): Boolean = mutate {
        val available = sessionsLocked(modelId)
        if (available.none { it.id == sessionId }) return@mutate false
        val remaining = available.filterNot { it.id == sessionId }
        val pointer = currentId(modelId)?.takeIf { it != sessionId }
        saveSessionsLocked(modelId, remaining, pointer, dropLegacy = true)
        true
    }

    suspend fun lastModelId(): String? = locked { preferences.getString(LAST_MODEL_KEY, null) }

    suspend fun setLastModelId(modelId: String) = mutate {
        if (preferences.getString(LAST_MODEL_KEY, null) != modelId) {
            commit(preferences.edit().putString(LAST_MODEL_KEY, modelId))
        }
    }

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

    private fun saveSessionsLocked(
        modelId: String,
        sessions: List<ChatHistorySession>,
        activeId: String?,
        dropLegacy: Boolean = false,
    ) {
        val payload = json.encodeToString(sessions)
        commit(
            preferences.edit().apply {
                putString(sessionsKey(modelId), payload)
                if (activeId != null) putString(currentKey(modelId), activeId) else remove(currentKey(modelId))
                if (dropLegacy) remove(modelId)
            },
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
