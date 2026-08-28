package dev.yashasvm.mobie.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownloadFilePolicyTest {
    @Test
    fun `different repositories use different model directories`() {
        assertNotEquals(
            DownloadFilePolicy.storageKey("owner-a/model"),
            DownloadFilePolicy.storageKey("owner-b/model"),
        )
    }

    @Test
    fun `artifact path is reduced to a safe leaf name`() {
        assertEquals("model.gguf", DownloadFilePolicy.safeFileName("weights/mobile/model.gguf"))
        assertEquals("model.bin", DownloadFilePolicy.safeFileName(".."))
    }

    @Test
    fun `resume requires only remaining free space`() {
        assertEquals(300, DownloadFilePolicy.remainingBytes(expectedSize = 1_000, partialSize = 700))
        assertEquals(0, DownloadFilePolicy.remainingBytes(expectedSize = 1_000, partialSize = 1_200))
    }
}
