package dev.yashasvm.mobie.core.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtCacheStateTest {
    @Test
    fun `ready marker only validates unchanged exact model cache and context`() {
        val root = Files.createTempDirectory("mobie-litert-cache").toFile()
        try {
            val model = File(root, "model.litertlm").apply { writeBytes(ByteArray(32) { it.toByte() }) }
            val cache = File(root, ".litert-cache").apply { mkdirs() }
            val optimized = File(cache, "compiled.bin").apply { writeBytes(ByteArray(64) { (it * 3).toByte() }) }

            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))
            assertTrue(LiteRtCacheState.markReady(cache, model, contextWindowTokens = 4_096))
            assertTrue(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))
            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 2_048))

            optimized.appendBytes(byteArrayOf(7))
            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `model replacement invalidates warm cache even when cache is untouched`() {
        val root = Files.createTempDirectory("mobie-litert-model").toFile()
        try {
            val model = File(root, "model.litertlm").apply { writeBytes(ByteArray(24) { 1 }) }
            val cache = File(root, ".litert-cache").apply { mkdirs() }
            File(cache, "compiled.bin").writeBytes(ByteArray(48) { 2 })

            assertTrue(LiteRtCacheState.markReady(cache, model, contextWindowTokens = 4_096))
            assertTrue(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))

            Thread.sleep(5)
            model.writeBytes(ByteArray(25) { 3 })
            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `empty cache or invalid context cannot be marked warm`() {
        val root = Files.createTempDirectory("mobie-litert-empty").toFile()
        try {
            val model = File(root, "model.litertlm").apply { writeText("model") }
            val cache = File(root, ".litert-cache").apply { mkdirs() }

            assertFalse(LiteRtCacheState.markReady(cache, model, contextWindowTokens = 4_096))
            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 4_096))

            File(cache, "compiled.bin").writeText("cache")
            assertFalse(LiteRtCacheState.markReady(cache, model, contextWindowTokens = 0))
            assertFalse(LiteRtCacheState.canReuse(cache, model, contextWindowTokens = 0))
        } finally {
            root.deleteRecursively()
        }
    }
}
