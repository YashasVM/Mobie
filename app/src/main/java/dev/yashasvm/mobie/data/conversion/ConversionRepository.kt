package dev.yashasvm.mobie.data.conversion

import dev.yashasvm.mobie.BuildConfig
import dev.yashasvm.mobie.core.model.ConversionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConversionRepository(
    private val client: OkHttpClient,
) {
    suspend fun request(modelId: String): Result<ConversionStatus> = withContext(Dispatchers.IO) {
        if (BuildConfig.CONVERSION_API_URL.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Model conversion is not configured"))
        }
        runCatching {
            val body = Json.encodeToString(mapOf("modelId" to modelId))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(BuildConfig.CONVERSION_API_URL).post(body).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Conversion service returned HTTP ${response.code}" }
            }
            ConversionStatus.REQUESTED
        }
    }
}
