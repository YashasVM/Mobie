package dev.yashasvm.mobie.data.catalog

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.core.security.HuggingFaceTokenStore
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

class HuggingFaceCatalogRepository(
    private val client: OkHttpClient,
    private val tokenStore: HuggingFaceTokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun featured(): Result<List<AiModel>> = fetchModels(
        "https://huggingface.co/api/models?author=litert-community&sort=downloads&direction=-1&limit=60&full=true",
    )

    suspend fun search(query: String): Result<List<AiModel>> {
        if (query.isBlank()) return Result.success(emptyList())
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return fetchModels("https://huggingface.co/api/models?author=litert-community&search=$encoded&limit=60&full=true")
    }

    private suspend fun fetchModels(url: String): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).apply {
                tokenStore.read()?.let { header("Authorization", "Bearer $it") }
            }.build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Hugging Face returned HTTP ${response.code}" }
                val summaries = json.decodeFromString<List<HfModel>>(checkNotNull(response.body).string())
                    .filter { it.hasLiteRtArtifact() }
                    .take(30)
                val limiter = Semaphore(6)
                val models = coroutineScope {
                    summaries.map { summary ->
                        async {
                            limiter.withPermit { fetchDetails(summary.repoId()) ?: summary }
                        }
                    }.awaitAll()
                }
                models.mapNotNull(HfModel::toDomain).filter { model ->
                    model.artifacts.any { it.format == ModelFormat.LITERT_LM && it.sizeBytes > 0 } &&
                        model.type in setOf(ModelType.TEXT_GENERATION, ModelType.VISION)
                }
            }
        }
    }

    private fun fetchDetails(repoId: String): HfModel? {
        val url = "https://huggingface.co".toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegment("models")
            repoId.split('/').forEach(::addPathSegment)
            addQueryParameter("blobs", "true")
        }.build()
        val request = Request.Builder().url(url).apply {
            tokenStore.read()?.let { header("Authorization", "Bearer $it") }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()?.let(json::decodeFromString)
        }
    }
}

@Serializable
private data class HfModel(
    @SerialName("modelId") val modelId: String? = null,
    val id: String? = null,
    val author: String? = null,
    val downloads: Long? = null,
    val gated: JsonElement? = null,
    val pipeline_tag: String? = null,
    val tags: List<String> = emptyList(),
    val siblings: List<HfSibling> = emptyList(),
) {
    fun repoId(): String = modelId ?: id.orEmpty()

    fun hasLiteRtArtifact(): Boolean = siblings.any { it.rfilename.endsWith(".litertlm", ignoreCase = true) }

    fun toDomain(): AiModel? {
        val repoId = modelId ?: id ?: return null
        val artifacts = siblings.mapNotNull { file ->
            val format = when {
                file.rfilename.endsWith(".litertlm", ignoreCase = true) -> ModelFormat.LITERT_LM
                else -> return@mapNotNull null
            }
            val size = file.size ?: file.lfs?.size ?: return@mapNotNull null
            ModelArtifact(
                fileName = file.rfilename,
                downloadUrl = artifactUrl(repoId, file.rfilename),
                sizeBytes = size,
                sha256 = file.lfs?.oid?.removePrefix("sha256:"),
                format = format,
                quantization = quantizationFrom(file.rfilename),
            )
        }
        val type = when (pipeline_tag) {
            "text-generation", "text2text-generation" -> ModelType.TEXT_GENERATION
            "feature-extraction", "sentence-similarity" -> ModelType.EMBEDDING
            "image-text-to-text", "image-classification" -> ModelType.VISION
            "automatic-speech-recognition", "text-to-speech" -> ModelType.AUDIO
            else -> ModelType.UNKNOWN
        }
        return AiModel(
            id = repoId,
            title = repoId.substringAfter('/'),
            author = author ?: repoId.substringBefore('/', "Unknown"),
            description = tags.take(4).joinToString(" · ").ifBlank { "Hugging Face model" },
            downloads = downloads ?: 0,
            gated = gated?.toString()?.trim('"') !in listOf(null, "false"),
            license = tags.firstOrNull { it.startsWith("license:") }?.substringAfter(':'),
            type = type,
            artifacts = artifacts,
        )
    }

    private fun quantizationFrom(name: String): String? =
        Regex("(?i)(Q[2-8](?:_[A-Z0-9]+)?)").find(name)?.value?.uppercase()

    private fun artifactUrl(repoId: String, fileName: String): String =
        "https://huggingface.co".toHttpUrl().newBuilder().apply {
            repoId.split('/').filter(String::isNotBlank).forEach(::addPathSegment)
            addPathSegment("resolve")
            addPathSegment("main")
            fileName.split('/').filter(String::isNotBlank).forEach(::addPathSegment)
        }.build().toString()
}

@Serializable
private data class HfSibling(
    val rfilename: String,
    val size: Long? = null,
    val lfs: HfLfs? = null,
)

@Serializable
private data class HfLfs(val oid: String? = null, val size: Long? = null)
