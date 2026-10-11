package dev.yashasvm.mobie.data.catalog

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceCatalogSearchTest {
    @Test
    fun `search is restricted to official LiteRT community models at the Hub`() {
        val url = huggingFaceSearchUrl("Qwen3.5 LiteRT").toHttpUrl()

        assertEquals("Qwen3.5 LiteRT", url.queryParameter("search"))
        assertEquals("litert-lm", url.queryParameter("filter"))
        assertEquals("litert-community", url.queryParameter("author"))
    }

    @Test
    fun `catalog rejects third party publishers and similar owner names`() {
        assertTrue(catalogOwnerAllowed("litert-community/Qwen3-0.6B"))
        assertFalse(catalogOwnerAllowed("someone-else/Qwen3-0.6B"))
        assertFalse(catalogOwnerAllowed("LudwigBanach/Qwen3.5-0.8B-LiteRT"))
        assertFalse(catalogOwnerAllowed("litert-community-copy/Qwen3-0.6B"))
        assertFalse(catalogOwnerAllowed("someone-else/litert-community"))
    }

    @Test
    fun `catalog rejects incomplete or nested repository IDs`() {
        assertFalse(catalogOwnerAllowed("litert-community"))
        assertFalse(catalogOwnerAllowed("litert-community/"))
        assertFalse(catalogOwnerAllowed("litert-community/ "))
        assertFalse(catalogOwnerAllowed("litert-community/nested/model"))
    }

    @Test
    fun `search query cannot override allowed publisher`() {
        val url = huggingFaceSearchUrl("  qwen&author=someone-else  ").toHttpUrl()

        assertEquals("qwen&author=someone-else", url.queryParameter("search"))
        assertEquals(listOf("litert-community"), url.queryParameterValues("author"))
    }

    @Test
    fun `artifact URLs pin discovered Hub revision`() {
        val revision = "0123456789abcdef0123456789abcdef01234567"
        val url = huggingFaceArtifactUrl(
            repoId = "litert-community/Qwen3-0.6B",
            fileName = "models/qwen.litertlm",
            revision = revision,
        )

        assertTrue(url.contains("/resolve/$revision/models/qwen.litertlm"))
        assertFalse(url.contains("/resolve/main/"))
    }

    @Test
    fun `artifact URL falls back to main only without a revision`() {
        val url = huggingFaceArtifactUrl(
            repoId = "litert-community/Qwen3-0.6B",
            fileName = "qwen.litertlm",
            revision = null,
        )

        assertTrue(url.contains("/resolve/main/qwen.litertlm"))
    }

    @Test
    fun `detail cache key changes when Hub revision changes`() {
        val repoId = "litert-community/Qwen3-0.6B"
        val first = modelDetailCacheKey(repoId, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        val second = modelDetailCacheKey(repoId, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")

        assertNotEquals(first, second)
        assertTrue(first.startsWith("$repoId@"))
    }

    @Test
    fun `detail cache key is stable when revision is unavailable`() {
        val repoId = "litert-community/Qwen3-0.6B"

        assertEquals(modelDetailCacheKey(repoId, null), modelDetailCacheKey(repoId, ""))
        assertEquals("$repoId@unversioned", modelDetailCacheKey(repoId, null))
    }
}
