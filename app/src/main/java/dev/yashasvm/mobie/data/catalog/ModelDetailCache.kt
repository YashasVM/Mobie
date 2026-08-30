package dev.yashasvm.mobie.data.catalog

/**
 * Small bounded TTL cache for Hugging Face model-detail responses.
 *
 * Catalog searches often return the same popular LiteRT repositories. Their immutable artifact
 * metadata does not need to be re-fetched on every keystroke/search refresh, so this avoids a
 * large fan-out of repeated API calls while keeping entries short-lived enough to pick up newly
 * published artifacts.
 */
internal class ModelDetailCache<T>(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<T>(val value: T, val expiresAtMillis: Long)

    private val entries = LinkedHashMap<String, Entry<T>>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): T? {
        val entry = entries[key] ?: return null
        if (nowMillis() >= entry.expiresAtMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: T) {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(ttlMillis > 0) { "ttlMillis must be positive" }
        entries[key] = Entry(value, nowMillis() + ttlMillis)
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 64
    }
}
