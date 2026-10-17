package dev.yashasvm.mobie

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.attachment.AttachmentRejection
import dev.yashasvm.mobie.core.attachment.AttachmentResult
import dev.yashasvm.mobie.data.attachment.AttachmentReader
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
