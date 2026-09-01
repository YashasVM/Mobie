package dev.yashasvm.mobie.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.work.WorkInfo

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
    fun `artifact paths get distinct storage names when leaf names collide`() {
        assertNotEquals(
            DownloadFilePolicy.storageFileName("en/model.litertlm"),
            DownloadFilePolicy.storageFileName("de/model.litertlm"),
        )
    }

    @Test
    fun `resume requires only remaining free space`() {
        assertEquals(300, DownloadFilePolicy.remainingBytes(expectedSize = 1_000, partialSize = 700))
        assertEquals(0, DownloadFilePolicy.remainingBytes(expectedSize = 1_000, partialSize = 1_200))
    }

    @Test
    fun `resolved transfer size is checked against remaining free space`() {
        assertTrue(
            DownloadFilePolicy.hasSpaceForRemaining(
                totalBytes = 1_000,
                downloadedBytes = 700,
                usableSpaceBytes = 300,
            ),
        )
        assertFalse(
            DownloadFilePolicy.hasSpaceForRemaining(
                totalBytes = 1_000,
                downloadedBytes = 700,
                usableSpaceBytes = 299,
            ),
        )
        assertTrue(
            DownloadFilePolicy.hasSpaceForRemaining(
                totalBytes = 0,
                downloadedBytes = 0,
                usableSpaceBytes = 0,
            ),
        )
    }

    @Test
    fun `only unfinished downloads can be cancelled`() {
        fun progress(state: WorkInfo.State) = DownloadProgress(state, 0, 0, 0)

        assertTrue(progress(WorkInfo.State.RUNNING).isCancellable)
        assertTrue(progress(WorkInfo.State.ENQUEUED).isCancellable)
        assertFalse(progress(WorkInfo.State.SUCCEEDED).isCancellable)
        assertFalse(progress(WorkInfo.State.CANCELLED).isCancellable)
    }
}
