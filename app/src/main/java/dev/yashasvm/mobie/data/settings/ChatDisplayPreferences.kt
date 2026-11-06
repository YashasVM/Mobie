package dev.yashasvm.mobie.data.settings

import android.content.Context
import java.io.IOException

/** Nonsecret display choices. Call from the serialized preference IO dispatcher. */
class ChatDisplayPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy { appContext.getSharedPreferences("chat_display", Context.MODE_PRIVATE) }

    data class Saved(val statsForNerds: Boolean, val replyTokensPerSecond: Int?)

    fun read() = Saved(
        statsForNerds = preferences.getBoolean("stats_for_nerds", false),
        replyTokensPerSecond = preferences.getInt("reply_tokens_per_second", 0).takeIf { it in 5..100 },
    )

    fun writeStatsForNerds(enabled: Boolean) {
        if (!preferences.edit().putBoolean("stats_for_nerds", enabled).commit()) {
            throw IOException("Couldn't save the detail setting.")
        }
    }

    fun writeReplyTokensPerSecond(limit: Int?) {
        require(limit == null || limit in 5..100)
        if (!preferences.edit().putInt("reply_tokens_per_second", limit ?: 0).commit()) {
            throw IOException("Couldn't save the reply speed.")
        }
    }
}
