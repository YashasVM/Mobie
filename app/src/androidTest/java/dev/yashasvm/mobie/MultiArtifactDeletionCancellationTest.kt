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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
