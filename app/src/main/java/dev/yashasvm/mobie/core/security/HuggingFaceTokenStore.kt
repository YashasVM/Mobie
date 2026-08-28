package dev.yashasvm.mobie.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class HuggingFaceTokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "hf_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): String? = preferences.getString(KEY, null)

    fun save(token: String?) {
        preferences.edit().apply {
            if (token.isNullOrBlank()) remove(KEY) else putString(KEY, token.trim())
        }.apply()
    }

    private companion object { const val KEY = "access_token" }
}
