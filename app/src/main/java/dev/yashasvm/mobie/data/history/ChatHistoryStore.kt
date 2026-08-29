package dev.yashasvm.mobie.data.history

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryMessage(val fromUser: Boolean, val text: String, val imagePath: String? = null)

class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(modelId: String): List<HistoryMessage> = preferences.getString(modelId, null)
        ?.let { runCatching { json.decodeFromString<List<HistoryMessage>>(it) }.getOrNull() }
        .orEmpty()

    fun write(modelId: String, messages: List<HistoryMessage>) {
        preferences.edit().putString(modelId, json.encodeToString(messages.takeLast(MAX_MESSAGES))).apply()
    }

    fun clear(modelId: String) {
        preferences.edit().remove(modelId).apply()
    }

    private companion object { const val MAX_MESSAGES = 100 }
}
