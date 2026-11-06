package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.ModelType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilterTest {
    private val text = AiModel("text", "Text", "publisher", "", type = ModelType.TEXT_GENERATION)
    private val vision = text.copy(id = "vision", type = ModelType.VISION)

    @Test
    fun fitsNowExcludesWarningsBlockedAndUnknownCompatibility() {
        assertTrue(CatalogFilter.FITS_NOW.includes(text, Compatibility.COMPATIBLE))
        listOf(null, Compatibility.WARNING, Compatibility.INCOMPATIBLE, Compatibility.CONVERSION_REQUIRED).forEach {
            assertFalse(CatalogFilter.FITS_NOW.includes(text, it))
        }
    }

    @Test
    fun capabilityFiltersKeepTextAndVisionSeparate() {
        assertTrue(CatalogFilter.PHOTOS.includes(vision, null))
        assertFalse(CatalogFilter.PHOTOS.includes(text, null))
        assertTrue(CatalogFilter.TEXT.includes(text, null))
        assertFalse(CatalogFilter.TEXT.includes(vision, null))
        assertFalse(CatalogFilter.TEXT.includes(text.copy(type = ModelType.AUDIO), null))
        assertTrue(CatalogFilter.ALL.includes(vision, Compatibility.INCOMPATIBLE))
    }
}
