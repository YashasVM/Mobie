package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class Utf8ByteCountTest {
    private fun String.referenceByteCount(): Int = toByteArray(Charsets.UTF_8).size

    @Test
    fun `empty string returns zero`() {
        assertEquals(0, "".utf8ByteCount())
    }

    @Test
    fun `ascii characters each count as one byte`() {
        val ascii = "Hello, World! 0123456789"
        assertEquals(ascii.referenceByteCount(), ascii.utf8ByteCount())
        assertEquals(ascii.length, ascii.utf8ByteCount())
    }

    @Test
    fun `two byte range characters are counted correctly`() {
        val twoByteChars = "éüÀЀԀ"
        assertEquals(twoByteChars.referenceByteCount(), twoByteChars.utf8ByteCount())
    }

    @Test
    fun `three byte range cjk characters are counted correctly`() {
        val cjk = "你好世界中文"
        assertEquals(cjk.referenceByteCount(), cjk.utf8ByteCount())
    }

    @Test
    fun `four byte supplementary characters via surrogate pairs are counted correctly`() {
        val emoji = "😀😁🌍"
        assertEquals(emoji.referenceByteCount(), emoji.utf8ByteCount())
    }

    @Test
    fun `mixed ascii unicode and emoji produce correct total`() {
        val mixed = "Hi 😀 café 中文"
        assertEquals(mixed.referenceByteCount(), mixed.utf8ByteCount())
    }

    @Test
    fun `long ascii string matches reference`() {
        val long = "x".repeat(10_000)
        assertEquals(long.referenceByteCount(), long.utf8ByteCount())
        assertEquals(10_000, long.utf8ByteCount())
    }

    @Test
    fun `long unicode string matches reference`() {
        val long = "中".repeat(2_000)
        assertEquals(long.referenceByteCount(), long.utf8ByteCount())
        assertEquals(6_000, long.utf8ByteCount())
    }

    @Test
    fun `result matches reference for all single bmp code points`() {
        for (cp in 0..0xFFFF) {
            val s = cp.toChar().toString()
            assertEquals("code point $cp", s.referenceByteCount(), s.utf8ByteCount())
        }
    }

    @Test
    fun `malformed surrogate sequences use the encoders single byte replacement`() {
        for (text in listOf("\uD800x", "\uDC00", "\uD800\uD800\uDC00", "\uDC00\uD800")) {
            assertEquals(text.referenceByteCount(), text.utf8ByteCount())
        }
    }
}
