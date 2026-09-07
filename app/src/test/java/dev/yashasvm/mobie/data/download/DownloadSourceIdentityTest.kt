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
    }

    @Test
    fun `different revision cannot reuse bytes even when artifact path is unchanged`() {
        val properties = Properties()
        DownloadSourceIdentity.stamp(
            properties,
            "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm",
        )

        assertFalse(
            DownloadSourceIdentity.matches(
                properties,
                "https://huggingface.co/acme/model/resolve/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/model.litertlm",
            ),
        )
    }

    @Test
    fun `legacy metadata without source identity is not trusted for reuse`() {
        assertFalse(
            DownloadSourceIdentity.matches(
                Properties(),
                "https://huggingface.co/acme/model/resolve/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/model.litertlm",
            ),
        )
    }
}
