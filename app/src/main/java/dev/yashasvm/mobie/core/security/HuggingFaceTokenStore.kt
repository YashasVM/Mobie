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

    fun hasCompletedOnboarding(): Boolean = preferences.getBoolean(KEY_ONBOARDED, false)

    fun hasSeenWelcome(): Boolean = preferences.getBoolean(KEY_WELCOME_SEEN, false)

    fun markWelcomeSeen() {
        preferences.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun completeOnboarding(token: String?) {
        preferences.edit().apply {
            if (token.isNullOrBlank()) remove(KEY) else putString(KEY, token.trim())
            putBoolean(KEY_ONBOARDED, true)
        }.apply()
    }

    private companion object {
        const val KEY = "access_token"
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_WELCOME_SEEN = "welcome_seen"
    }
}
