package dev.yashasvm.mobie.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Credentials for the user's own OpenCode gateway. Never include these in logs or exports. */
data class CloudConnection(val baseUrl: String, val token: String) {
    override fun toString(): String = "CloudConnection(credentials=redacted)"
}

/** Construct and call this store on an IO dispatcher, including first access to its lazy owner. */
class CloudConnectionStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "cloud_gateway_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): CloudConnection? {
        val baseUrl = preferences.getString(KEY_URL, null) ?: return null
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        return validatedConnection(baseUrl, token)
    }

    fun save(baseUrl: String, token: String) {
        val connection = validatedConnection(baseUrl, token)
        check(preferences.edit().putString(KEY_URL, connection.baseUrl)
            .putString(KEY_TOKEN, connection.token).commit()) { "Could not save the cloud connection." }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not remove the cloud connection." }
    }

    companion object {
        private const val KEY_URL = "gateway_url"
        private const val KEY_TOKEN = "pairing_token"

        fun validatedConnection(baseUrl: String, token: String): CloudConnection {
            val url = baseUrl.trim().toHttpUrlOrNull()
            require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                url.query == null && url.fragment == null) {
                "Enter an HTTPS server address without login details, query parameters or a fragment."
            }
            val pairingToken = token.trim()
            require(pairingToken.isNotEmpty() && pairingToken.all { it.code in 33..126 }) {
                "Enter the pairing key shown on your Linux box."
            }
            return CloudConnection(url.toString().trimEnd('/'), pairingToken)
        }
    }
}
