package dev.yashasvm.mobie.core

import android.content.Context

/**
 * Remembers the id of the installed model the user last chatted with, so launch can reopen it.
 * Model ids are public catalog identifiers, so plain private SharedPreferences is enough here.
 */
class LastUsedModelStore(context: Context) {
    private val preferences = context.getSharedPreferences("model_routing", Context.MODE_PRIVATE)

    fun read(): String? = preferences.getString(KEY, null)

    fun write(modelId: String) {
        preferences.edit().putString(KEY, modelId).apply()
    }

    private companion object {
        const val KEY = "last_used_model_id"
    }
}
