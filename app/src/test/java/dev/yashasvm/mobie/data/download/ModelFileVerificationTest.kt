package dev.yashasvm.mobie.data.download

import java.io.File
import java.util.Properties
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelFileVerificationTest {
    @Test
    fun `completed file verification returns its trusted digest and rejects mismatches`() {
        val file = File.createTempFile("mobie-completed-digest", ".litertlm")
        try {
            file.writeText("abc")
            val expectedSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

            assertEquals(expectedSha, ModelFileVerification.verifyComplete(file, 3, expectedSha)?.sha256)
            assertEquals(expectedSha, ModelFileVerification.verifyComplete(file, 0, expectedSha)?.sha256)
            assertNull(ModelFileVerification.verifyComplete(file, 3, "0".repeat(64)))
            assertNull(ModelFileVerification.verifyComplete(file, 4, expectedSha))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `completed file verification preserves size only recovery requirements`() {
        val file = File.createTempFile("mobie-completed-size", ".litertlm")
        try {
            file.writeText("abc")
            val completed = ModelFileVerification.verifyComplete(file, 3, null)

            assertTrue(completed != null)
            assertNull(completed?.sha256)
            assertNull(ModelFileVerification.verifyComplete(file, 0, null))
            val malformedLength = Properties().apply {
                setProperty(ModelFileVerification.KEY_INSTALLED_LENGTH, "invalid")
            }
            assertNull(ModelFileVerification.verifyComplete(file, 3, null, malformedLength))
            file.delete()
            assertNull(ModelFileVerification.verifyComplete(file, 3, null))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `completed file digest reuse rejects replacement bytes after fingerprint changes`() {
        val file = File.createTempFile("mobie-completed-fingerprint", ".litertlm")
        try {
            file.writeText("abc")
            val expectedSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            val metadata = Properties().apply {
                setProperty("fileName", file.name)
                setProperty("sha256", expectedSha)
            }
            ModelFileVerification.stamp(metadata, file, expectedSha)

            assertEquals(expectedSha, ModelFileVerification.verifyComplete(file, 3, expectedSha, metadata)?.sha256)
            file.writeText("xyz")
            assertTrue(file.setLastModified(metadata.getProperty(ModelFileVerification.KEY_VERIFIED_LAST_MODIFIED).toLong() + 1_000))
            assertNull(ModelFileVerification.verifyComplete(file, 3, expectedSha, metadata))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `checksum less completed file verifies its local digest after fingerprint changes`() {
        val file = File.createTempFile("mobie-completed-local-digest", ".litertlm")
        try {
            file.writeText("abc")
            val localSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            val metadata = Properties().apply { setProperty("fileName", file.name) }
            ModelFileVerification.stampInstalledLength(metadata, file)
            ModelFileVerification.stamp(metadata, file, localSha)
            val installedMtime = file.lastModified()

            assertTrue(file.setLastModified(installedMtime + 1_000))
            assertEquals(localSha, ModelFileVerification.verifyComplete(file, 3, null, metadata)?.sha256)

            file.writeText("xyz")
            assertTrue(file.setLastModified(installedMtime + 2_000))
            assertNull(ModelFileVerification.verifyComplete(file, 3, null, metadata))
        } finally {
            file.delete()
        }
    }

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
