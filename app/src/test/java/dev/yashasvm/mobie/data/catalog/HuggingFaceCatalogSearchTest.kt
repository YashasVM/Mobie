package dev.yashasvm.mobie.data.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceCatalogSearchTest {
    @Test
    fun `search is restricted to LiteRT LM at the Hub but not by owner`() {
        val url = huggingFaceSearchUrl("Qwen3.5 LiteRT")

        assertTrue(url.contains("search=Qwen3.5+LiteRT"))
        assertTrue(url.contains("filter=litert-lm"))
        assertFalse(url.contains("author="))
        assertTrue(catalogOwnerAllowed("LudwigBanach/Qwen3.5-0.8B-LiteRT", expectedOwner = null))
    }

    @Test
    fun `featured owner restriction remains explicit`() {
        assertTrue(catalogOwnerAllowed("litert-community/Qwen3-0.6B", "litert-community"))
        assertFalse(catalogOwnerAllowed("someone-else/Qwen3-0.6B", "litert-community"))
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
}
