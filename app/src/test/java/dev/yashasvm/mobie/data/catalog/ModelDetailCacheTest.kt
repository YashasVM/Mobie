package dev.yashasvm.mobie.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDetailCacheTest {
    @Test
    fun `cached value expires after ttl`() {
        var now = 1_000L
        val cache = ModelDetailCache<String>(ttlMillis = 500, maxEntries = 4) { now }

        cache.put("model", "v1")
        assertEquals("v1", cache.get("model"))

        now = 1_499L
        assertEquals("v1", cache.get("model"))

        now = 1_500L
        assertNull(cache.get("model"))
    }

    @Test
    fun `least recently used entry is evicted when cache is full`() {
        val cache = ModelDetailCache<String>(ttlMillis = 10_000, maxEntries = 2) { 0L }

        cache.put("a", "A")
        cache.put("b", "B")
        assertEquals("A", cache.get("a"))
        cache.put("c", "C")

        assertEquals("A", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("C", cache.get("c"))
    }
}
