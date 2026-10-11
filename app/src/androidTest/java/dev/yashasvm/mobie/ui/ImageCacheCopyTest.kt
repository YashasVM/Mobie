package dev.yashasvm.mobie.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCacheCopyTest {
    @Test
    fun copiesFileUrisAndDeletesFailedCopies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createImage(8, 4)
        val copied = copyImageToCache(context, Uri.fromFile(source))

        assertTrue(copied != null)
        assertArrayEquals(source.readBytes(), File(copied!!).readBytes())
        File(copied).delete()
        source.delete()

        val before = context.cacheDir.listFiles().orEmpty().map(File::getName).toSet()
        val missing = File(context.cacheDir, "image-source-missing.bin")
        assertNull(copyImageToCache(context, Uri.fromFile(missing)))
        assertEquals(before, context.cacheDir.listFiles().orEmpty().map(File::getName).toSet())
    }

    @Test
    fun rejectsMalformedAndOversizedImagesWithoutLeavingCopies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val malformed = File.createTempFile("invalid-image-", ".bin", context.cacheDir).apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val oversized = File.createTempFile("large-image-", ".bin", context.cacheDir)
        RandomAccessFile(oversized, "rw").use { it.setLength(20L * 1024 * 1024 + 1) }
        val tooWide = createImage(20_000, 1)
        val tooManyPixels = createImage(8, 4).apply {
            val bytes = readBytes()
            ByteBuffer.wrap(bytes).putInt(16, 5_001).putInt(20, 5_000)
            val checksum = CRC32().apply { update(bytes, 12, 17) }
            ByteBuffer.wrap(bytes).putInt(29, checksum.value.toInt())
            writeBytes(bytes)
        }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tooManyPixels.path, bounds)
            assertEquals(5_001, bounds.outWidth)
            assertEquals(5_000, bounds.outHeight)
            val before = context.cacheDir.listFiles().orEmpty().map(File::getName).toSet()
            for (source in listOf(malformed, oversized, tooWide, tooManyPixels)) {
                assertNull(copyImageToCache(context, Uri.fromFile(source)))
                assertEquals(before, context.cacheDir.listFiles().orEmpty().map(File::getName).toSet())
            }
        } finally {
            malformed.delete()
            oversized.delete()
            tooWide.delete()
            tooManyPixels.delete()
        }
    }

    @Test
    fun downsamplesBothWideAndTallThumbnailsWithoutChangingInferenceBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((width, height) in listOf(3_200 to 32, 32 to 3_200)) {
            val source = createImage(width, height)
            var copied: String? = null
            try {
                copied = copyImageToCache(context, Uri.fromFile(source))
                assertTrue(copied != null)
                assertArrayEquals(source.readBytes(), File(copied!!).readBytes())
                val thumbnail = decodeThumbnail(context, copied)
                assertTrue(thumbnail != null)
                assertTrue(thumbnail!!.width <= 320)
                assertTrue(thumbnail.height <= 320)
            } finally {
                copied?.let { File(it).delete() }
                source.delete()
            }
        }
    }

    @Test
    fun doesNotDecodeImagesInSiblingCacheDirectories() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sibling = File(context.cacheDir.parentFile, "${context.cacheDir.name}-outside").apply { mkdirs() }
        val source = createImage(8, 4)
        val outside = File(sibling, "image.png")
        try {
            source.copyTo(outside, overwrite = true)
            assertNull(decodeThumbnail(context, outside.path))
        } finally {
            source.delete()
            outside.delete()
            sibling.delete()
        }
    }

    private fun createImage(width: Int, height: Int): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("image-source-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        return image
    }
}
