package dev.yashasvm.mobie.ui

import android.net.Uri
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
class ImageCacheCopyTest {
    @Test
    fun copiesFileUrisAndDeletesFailedCopies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("image-source-", ".bin", context.cacheDir).apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
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
}
