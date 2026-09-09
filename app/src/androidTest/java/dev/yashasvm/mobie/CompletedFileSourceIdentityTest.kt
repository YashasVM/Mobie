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
        val oldSource =
            "https://huggingface.co/example/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/$fileName"
        val newSource =
            "https://huggingface.co/example/model/resolve/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/$fileName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val destination = File(modelDir, DownloadFilePolicy.storageFileName(fileName))
        destination.writeBytes(ByteArray(64 * 1024) { index -> (index * 29).toByte() })
        val metadataFile = File(modelDir, DownloadFilePolicy.METADATA_FILE)
        writeIdentityMetadata(metadataFile, modelId, fileName, destination, oldSource)

        val downloads = ModelDownloadManager(context)
        val oldArtifact = artifact(fileName, oldSource, destination.length())
        val newArtifact = oldArtifact.copy(downloadUrl = newSource)

        assertEquals(destination.absolutePath, downloads.completedFile(modelId, oldArtifact)?.absolutePath)
        assertNull(
            "A same-size checksum-less file from an older Hub revision must not bypass the worker",
            downloads.completedFile(modelId, newArtifact),
        )

        modelDir.deleteRecursively()
        Unit
    }

    @Test
    fun perArtifactIdentitySurvivesAnotherArtifactBecomingCanonical() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "mobie-test/multi-artifact-source-identity"
        val firstName = "model-q4.litertlm"
        val secondName = "model-q8.litertlm"
        val firstSource =
            "https://huggingface.co/example/model/resolve/cccccccccccccccccccccccccccccccccccccccc/$firstName"
        val secondSource =
            "https://huggingface.co/example/model/resolve/dddddddddddddddddddddddddddddddddddddddd/$secondName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val firstFile = File(modelDir, DownloadFilePolicy.storageFileName(firstName)).apply {
            writeBytes(ByteArray(48 * 1024) { index -> (index * 11).toByte() })
        }
        val secondFile = File(modelDir, DownloadFilePolicy.storageFileName(secondName)).apply {
            writeBytes(ByteArray(72 * 1024) { index -> (index * 17).toByte() })
        }

        writeIdentityMetadata(
            DownloadFilePolicy.artifactMetadataFile(modelDir, firstName),
            modelId,
            firstName,
            firstFile,
            firstSource,
        )
        writeIdentityMetadata(
            DownloadFilePolicy.artifactMetadataFile(modelDir, secondName),
            modelId,
            secondName,
            secondFile,
            secondSource,
        )
        writeIdentityMetadata(
            File(modelDir, DownloadFilePolicy.METADATA_FILE),
            modelId,
            secondName,
            secondFile,
            secondSource,
        )

        val downloads = ModelDownloadManager(context)
        assertEquals(
            firstFile.absolutePath,
            downloads.completedFile(modelId, artifact(firstName, firstSource, firstFile.length()))?.absolutePath,
        )
        assertEquals(
            secondFile.absolutePath,
            downloads.completedFile(modelId, artifact(secondName, secondSource, secondFile.length()))?.absolutePath,
        )

        modelDir.deleteRecursively()
        Unit
    }

    @Test
    fun corruptPerArtifactMetadataFallsBackToMatchingCanonicalMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "mobie-test/corrupt-artifact-metadata-fallback"
        val fileName = "fallback-test.litertlm"
        val source =
            "https://huggingface.co/example/model/resolve/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/$fileName"
        val modelDir = File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(modelId))
        modelDir.deleteRecursively()
        modelDir.mkdirs()

        val destination = File(modelDir, DownloadFilePolicy.storageFileName(fileName)).apply {
            writeBytes(ByteArray(32 * 1024) { index -> (index * 7).toByte() })
        }
        DownloadFilePolicy.artifactMetadataFile(modelDir, fileName).writeText("broken=\\u12G4\n")
        writeIdentityMetadata(
            File(modelDir, DownloadFilePolicy.METADATA_FILE),
            modelId,
            fileName,
            destination,
            source,
        )

        val completed = ModelDownloadManager(context).completedFile(
            modelId,
            artifact(fileName, source, destination.length()),
        )

        assertEquals(destination.absolutePath, completed?.absolutePath)
        modelDir.deleteRecursively()
        Unit
    }

    private fun artifact(fileName: String, source: String, size: Long) = ModelArtifact(
        fileName = fileName,
        downloadUrl = source,
        sizeBytes = size,
        sha256 = null,
        format = ModelFormat.LITERT_LM,
    )

    private fun writeIdentityMetadata(
        metadataFile: File,
        modelId: String,
        sourceFileName: String,
        destination: File,
        source: String,
    ) {
        Properties().apply {
            setProperty("modelId", modelId)
            setProperty("fileName", destination.name)
            setProperty("sourceFileName", sourceFileName)
            setProperty("installedLength", destination.length().toString())
            DownloadSourceIdentity.stamp(this, source)
            metadataFile.outputStream().use { store(it, null) }
        }
    }
}
