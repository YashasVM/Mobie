package dev.yashasvm.mobie.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResponsePolicyTest {
    @Test
    fun `content range parser accepts valid byte ranges`() {
        assertEquals(
            ContentRange(start = 500, endInclusive = 999, totalBytes = 1_000),
            DownloadResponsePolicy.parseContentRange("bytes 500-999/1000"),
        )
    }

    @Test
    fun `content range parser rejects malformed or impossible ranges`() {
        assertNull(DownloadResponsePolicy.parseContentRange("bytes 600-500/1000"))
        assertNull(DownloadResponsePolicy.parseContentRange("bytes 500-1000/1000"))
        assertNull(DownloadResponsePolicy.parseContentRange("500-999/1000"))
    }

    @Test
    fun `resume response must start exactly at local partial length`() {
        assertTrue(DownloadResponsePolicy.isValidResumeResponse("bytes 700-999/1000", 700, 1_000))
        assertFalse(DownloadResponsePolicy.isValidResumeResponse("bytes 600-999/1000", 700, 1_000))
    }

    @Test
    fun `resume response must agree with expected model size`() {
        assertFalse(DownloadResponsePolicy.isValidResumeResponse("bytes 700-1099/1100", 700, 1_000))
    }

    @Test
    fun `resume response requires authoritative total size`() {
        assertFalse(DownloadResponsePolicy.isValidResumeResponse("bytes 700-999/*", 700, 0))
        assertTrue(DownloadResponsePolicy.isValidResumeResponse("bytes 700-999/1000", 700, 0))
    }

    @Test
    fun `server content range supplies total when catalog size is unknown`() {
        assertEquals(
            1_000,
            DownloadResponsePolicy.resolvedTotalBytes(0, "bytes 700-999/1000", bodyLength = 300, startAt = 700),
        )
    }

    @Test
    fun `throttling and transient server failures retry`() {
        assertTrue(DownloadResponsePolicy.isRetryableHttp(408))
        assertTrue(DownloadResponsePolicy.isRetryableHttp(429))
        assertTrue(DownloadResponsePolicy.isRetryableHttp(503))
        assertFalse(DownloadResponsePolicy.isRetryableHttp(404))
    }
}
