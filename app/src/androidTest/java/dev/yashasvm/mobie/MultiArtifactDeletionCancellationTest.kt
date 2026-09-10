package dev.yashasvm.mobie

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiArtifactDeletionCancellationTest {
    @Test
    fun deletingModelCancelsEveryArtifactDownloadSharingItsStorage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ModelDownloadManager(context)
        val workManager = WorkManager.getInstance(context)
        val model = AiModel(
            id = "mobie-test/multi-artifact-delete",
            title = "Multi artifact delete",
            author = "Mobie",
            description = "Deletion cancellation integration test",
            artifacts = listOf(
                ModelArtifact(
                    fileName = "first.task",
                    downloadUrl = "https://127.0.0.1:1/first.task",
                    sizeBytes = 1024,
                    format = ModelFormat.LITERT_LM,
                ),
                ModelArtifact(
                    fileName = "second.task",
                    downloadUrl = "https://127.0.0.1:1/second.task",
                    sizeBytes = 2048,
                    format = ModelFormat.LITERT_LM,
                ),
            ),
        )

        val workIds = model.artifacts.map { manager.enqueue(model, it) }

        assertTrue(manager.deleteInstalled(model))

        workIds.forEach { id ->
            val info = workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS)
            assertEquals(WorkInfo.State.CANCELLED, info?.state)
        }
    }

    @Test
    fun deletingRecoveredInstallAcceptsArtifactMetadataWithoutCanonicalMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ModelDownloadManager(context)
        val artifact = ModelArtifact(
            fileName = "recovered.task",
            downloadUrl = "https://example.invalid/recovered.task",
            sizeBytes = 4,
            format = ModelFormat.LITERT_LM,
        )
        val model = AiModel(
            id = "mobie-test/artifact-metadata-delete",
            title = "Recovered delete",
            author = "Mobie",
            description = "Crash-recovered install deletion test",
            artifacts = listOf(artifact),
        )
        val directory = File(File(context.filesDir, "models"), sha256Prefix(model.id, 20))
        directory.deleteRecursively()
        assertTrue(directory.mkdirs())
        File(directory, "recovered.task").writeBytes(byteArrayOf(1, 2, 3, 4))
        val artifactMetadata = File(
            directory,
            ".artifact-${sha256Prefix(artifact.fileName, 12)}.properties",
        )
        artifactMetadata.outputStream().use { output ->
            Properties().apply {
                setProperty("modelId", model.id)
                setProperty("fileName", "recovered.task")
            }.store(output, null)
        }
        assertFalse(File(directory, ".model.properties").exists())

        assertTrue(manager.deleteInstalled(model))
        assertFalse(directory.exists())
    }

    private fun sha256Prefix(value: String, characters: Int): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(characters)
}
