package dev.yashasvm.mobie.data.download

import java.util.Properties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourceIdentityTest {
    @Test
    fun `matching commit-pinned source can reuse bytes`() {
        val properties = Properties()
        val url = "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm"
        DownloadSourceIdentity.stamp(properties, url)

        assertTrue(DownloadSourceIdentity.matches(properties, url))
        assertTrue(DownloadSourceIdentity.canReuseCompleted(properties, url, expectedSha256 = null))
    }

    @Test
    fun `different revision cannot reuse unverified bytes even when artifact path is unchanged`() {
        val properties = Properties()
        DownloadSourceIdentity.stamp(
            properties,
            "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm",
        )
        val newRevision =
            "https://huggingface.co/acme/model/resolve/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/model.litertlm"

        assertFalse(DownloadSourceIdentity.matches(properties, newRevision))
        assertFalse(DownloadSourceIdentity.canReuseCompleted(properties, newRevision, expectedSha256 = null))
    }

    @Test
    fun `checksum proven completed file may be reused across source revisions`() {
        val properties = Properties()
        DownloadSourceIdentity.stamp(
            properties,
            "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm",
        )

        assertTrue(
            DownloadSourceIdentity.canReuseCompleted(
                properties,
                "https://huggingface.co/acme/model/resolve/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/model.litertlm",
                expectedSha256 = "0123456789abcdef",
            ),
        )
    }

    @Test
    fun `legacy metadata without source identity is not trusted for unverified reuse`() {
        val url = "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm"

        assertFalse(DownloadSourceIdentity.matches(Properties(), url))
        assertFalse(DownloadSourceIdentity.canReuseCompleted(Properties(), url, expectedSha256 = null))
    }
}
