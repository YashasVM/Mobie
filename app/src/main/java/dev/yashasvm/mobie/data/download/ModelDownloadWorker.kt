package dev.yashasvm.mobie.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.yashasvm.mobie.R
import dev.yashasvm.mobie.core.security.HuggingFaceTokenStore
import dev.yashasvm.mobie.data.catalog.awaitResponse
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (runAttemptCount >= MAX_ATTEMPTS) {
            return@withContext Result.failure(dataOf("Download failed after $MAX_ATTEMPTS attempts"))
        }

        val url = inputData.getString(KEY_URL) ?: return@withContext invalidInput("Missing download URL")
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext invalidInput("Missing model ID")
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext invalidInput("Missing file name")
        val expectedSha = inputData.getString(KEY_SHA256)?.lowercase()
        val expectedSize = inputData.getLong(KEY_SIZE, 0)
        setForeground(createForegroundInfo(fileName, totalBytes = expectedSize))

        val modelDir = File(
            File(applicationContext.filesDir, "models"),
            DownloadFilePolicy.storageKey(modelId),
        ).apply { mkdirs() }
        val storageDestination = File(modelDir, DownloadFilePolicy.storageFileName(fileName))
        val partial = File(storageDestination.path + ".part")

        if (inputData.getBoolean(KEY_GATED, false) && HuggingFaceTokenStore(applicationContext).read().isNullOrBlank()) {
            return@withContext Result.failure(dataOf("This gated model requires a Hugging Face token"))
        }

        try {
            if (isComplete(storageDestination, expectedSize, expectedSha)) {
                return@withContext success(storageDestination)
            }
            if (storageDestination.exists()) storageDestination.delete()

            var downloaded = partial.takeIf(File::exists)?.length() ?: 0
            if (expectedSize > 0 && downloaded > expectedSize) {
                partial.delete()
                downloaded = 0
            }
            if (isComplete(partial, expectedSize, expectedSha)) {
                finalizeFile(partial, storageDestination)
                return@withContext success(storageDestination)
            }

            val remaining = DownloadFilePolicy.remainingBytes(expectedSize, downloaded)
            if (expectedSize > 0 && remaining > modelDir.usableSpace) {
                return@withContext Result.failure(dataOf("Not enough free storage"))
            }

            val request = Request.Builder().url(url).apply {
                if (downloaded > 0) header("Range", "bytes=$downloaded-")
                HuggingFaceTokenStore(applicationContext).read()?.let { header("Authorization", "Bearer $it") }
            }.build()

            var transferTotal = expectedSize
            client.newCall(request).awaitResponse().use { response ->
                when (response.code) {
                    401, 403 -> return@withContext Result.failure(
                        dataOf("Access denied. Check the model terms and Hugging Face token."),
                    )
                    404 -> return@withContext Result.failure(dataOf("Model artifact was not found"))
                    416 -> {
                        if (isComplete(partial, expectedSize, expectedSha)) {
                            finalizeFile(partial, storageDestination)
                            return@withContext success(storageDestination)
                        }
                        partial.delete()
                        return@withContext Result.retry()
                    }
                }
                if (DownloadResponsePolicy.isRetryableHttp(response.code)) return@withContext Result.retry()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(dataOf("Download failed with HTTP ${response.code}"))
                }

                val isPartialResponse = response.code == 206
                if (isPartialResponse && !DownloadResponsePolicy.isValidResumeResponse(
                        contentRangeHeader = response.header("Content-Range"),
                        expectedStart = downloaded,
                        expectedTotalBytes = expectedSize,
                    )
                ) {
                    partial.delete()
                    return@withContext Result.retry()
                }

                val append = downloaded > 0 && isPartialResponse
                if (!append && partial.exists()) {
                    partial.delete()
                    downloaded = 0
                }

                val body = response.body ?: return@withContext Result.retry()
                val startAt = if (append) downloaded else 0
                transferTotal = DownloadResponsePolicy.resolvedTotalBytes(
                    expectedTotalBytes = expectedSize,
                    contentRangeHeader = response.header("Content-Range"),
                    bodyLength = body.contentLength(),
                    startAt = startAt,
                )

                RandomAccessFile(partial, "rw").use { output ->
                    output.seek(startAt)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var current = startAt
                        var lastProgressAt = 0L
                        val startedAt = SystemClock.elapsedRealtime()
                        updateForeground(fileName, current, transferTotal)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            current += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                                val elapsedMs = (now - startedAt).coerceAtLeast(1)
                                updateForeground(
                                    fileName,
                                    current,
                                    transferTotal,
                                    ((current - startAt) * 1000L) / elapsedMs,
                                )
                                lastProgressAt = now
                            }
                        }
                    }
                }
            }

            if (transferTotal > 0 && partial.length() != transferTotal) return@withContext Result.retry()
            if (!expectedSha.isNullOrBlank() && sha256(partial) != expectedSha) {
                partial.delete()
                return@withContext Result.failure(dataOf("Checksum validation failed"))
            }
            finalizeFile(partial, storageDestination)
            success(storageDestination)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(dataOf(error.message?.take(160) ?: "Download failed"))
        }
    }

    private fun isComplete(file: File, expectedSize: Long, expectedSha: String?): Boolean {
        if (!file.isFile) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        return when {
            !expectedSha.isNullOrBlank() -> sha256(file) == expectedSha
            expectedSize > 0 -> true
            else -> false
        }
    }

    private fun finalizeFile(partial: File, destination: File) {
        try {
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
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

    private suspend fun updateForeground(fileName: String, downloaded: Long, total: Long, speed: Long = 0) {
        setProgress(progressData(downloaded, total, speed))
        setForeground(createForegroundInfo(fileName, downloaded, total))
    }

    private fun createForegroundInfo(fileName: String, downloadedBytes: Long = 0, totalBytes: Long = 0): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val hasTotal = totalBytes > 0
        val progress = if (hasTotal) {
            (downloadedBytes.toDouble() / totalBytes * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val safeName = DownloadFilePolicy.safeFileName(fileName)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading model")
            .setContentText(if (hasTotal) "$safeName · $progress%" else safeName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, !hasTotal)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId(), notification)
        }
    }

    private fun notificationId(): Int = (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun progressData(downloaded: Long, total: Long, speed: Long) = Data.Builder()
        .putLong(KEY_DOWNLOADED, downloaded)
        .putLong(KEY_SIZE, total)
        .putLong(KEY_SPEED, speed)
        .build()

    private fun success(destination: File): Result {
        writeMetadata(destination)
        return Result.success(
            Data.Builder()
                .putString(KEY_PATH, destination.absolutePath)
                .putLong(KEY_DOWNLOADED, destination.length())
                .putLong(KEY_SIZE, destination.length())
                .build(),
        )
    }

    private fun writeMetadata(destination: File) {
        val expectedSha = inputData.getString(KEY_SHA256).orEmpty()
        val properties = Properties().apply {
            setProperty("modelId", inputData.getString(KEY_MODEL_ID).orEmpty())
            setProperty("title", inputData.getString(KEY_TITLE).orEmpty())
            setProperty("author", inputData.getString(KEY_AUTHOR).orEmpty())
            setProperty("description", inputData.getString(KEY_DESCRIPTION).orEmpty())
            setProperty("type", inputData.getString(KEY_TYPE).orEmpty())
            setProperty("license", inputData.getString(KEY_LICENSE).orEmpty())
            setProperty("gated", inputData.getBoolean(KEY_GATED, false).toString())
            setProperty("fileName", destination.name)
            setProperty("sourceFileName", inputData.getString(KEY_FILE_NAME).orEmpty())
            setProperty("sha256", expectedSha)
            setProperty("quantization", inputData.getString(KEY_QUANTIZATION).orEmpty())
            if (expectedSha.isNotBlank()) ModelFileVerification.stamp(this, destination)
        }
        val metadata = File(destination.parentFile, DownloadFilePolicy.METADATA_FILE)
        val partial = File(metadata.path + ".part")
        partial.outputStream().use { properties.store(it, null) }
        finalizeFile(partial, metadata)
    }

    private fun invalidInput(message: String) = Result.failure(dataOf(message))
    private fun dataOf(message: String) = Data.Builder().putString(KEY_ERROR, message).build()

    companion object {
        const val KEY_URL = "url"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_TITLE = "title"
        const val KEY_AUTHOR = "author"
        const val KEY_DESCRIPTION = "description"
        const val KEY_TYPE = "type"
        const val KEY_LICENSE = "license"
        const val KEY_GATED = "gated"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_QUANTIZATION = "quantization"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_SPEED = "speed"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"
        const val MAX_ATTEMPTS = 4
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val CHANNEL_ID = "model_downloads"
    }
}
