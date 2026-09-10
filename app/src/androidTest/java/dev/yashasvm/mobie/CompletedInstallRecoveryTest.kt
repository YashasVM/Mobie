package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.DownloadSourceIdentity
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletedInstallRecoveryTest {
    @Test
    fun completedModelWithMatchingResumeIdentityRecoversWithoutRedownload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "mobie-test/completed-install-recovery"
        val fileName = "recovery-test.litertlm"
        val payload = ByteArray(256 * 1024) { index -> (index * 17).toByte() }
        val sourceUrl =
            "https://huggingface.co/mobie-test/recovery/resolve/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/$fileName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val destination = File(modelDir, DownloadFilePolicy.storageFileName(fileName))
        destination.writeBytes(payload)
        val resumeMetadata = File(destination.path + ".part" + DownloadSourceIdentity.RESUME_METADATA_SUFFIX)
        Properties().apply {
            DownloadSourceIdentity.stamp(this, sourceUrl)
            resumeMetadata.outputStream().use { store(it, null) }
        }

        val artifact = ModelArtifact(
            fileName = fileName,
            downloadUrl = sourceUrl,
            sizeBytes = payload.size.toLong(),
            sha256 = sha256(payload),
            format = ModelFormat.LITERT_LM,
        )
        val downloads = ModelDownloadManager(context)
        val requestId = downloads.enqueue(modelId, artifact)
        val result = withTimeout(20_000L) {
            downloads.observe(requestId).first { it.state.isFinished }
        }

        assertEquals("Interrupted install should recover locally", WorkInfo.State.SUCCEEDED, result.state)
        assertArrayEquals(payload, destination.readBytes())
        assertFalse("Recovery sidecar should be removed after metadata commit", resumeMetadata.exists())

        val metadataFile = File(modelDir, DownloadFilePolicy.METADATA_FILE)
        assertTrue(metadataFile.isFile)
        val metadata = Properties().apply { metadataFile.inputStream().use(::load) }
        assertEquals(sourceUrl, metadata.getProperty(DownloadSourceIdentity.SOURCE_URL_PROPERTY))
        assertEquals(destination.length().toString(), metadata.getProperty("verifiedLength"))

        modelDir.deleteRecursively()
        Unit
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
