package dev.yashasvm.mobie.data.download

import java.io.File
import java.util.Properties
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelFileVerificationTest {
    @Test
    fun `reuses checksum only for the exact stamped file fingerprint`() {
        val file = File.createTempFile("mobie-verification", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val properties = Properties().apply {
                setProperty("fileName", file.name)
                setProperty("sha256", "abc123")
            }
            ModelFileVerification.stamp(properties, file)

            assertTrue(ModelFileVerification.canReuseShaVerification(properties, file, "ABC123"))

            file.writeBytes(byteArrayOf(4, 3, 2, 1, 0))
            assertFalse(ModelFileVerification.canReuseShaVerification(properties, file, "abc123"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `does not trust missing or mismatched checksum metadata`() {
        val file = File.createTempFile("mobie-verification", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            val properties = Properties().apply {
                setProperty("fileName", file.name)
                setProperty("sha256", "expected")
            }
            ModelFileVerification.stamp(properties, file)

            assertFalse(ModelFileVerification.canReuseShaVerification(properties, file, "different"))
            properties.remove(ModelFileVerification.KEY_VERIFIED_LAST_MODIFIED)
            assertFalse(ModelFileVerification.canReuseShaVerification(properties, file, "expected"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `installed length detects truncation even without a checksum`() {
        val file = File.createTempFile("mobie-installed-length", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val properties = Properties()
            ModelFileVerification.stampInstalledLength(properties, file)

            assertTrue(ModelFileVerification.matchesInstalledLength(properties, file))

            file.writeBytes(byteArrayOf(1, 2))
            assertFalse(ModelFileVerification.matchesInstalledLength(properties, file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `malformed installed length fails closed`() {
        val file = File.createTempFile("mobie-malformed-installed-length", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val properties = Properties().apply {
                setProperty(ModelFileVerification.KEY_INSTALLED_LENGTH, "not-a-length")
            }

            assertFalse(ModelFileVerification.matchesInstalledLength(properties, file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `local digest protects checksum-less model fingerprint`() {
        val file = File.createTempFile("mobie-local-digest", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val properties = Properties().apply { setProperty("fileName", file.name) }
            ModelFileVerification.stampInstalledLength(properties, file)
            ModelFileVerification.stamp(properties, file, "AABBCC")

            assertEquals("aabbcc", ModelFileVerification.localSha256(properties))
            assertTrue(ModelFileVerification.canReuseLocalVerification(properties, file))

            file.writeBytes(byteArrayOf(4, 3, 2, 1))
            val stampedMtime = properties.getProperty(ModelFileVerification.KEY_VERIFIED_LAST_MODIFIED).toLong()
            file.setLastModified(stampedMtime + 1_000)
            assertTrue(ModelFileVerification.matchesInstalledLength(properties, file))
            assertFalse(ModelFileVerification.canReuseLocalVerification(properties, file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `legacy metadata without installed length remains readable`() {
        val file = File.createTempFile("mobie-legacy-length", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(ModelFileVerification.matchesInstalledLength(Properties(), file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `sha verification checks cancellation between file chunks`() {
        val file = File.createTempFile("mobie-cancellable-sha", ".litertlm")
        try {
            file.outputStream().use { output ->
                repeat(8) { output.write(ByteArray(DEFAULT_BUFFER_SIZE)) }
            }
            var checks = 0

            try {
                ModelFileVerification.sha256(file) {
                    checks += 1
                    if (checks == 3) throw CancellationException("cancelled")
                }
                fail("Expected verification to stop when cancelled")
            } catch (_: CancellationException) {
                assertEquals(3, checks)
            }
        } finally {
            file.delete()
        }
    }
}
