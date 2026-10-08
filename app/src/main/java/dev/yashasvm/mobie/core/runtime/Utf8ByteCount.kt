package dev.yashasvm.mobie.core.runtime

/**
 * Counts the UTF-8 encoded byte length of a string without allocating a byte array.
 *
 * Used as a token-count upper bound in context admission policies. The result matches
 * String.toByteArray(Charsets.UTF_8).size, including the encoder's single-byte replacement for
 * orphaned surrogates.
 */
internal fun String.utf8ByteCount(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        count += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate() -> {
                i++
                4
            }
            c.isSurrogate() -> 1
            else -> 3
        }
        i++
    }
    return count
}
