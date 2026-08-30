package dev.yashasvm.mobie.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import java.util.UUID
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

data class DownloadProgress(
    val state: WorkInfo.State,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val localPath: String? = null,
    val error: String? = null,
)

val DownloadProgress.isCancellable: Boolean
    get() = state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)

data class InstalledModelEntry(val model: AiModel, val localPath: String)

class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(context)

    suspend fun completedFile(modelId: String, artifact: ModelArtifact): File? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val directory = File(File(appContext.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        listOf(DownloadFilePolicy.storageFileName(artifact.fileName), DownloadFilePolicy.safeFileName(artifact.fileName))
            .asSequence()
            .map { File(directory, it) }
            .firstOrNull { file ->
                file.isFile &&
                    (artifact.sizeBytes <= 0 || file.length() == artifact.sizeBytes) &&
                    (artifact.sha256.isNullOrBlank() || sha256(file) == artifact.sha256.lowercase())
            }
    }

    fun enqueue(modelId: String, artifact: ModelArtifact): UUID {
        return enqueue(
            AiModel(
                id = modelId,
                title = modelId.substringAfter('/'),
                author = modelId.substringBefore('/', "Unknown"),
                description = "Installed LiteRT-LM model",
                artifacts = listOf(artifact),
            ),
            artifact,
        )
    }

    fun enqueue(model: AiModel, artifact: ModelArtifact): UUID {
        val input = Data.Builder()
            .putString(ModelDownloadWorker.KEY_URL, artifact.downloadUrl)
            .putString(ModelDownloadWorker.KEY_MODEL_ID, model.id)
            .putString(ModelDownloadWorker.KEY_TITLE, model.title)
            .putString(ModelDownloadWorker.KEY_AUTHOR, model.author)
            .putString(ModelDownloadWorker.KEY_DESCRIPTION, model.description)
            .putString(ModelDownloadWorker.KEY_TYPE, model.type.name)
            .putString(ModelDownloadWorker.KEY_LICENSE, model.license)
            .putBoolean(ModelDownloadWorker.KEY_GATED, model.gated)
            .putString(ModelDownloadWorker.KEY_FILE_NAME, artifact.fileName)
            .putString(ModelDownloadWorker.KEY_QUANTIZATION, artifact.quantization)
            .putLong(ModelDownloadWorker.KEY_SIZE, artifact.sizeBytes)
            .apply { artifact.sha256?.let { putString(ModelDownloadWorker.KEY_SHA256, it) } }
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(input)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("model-download")
            .build()
        workManager.enqueueUniqueWork(workName(model.id, artifact), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    fun observe(modelId: String, artifact: ModelArtifact): Flow<DownloadProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(modelId, artifact)).map { work ->
            work.lastOrNull()?.toProgress()
        }

    fun observe(id: UUID): Flow<DownloadProgress> =
        workManager.getWorkInfoByIdFlow(id).filterNotNull().map { it.toProgress() }

    /** Stops the persisted WorkManager request while retaining its .part file for a later resume. */
    fun cancel(model: AiModel, artifact: ModelArtifact) = cancel(model.id, artifact)

    fun cancel(modelId: String, artifact: ModelArtifact) {
        workManager.cancelUniqueWork(workName(modelId, artifact))
    }

    fun installedModels(): List<InstalledModelEntry> = File(appContext.filesDir, "models")
        .listFiles(File::isDirectory)
        .orEmpty()
        .mapNotNull { directory ->
            val metadata = File(directory, DownloadFilePolicy.METADATA_FILE).takeIf(File::isFile)
                ?: return@mapNotNull null
            val properties = Properties().apply { metadata.inputStream().use(::load) }
            val file = File(directory, properties.getProperty("fileName") ?: return@mapNotNull null)
                .takeIf(File::isFile) ?: return@mapNotNull null
            val expectedSha = properties.getProperty("sha256")?.ifBlank { null }
            if (expectedSha != null && sha256(file) != expectedSha.lowercase()) return@mapNotNull null
            val artifact = ModelArtifact(
                fileName = file.name,
                downloadUrl = "",
                sizeBytes = file.length(),
                sha256 = expectedSha,
                format = ModelFormat.LITERT_LM,
                quantization = properties.getProperty("quantization")?.ifBlank { null },
            )
            InstalledModelEntry(
                model = AiModel(
                    id = properties.getProperty("modelId") ?: return@mapNotNull null,
                    title = properties.getProperty("title", file.nameWithoutExtension),
                    author = properties.getProperty("author", "Unknown"),
                    description = properties.getProperty("description", "Installed LiteRT-LM model"),
                    gated = properties.getProperty("gated").toBoolean(),
                    license = properties.getProperty("license")?.ifBlank { null },
                    type = runCatching { ModelType.valueOf(properties.getProperty("type")) }
                        .getOrDefault(ModelType.TEXT_GENERATION),
                    artifacts = listOf(artifact),
                ),
                localPath = file.absolutePath,
            )
        }
        .sortedBy { it.model.title.lowercase() }

    suspend fun deleteInstalled(model: AiModel): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val artifact = model.bestArtifact ?: return@withContext false
        cancel(model, artifact)
        val directory = File(File(appContext.filesDir, "models"), DownloadFilePolicy.storageKey(model.id))
        if (!directory.exists()) return@withContext true
        val metadata = File(directory, DownloadFilePolicy.METADATA_FILE)
        val storedId = metadata.takeIf(File::isFile)?.inputStream()?.use { input ->
            Properties().apply { load(input) }.getProperty("modelId")
        }
        storedId == model.id && directory.deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun WorkInfo.toProgress(): DownloadProgress {
        val data = if (state.isFinished) outputData else progress
        return DownloadProgress(
            state = state,
            downloadedBytes = data.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0),
            totalBytes = data.getLong(ModelDownloadWorker.KEY_SIZE, 0),
            bytesPerSecond = data.getLong(ModelDownloadWorker.KEY_SPEED, 0),
            localPath = outputData.getString(ModelDownloadWorker.KEY_PATH),
            error = outputData.getString(ModelDownloadWorker.KEY_ERROR),
        )
    }

    private fun workName(modelId: String, artifact: ModelArtifact) =
        "model-${modelId.hashCode()}-${artifact.fileName.hashCode()}"
}
