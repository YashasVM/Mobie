package dev.yashasvm.mobie.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import java.util.UUID
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

class ModelDownloadManager(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(modelId: String, artifact: ModelArtifact): UUID {
        val input = Data.Builder()
            .putString(ModelDownloadWorker.KEY_URL, artifact.downloadUrl)
            .putString(ModelDownloadWorker.KEY_MODEL_ID, modelId)
            .putString(ModelDownloadWorker.KEY_FILE_NAME, artifact.fileName)
            .putLong(ModelDownloadWorker.KEY_SIZE, artifact.sizeBytes)
            .apply { artifact.sha256?.let { putString(ModelDownloadWorker.KEY_SHA256, it) } }
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(input)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("model-download")
            .build()
        val workName = "model-${modelId.hashCode()}-${artifact.fileName.hashCode()}"
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun observe(id: UUID): Flow<DownloadProgress> = workManager.getWorkInfoByIdFlow(id).filterNotNull().map { work ->
        val data = if (work.state.isFinished) work.outputData else work.progress
        DownloadProgress(
            state = work.state,
            downloadedBytes = data.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0),
            totalBytes = data.getLong(ModelDownloadWorker.KEY_SIZE, 0),
            bytesPerSecond = data.getLong(ModelDownloadWorker.KEY_SPEED, 0),
            localPath = work.outputData.getString(ModelDownloadWorker.KEY_PATH),
            error = work.outputData.getString(ModelDownloadWorker.KEY_ERROR),
        )
    }
}
