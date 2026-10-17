package dev.yashasvm.mobie

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.attachment.AttachmentRejection
import dev.yashasvm.mobie.core.attachment.AttachmentResult
import dev.yashasvm.mobie.data.attachment.AttachmentReader
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentReaderTest {
    private val reader = AttachmentReader(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun uri(path: String) = Uri.parse("content://dev.yashasvm.mobie.test.documents/$path")

    @Test fun readsUtf8DocumentThroughARealContentProvider() = runBlocking {
        val result = reader.read(uri("notes"), 1024)
        assertTrue(result is AttachmentResult.Accepted)
        assertEquals("Local UTF-8 notes: café", (result as AttachmentResult.Accepted).attachment.text)
        assertEquals("notes.md", result.attachment.name)
    }

    @Test fun providerMimeFailureBecomesAVisibleRejection() = runBlocking {
        assertEquals(AttachmentResult.Rejected(AttachmentRejection.ReadFailed), reader.read(uri("mime-denied"), 1024))
    }

    @Test fun providerQueryFailureBecomesAVisibleRejection() = runBlocking {
        assertEquals(AttachmentResult.Rejected(AttachmentRejection.ReadFailed), reader.read(uri("query-failed"), 1024))
    }

    @Test fun rejectsBinaryContentReportedAsText() = runBlocking {
        assertEquals(AttachmentResult.Rejected(AttachmentRejection.Binary), reader.read(uri("binary"), 1024))
    }
}

/** Only registered by the instrumentation APK; no provider is shipped in the application. */
class AttachmentFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String {
        if (uri.lastPathSegment == "mime-denied") throw SecurityException("Fixture provider refused MIME access")
        return "text/markdown"
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        if (uri.lastPathSegment == "query-failed") throw IllegalStateException("Fixture provider unavailable")
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf("notes.md", null)) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File(requireNotNull(context).cacheDir, "attachment-reader-fixture-${uri.lastPathSegment}")
        file.writeBytes(if (uri.lastPathSegment == "binary") byteArrayOf(65, 0, 66) else "Local UTF-8 notes: café".toByteArray())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
