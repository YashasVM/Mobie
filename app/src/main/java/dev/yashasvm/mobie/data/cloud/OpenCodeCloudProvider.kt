package dev.yashasvm.mobie.data.cloud

import dev.yashasvm.mobie.core.model.CloudModel
import dev.yashasvm.mobie.core.security.CloudConnection
import dev.yashasvm.mobie.core.runtime.CloudChatProvider
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Paired Linux gateway. The gateway runs genuine OpenCode; the phone never calls its free API directly. */
class OpenCodeCloudProvider(
    client: OkHttpClient,
    private val connection: suspend () -> CloudConnection?,
) : CloudChatProvider {
    private val http = client.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()

    override suspend fun models(): List<CloudModel> = withContext(Dispatchers.IO) {
        val paired = requireNotNull(connection()) { "Connect your Linux box in Settings first." }
        val call = http.newCall(Request.Builder().url("${paired.baseUrl}/v1/models")
            .header("User-Agent", "Mobie/0.2.1")
            .header("Authorization", "Bearer ${paired.token}").build())
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException(serviceError(response))
            val body = response.body ?: throw IOException("OpenCode returned an empty catalog.")
            val data = Json.parseToJsonElement(body.string()).jsonObject["data"]?.jsonArray
                ?: throw IOException("OpenCode returned an unreadable catalog.")
            data.mapNotNull { entry ->
                val metadata = entry as? JsonObject ?: return@mapNotNull null
                val id = (metadata["id"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { MODEL_ID.matches(it) } ?: return@mapNotNull null
                CloudModel(
                    id = id,
                    title = (metadata["title"] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.isNotBlank() } ?: id,
                    supportsImages = (metadata["images"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    privacyNotice = (metadata["privacy_notice"] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.isNotBlank() } ?: DEFAULT_PRIVACY,
                )
            }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
        }
    }

    override fun generate(model: CloudModel, messages: List<RuntimeMessage>): Flow<InferenceEvent> = callbackFlow {
        require(MODEL_ID.matches(model.id)) { "This cloud model isn't supported." }
        require(messages.isNotEmpty()) { "Write a message first." }
        val paired = requireNotNull(connection()) { "Connect your Linux box in Settings first." }
        val payload = requestPayload(model, messages)
        val request = Request.Builder().url("${paired.baseUrl}/v1/chat/completions")
            .header("User-Agent", "Mobie/0.2.1")
            .header("Authorization", "Bearer ${paired.token}")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val call = http.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySendBlocking(InferenceEvent.Error("Couldn't reach your Linux box. Check the server address and your connection."))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) {
                            trySendBlocking(InferenceEvent.Error(serviceError(response)))
                            return
                        }
                        val source = response.body?.source() ?: throw IOException("Empty response")
                        var finished = false
                        var frame = StringBuilder()
                        fun dispatch(): Boolean {
                            if (frame.isEmpty()) return true
                            val data = frame.toString()
                            frame = StringBuilder()
                            if (data == "[DONE]") {
                                finished = true
                                return false
                            }
                            val chunk = Json.parseToJsonElement(data).jsonObject
                            val cloudError = chunk["error"] as? JsonObject
                            if (cloudError != null) {
                                val message = when (cloudError["code"]?.jsonPrimitive?.contentOrNull) {
                                    "provider_access_denied" -> "OpenCode on your Linux box could not access this model. Check the server's OpenCode service."
                                    "provider_rate_limit" -> "OpenCode's usage limit was reached on your Linux box. Try again later."
                                    else -> "OpenCode on your Linux box could not finish this reply. Try again or start a new chat."
                                }
                                throw CloudStreamFailure(message)
                            }
                            val choice = chunk["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return true
                            val delta = choice["delta"]?.jsonObject
                            for ((key, thinking) in listOf("reasoning_content" to true, "content" to false)) {
                                val value = (delta?.get(key) as? JsonPrimitive)?.contentOrNull
                                if (!value.isNullOrEmpty() && !trySendBlocking(InferenceEvent.Token(value, thinking)).isSuccess) return false
                            }
                            val reason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
                            if (reason != null) {
                                if (reason !in setOf("stop", "length")) throw IOException("Unsupported finish reason")
                                finished = true
                                return false
                            }
                            return true
                        }
                        while (!call.isCanceled()) {
                            val line = source.readUtf8LineStrict(1_048_576)
                            if (line.isEmpty()) {
                                if (!dispatch()) break
                            } else if (line.startsWith("data:")) {
                                if (frame.isNotEmpty()) frame.append('\n')
                                frame.append(line.removePrefix("data:").trimStart())
                                if (frame.length > 1_048_576) throw IOException("Oversized event")
                            }
                        }
                        if (finished) trySendBlocking(InferenceEvent.Complete)
                        else throw IOException("Incomplete stream")
                    } catch (error: Exception) {
                        trySendBlocking(InferenceEvent.Error(if (error is CloudStreamFailure) error.message.orEmpty()
                            else "OpenCode's reply was interrupted or unreadable. Try again."))
                    } finally {
                        close()
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(InferenceEvent.Error(if (error is IllegalArgumentException) error.message ?: "Unsupported request." else "Couldn't prepare this cloud request. Reattach any missing photos and try again."))
    }.flowOn(Dispatchers.IO)

    private fun requestPayload(model: CloudModel, messages: List<RuntimeMessage>): JsonObject {
        var photoBytes = 0L
        return buildJsonObject {
            put("model", model.id)
            put("stream", true)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                messages.filter { it.text.isNotBlank() || it.imagePath != null }.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.fromUser) "user" else "assistant")
                        if (message.imagePath == null) put("content", message.text)
                        else {
                            require(message.fromUser && model.supportsImages) { "This cloud model can't read photos." }
                            val file = File(message.imagePath)
                            require(file.length() in 1..MAX_IMAGE_BYTES) { "Attach a photo smaller than 20 MB." }
                            val bytes = file.inputStream().use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    require(output.size().toLong() + count <= MAX_IMAGE_BYTES) { "Attach a photo smaller than 20 MB." }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            photoBytes += bytes.size
                            require(photoBytes <= MAX_IMAGE_BYTES) { "This conversation has more than 20 MB of photos. Start a new chat." }
                            val mime = imageMime(bytes)
                            putJsonArray("content") {
                                add(buildJsonObject { put("type", "text"); put("text", message.text) })
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", "data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}") }
                                })
                            }
                        }
                    })
                }
            }
        }
    }

    private fun imageMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> throw IllegalArgumentException("Use a JPEG, PNG or WebP photo for cloud chat.")
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Couldn't reach your Linux box. Check the server address and your connection."))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun serviceError(response: Response): String = when (response.code) {
        429 -> {
            val seconds = response.header("Retry-After")?.toLongOrNull()?.takeIf { it > 0 }
            if (seconds == null) "OpenCode's usage limit was reached. Try later or choose another model."
            else "OpenCode's usage limit was reached. Try again in $seconds seconds."
        }
        401 -> "Your Linux box rejected the pairing key. Save the current key in Settings."
        403 -> {
            // Inspect only enough to recognize known denials; never display or log provider bodies.
            val denial = response.peekBody(16_384).string().lowercase()
            when {
                "freetiererror" in denial || "can only be used from within opencode" in denial ->
                    "OpenCode restricts this free model to its own app. Direct access from Mobie is unavailable."
                "cloudflare" in denial || "error code: 1010" in denial ->
                    "OpenCode's security service blocked this request (HTTP 403). This is an access restriction, not a confirmed rate limit."
                else -> "Your Linux box refused access (HTTP 403). Check its OpenCode connection."
            }
        }
        404, 410 -> "This model is no longer available on OpenCode. Refresh cloud models."
        else -> "Your Linux box could not complete this request (HTTP ${response.code}). Check its OpenCode service."
    }

    private class CloudStreamFailure(message: String) : IOException(message)

    companion object {
        private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        private val MODEL_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private const val DEFAULT_PRIVACY = "OpenCode provider privacy and retention policies apply. " +
            "Some free models use submissions for improvement. Avoid personal or confidential information."
    }
}
