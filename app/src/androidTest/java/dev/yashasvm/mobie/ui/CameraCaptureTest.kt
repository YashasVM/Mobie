package dev.yashasvm.mobie.ui

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    @Test
    fun importsValidatedPhotoThroughPrivateProvider() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val source = File.createTempFile("capture-", ".jpg", directory)
        var copied: String? = null
        try {
            val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            bitmap.recycle()
            assertEquals(source.canonicalFile, cameraCaptureFile(context, source.absolutePath))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", source)
            assertEquals("content", uri.scheme)
            copied = copyImageToCache(context, uri)
            assertTrue(copied != null)
            assertArrayEquals(source.readBytes(), File(checkNotNull(copied)).readBytes())
        } finally {
            copied?.let { File(it).delete() }
            source.delete()
        }
    }

    @Test
    fun rejectsRestoredPathsOutsideCameraDirectoryAndMalformedPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        assertNull(cameraCaptureFile(context, File(context.cacheDir, "capture-outside.jpg").path))
        assertNull(cameraCaptureFile(context, File(directory, "../capture-outside.jpg").path))
        assertNull(cameraCaptureFile(context, File(directory, "other.jpg").path))
        assertNull(cameraCaptureFile(context, File(directory, "capture-wrong.bin").path))
        val source = File.createTempFile("capture-", ".jpg", directory)
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", source)
            assertNull(copyImageToCache(context, uri))
        } finally {
            source.delete()
        }
    }
}
