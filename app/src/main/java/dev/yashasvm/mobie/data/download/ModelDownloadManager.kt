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

data class InstalledModelEntry(val model: AiModel, val localPath: String)

class ModelDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(context)

    fun completedFile(modelId: String, artifact: ModelArtifact): File? {
        val file = File(
            File(File(appContext.filesDir, "models"), DownloadFilePolicy.storageKey(modelId)),
            DownloadFilePolicy.safeFileName(artifact.fileName),
        )
        return file.takeIf { it.isFile && (artifact.sizeBytes <= 0 || it.length() == artifact.sizeBytes) }
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

    fun installedModels(): List<InstalledModelEntry> = File(appContext.filesDir, "models")
        .listFiles(File::isDirectory)
        .orEmpty()
        .mapNotNull { directory ->
            val metadata = File(directory, DownloadFilePolicy.METADATA_FILE).takeIf(File::isFile)
                ?: return@mapNotNull null
            val properties = Properties().apply { metadata.inputStream().use(::load) }
            val file = File(directory, properties.getProperty("fileName") ?: return@mapNotNull null)
                .takeIf(File::isFile) ?: return@mapNotNull null
            val artifact = ModelArtifact(
                fileName = file.name,
                downloadUrl = "",
                sizeBytes = file.length(),
                sha256 = properties.getProperty("sha256")?.ifBlank { null },
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
