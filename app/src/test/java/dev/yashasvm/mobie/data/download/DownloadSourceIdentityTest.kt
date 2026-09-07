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

        assertTrue(DownloadSourceIdentity.isImmutable(url))
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
    fun `mutable same url cannot reuse checksum-less completed or partial bytes`() {
        val properties = Properties()
        val mutableUrl = "https://example.com/models/current/model.litertlm"
        DownloadSourceIdentity.stamp(properties, mutableUrl)

        assertFalse(DownloadSourceIdentity.isImmutable(mutableUrl))
        assertFalse(DownloadSourceIdentity.matches(properties, mutableUrl))
        assertFalse(DownloadSourceIdentity.canReuseCompleted(properties, mutableUrl, expectedSha256 = null))
        assertFalse(DownloadSourceIdentity.canRecoverInstalled(properties, mutableUrl))
    }

    @Test
    fun `symbolic hugging face revision is mutable even when url matches`() {
        val properties = Properties()
        val branchUrl = "https://huggingface.co/acme/model/resolve/main/model.litertlm"
        DownloadSourceIdentity.stamp(properties, branchUrl)

        assertFalse(DownloadSourceIdentity.isImmutable(branchUrl))
        assertFalse(DownloadSourceIdentity.matches(properties, branchUrl))
        assertFalse(DownloadSourceIdentity.canReuseCompleted(properties, branchUrl, expectedSha256 = null))
        assertFalse(DownloadSourceIdentity.canRecoverInstalled(properties, branchUrl))
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

    @Test
    fun `matching resume sidecar can recover interrupted installed file`() {
        val properties = Properties()
        val url = "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm"
        DownloadSourceIdentity.stamp(properties, url)

        assertTrue(DownloadSourceIdentity.canRecoverInstalled(properties, url))
    }

    @Test
    fun `resume sidecar from another revision cannot recover installed file`() {
        val properties = Properties()
        DownloadSourceIdentity.stamp(
            properties,
            "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm",
        )

        assertFalse(
            DownloadSourceIdentity.canRecoverInstalled(
                properties,
                "https://huggingface.co/acme/model/resolve/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/model.litertlm",
            ),
        )
    }
}
