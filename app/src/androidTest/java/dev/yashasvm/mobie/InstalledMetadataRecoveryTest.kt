package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.download.DownloadSourceIdentity
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import dev.yashasvm.mobie.data.download.ModelFileVerification
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledMetadataRecoveryTest {
    @Test
    fun installedModelsRepairsMissingCanonicalMetadataFromArtifactRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "mobie-test/artifact-metadata-recovery"
        val sourceFileName = "recovered-model.litertlm"
        val sourceUrl = "https://huggingface.co/mobie-test/recovery/resolve/0123456789abcdef/$sourceFileName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val modelFile = File(modelDir, DownloadFilePolicy.storageFileName(sourceFileName))
        modelFile.writeBytes(ByteArray(64 * 1024) { index -> (index * 29).toByte() })
        val artifactMetadata = DownloadFilePolicy.artifactMetadataFile(modelDir, sourceFileName)
        Properties().apply {
            setProperty("modelId", modelId)
            setProperty("title", "Recovered model")
            setProperty("author", "Mobie test")
            setProperty("description", "Recovery fixture")
            setProperty("type", "TEXT_GENERATION")
            setProperty("gated", "false")
            setProperty("fileName", modelFile.name)
            setProperty("sourceFileName", sourceFileName)
            setProperty("quantization", "int4")
            DownloadSourceIdentity.stamp(this, sourceUrl)
            ModelFileVerification.stampInstalledLength(this, modelFile)
            artifactMetadata.outputStream().use { store(it, null) }
        }

        val canonicalMetadata = File(modelDir, DownloadFilePolicy.METADATA_FILE)
        assertTrue(!canonicalMetadata.exists())

        val installed = ModelDownloadManager(context).installedModels().single { it.model.id == modelId }
        val recoveredArtifact = requireNotNull(installed.model.bestArtifact)

        assertEquals(modelFile.absolutePath, installed.localPath)
        assertEquals(sourceFileName, recoveredArtifact.fileName)
        assertEquals(sourceUrl, recoveredArtifact.downloadUrl)
        assertTrue("Canonical metadata should be repaired atomically", canonicalMetadata.isFile)
        val repaired = Properties().apply { canonicalMetadata.inputStream().use(::load) }
        assertEquals(modelId, repaired.getProperty("modelId"))
        assertEquals(sourceFileName, repaired.getProperty("sourceFileName"))
        assertEquals(sourceUrl, repaired.getProperty(DownloadSourceIdentity.SOURCE_URL_PROPERTY))

        modelDir.deleteRecursively()
    }
}
