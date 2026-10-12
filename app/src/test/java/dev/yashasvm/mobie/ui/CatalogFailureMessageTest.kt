package dev.yashasvm.mobie.ui

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFailureMessageTest {
    @Test
    fun offlineFailurePointsToLibraryInsteadOfShowingResolverText() {
        val message = catalogFailureMessage(UnknownHostException("Unable to resolve host \"huggingface.co\""))

        assertTrue(message.startsWith("No connection to Hugging Face."))
        assertTrue("huggingface.co\"" !in message)
    }

    @Test
    fun otherFailuresKeepTheirMessage() {
        assertEquals("HTTP 503", catalogFailureMessage(IOException("HTTP 503")))
        assertEquals("Catalog request failed", catalogFailureMessage(IllegalStateException()))
    }
}
