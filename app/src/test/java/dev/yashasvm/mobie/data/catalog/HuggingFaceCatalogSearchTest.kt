package dev.yashasvm.mobie.data.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceCatalogSearchTest {
    @Test
    fun `search is restricted to the supported LiteRT publisher`() {
        val url = huggingFaceSearchUrl("Qwen3.5 LiteRT")

        assertTrue(url.contains("search=Qwen3.5+LiteRT"))
        assertTrue(url.contains("filter=litert-lm"))
        assertTrue(url.contains("author=litert-community"))
        assertTrue(catalogOwnerAllowed("litert-community/Qwen3.5-0.8B-LiteRT", expectedOwner = "litert-community"))
    }

    @Test
    fun `featured owner restriction remains explicit`() {
        assertTrue(catalogOwnerAllowed("litert-community/Qwen3-0.6B", "litert-community"))
        assertFalse(catalogOwnerAllowed("someone-else/Qwen3-0.6B", "litert-community"))
    }
}
