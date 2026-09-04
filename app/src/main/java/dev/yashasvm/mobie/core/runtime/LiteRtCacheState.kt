package dev.yashasvm.mobie.core.runtime

import java.io.File
import java.security.MessageDigest
import java.util.Properties

/**
 * Conservative proof that LiteRT's persistent optimized cache still belongs to the exact installed
 * model artifact and runtime configuration that created it.
 *
 * Warm-cache storage admission must fail closed: if the model fingerprint, LiteRT version marker,
 * or any cache entry changes, Mobie treats the next load as cold and reserves full cache-build
 * headroom again. This avoids trusting a stale/shared `.litert-cache` merely because it exists.
 */
internal object LiteRtCacheState {
    const val LITERT_LM_VERSION = "0.16.1"
    private const val MARKER_SCHEMA = "1"
    private const val MARKER_FILE = ".mobie-cache-ready.properties"

    fun canReuse(cacheDirectory: File, modelFile: File): Boolean {
        val markerFile = File(cacheDirectory, MARKER_FILE)
        if (!modelFile.isFile || !markerFile.isFile) return false
        val properties = runCatching {
            Properties().apply { markerFile.inputStream().use(::load) }
        }.getOrNull() ?: return false

        if (properties.getProperty("schema") != MARKER_SCHEMA) return false
        if (properties.getProperty("litertLmVersion") != LITERT_LM_VERSION) return false
        if (properties.getProperty("modelPath") != modelFile.absoluteFile.normalize().path) return false
        if (properties.getProperty("modelLength")?.toLongOrNull() != modelFile.length()) return false
        if (properties.getProperty("modelLastModified")?.toLongOrNull() != modelFile.lastModified()) return false

        val manifest = cacheManifest(cacheDirectory) ?: return false
        if (manifest.entryCount <= 0 || manifest.totalBytes <= 0) return false
        return properties.getProperty("cacheEntryCount")?.toIntOrNull() == manifest.entryCount &&
            properties.getProperty("cacheBytes")?.toLongOrNull() == manifest.totalBytes &&
            properties.getProperty("cacheManifestSha256") == manifest.sha256
    }

    fun markReady(cacheDirectory: File, modelFile: File): Boolean {
        if (!modelFile.isFile || !cacheDirectory.isDirectory) return false
        val manifest = cacheManifest(cacheDirectory) ?: return false
        if (manifest.entryCount <= 0 || manifest.totalBytes <= 0) return false

        val properties = Properties().apply {
            setProperty("schema", MARKER_SCHEMA)
            setProperty("litertLmVersion", LITERT_LM_VERSION)
            setProperty("modelPath", modelFile.absoluteFile.normalize().path)
            setProperty("modelLength", modelFile.length().toString())
            setProperty("modelLastModified", modelFile.lastModified().toString())
            setProperty("cacheEntryCount", manifest.entryCount.toString())
            setProperty("cacheBytes", manifest.totalBytes.toString())
            setProperty("cacheManifestSha256", manifest.sha256)
        }
        val markerFile = File(cacheDirectory, MARKER_FILE)
        val temporary = File(cacheDirectory, "$MARKER_FILE.tmp")
        return runCatching {
            temporary.outputStream().use { properties.store(it, "Mobie LiteRT cache readiness") }
            if (markerFile.exists() && !markerFile.delete()) return@runCatching false
            temporary.renameTo(markerFile)
        }.getOrDefault(false).also { success ->
            if (!success) temporary.delete()
        }
    }

    private fun cacheManifest(cacheDirectory: File): CacheManifest? {
        if (!cacheDirectory.isDirectory) return null
        val entries = cacheDirectory.walkTopDown()
            .filter { it.isFile && it.name != MARKER_FILE && it.name != "$MARKER_FILE.tmp" }
            .map { file ->
                val relative = file.relativeTo(cacheDirectory).invariantSeparatorsPath
                CacheEntry(relative, file.length(), file.lastModified())
            }
            .sortedBy(CacheEntry::path)
            .toList()
        if (entries.isEmpty()) return CacheManifest(0, 0, sha256(""))
        val serialized = buildString {
            entries.forEach { entry ->
                append(entry.path).append('\u0000')
                append(entry.length).append('\u0000')
                append(entry.lastModified).append('\n')
            }
        }
        return CacheManifest(
            entryCount = entries.size,
            totalBytes = entries.sumOf(CacheEntry::length),
            sha256 = sha256(serialized),
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class CacheEntry(val path: String, val length: Long, val lastModified: Long)
    private data class CacheManifest(val entryCount: Int, val totalBytes: Long, val sha256: String)
}
