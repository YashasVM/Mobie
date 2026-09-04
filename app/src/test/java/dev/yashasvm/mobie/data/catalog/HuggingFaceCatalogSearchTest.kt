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
}
