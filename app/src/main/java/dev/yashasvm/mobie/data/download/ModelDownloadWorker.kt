package dev.yashasvm.mobie.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.yashasvm.mobie.core.security.HuggingFaceTokenStore
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (runAttemptCount >= MAX_ATTEMPTS) return@withContext Result.failure(dataOf("Download failed after $MAX_ATTEMPTS attempts"))
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val expectedSha = inputData.getString(KEY_SHA256)
        val expectedSize = inputData.getLong(KEY_SIZE, 0)
        val modelDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val destination = File(modelDir, fileName.substringAfterLast('/'))
        val partial = File(destination.path + ".part")
        val downloaded = partial.takeIf(File::exists)?.length() ?: 0

        try {
            if (expectedSize > 0 && expectedSize > usableSpace(modelDir)) {
                return@withContext Result.failure(dataOf("Not enough free storage"))
            }
            val request = Request.Builder().url(url).apply {
                if (downloaded > 0) header("Range", "bytes=$downloaded-")
                HuggingFaceTokenStore(applicationContext).read()?.let { header("Authorization", "Bearer $it") }
            }.build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.retry()
                val append = downloaded > 0 && response.code == 206
                if (!append && partial.exists()) partial.delete()
                val startAt = if (append) downloaded else 0
                RandomAccessFile(partial, "rw").use { output ->
                    output.seek(startAt)
                    val body = response.body ?: return@withContext Result.retry()
                    val total = expectedSize.takeIf { it > 0 } ?: (body.contentLength() + startAt)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var current = startAt
                        val startedAt = SystemClock.elapsedRealtime()
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            current += read
                            val elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1)
                            val speed = ((current - startAt) * 1000L) / elapsedMs
                            setProgress(
                                Data.Builder()
                                    .putLong(KEY_DOWNLOADED, current)
                                    .putLong(KEY_SIZE, total)
                                    .putLong(KEY_SPEED, speed)
                                    .build(),
                            )
                            if (isStopped) return@withContext Result.retry()
                        }
                    }
                }
            }
            if (!expectedSha.isNullOrBlank() && sha256(partial) != expectedSha.lowercase()) {
                partial.delete()
                return@withContext Result.failure(dataOf("Checksum validation failed"))
            }
            check(partial.renameTo(destination)) { "Could not finalize model file" }
            Result.success(Data.Builder().putString(KEY_PATH, destination.absolutePath).build())
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun usableSpace(dir: File): Long = dir.usableSpace

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

    private fun dataOf(message: String) = Data.Builder().putString(KEY_ERROR, message).build()

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_SPEED = "speed"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"
        const val MAX_ATTEMPTS = 4
    }
}
