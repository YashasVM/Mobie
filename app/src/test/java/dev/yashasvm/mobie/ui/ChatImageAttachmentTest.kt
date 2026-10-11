package dev.yashasvm.mobie.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageAttachmentTest {
    @Test
    fun `pending photo is dropped once image input is no longer allowed`() {
        assertTrue(shouldDropPendingImage("/cache/photo.jpg", imageAllowed = false))
    }

    @Test
    fun `pending photo is kept while image input is allowed`() {
        assertFalse(shouldDropPendingImage("/cache/photo.jpg", imageAllowed = true))
    }

    @Test
    fun `no photo means nothing to drop`() {
        assertFalse(shouldDropPendingImage(null, imageAllowed = false))
    }
}
