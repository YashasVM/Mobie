package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.DownloadSourceIdentity
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletedFileSourceIdentityTest {
    @Test
    fun checksumlessCompletedFileIsReusableOnlyForExactSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "mobie-test/completed-file-source-identity"
        val fileName = "identity-test.litertlm"
        val oldSource = "https://huggingface.co/example/model/resolve/oldsha/$fileName"
        val newSource = "https://huggingface.co/example/model/resolve/newsha/$fileName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val destination = File(modelDir, DownloadFilePolicy.storageFileName(fileName))
        destination.writeBytes(ByteArray(64 * 1024) { index -> (index * 29).toByte() })
        val metadataFile = File(modelDir, DownloadFilePolicy.METADATA_FILE)
        Properties().apply {
            setProperty("modelId", modelId)
            setProperty("fileName", destination.name)
            setProperty("sourceFileName", fileName)
            setProperty("installedLength", destination.length().toString())
            DownloadSourceIdentity.stamp(this, oldSource)
            metadataFile.outputStream().use { store(it, null) }
        }

        val downloads = ModelDownloadManager(context)
        val oldArtifact = ModelArtifact(
            fileName = fileName,
            downloadUrl = oldSource,
            sizeBytes = destination.length(),
            sha256 = null,
            format = ModelFormat.LITERT_LM,
        )
        val newArtifact = oldArtifact.copy(downloadUrl = newSource)

        assertEquals(destination.absolutePath, downloads.completedFile(modelId, oldArtifact)?.absolutePath)
        assertNull(
            "A same-size checksum-less file from an older Hub revision must not bypass the worker",
            downloads.completedFile(modelId, newArtifact),
        )

        modelDir.deleteRecursively()
        Unit
    }
}
