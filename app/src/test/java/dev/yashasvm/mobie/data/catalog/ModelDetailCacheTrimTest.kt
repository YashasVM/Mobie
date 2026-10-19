package dev.yashasvm.mobie.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDetailCacheTrimTest {
    @Test
    fun trimReleasesEntriesAndCacheCanBeRepopulated() {
        val cache = ModelDetailCache<String>()
        cache.put("one", "metadata")
        cache.put("two", "other metadata")
        cache.clear()
        assertNull(cache.get("one"))
        assertNull(cache.get("two"))
        cache.put("one", "fresh metadata")
        assertEquals("fresh metadata", cache.get("one"))
    }
}
