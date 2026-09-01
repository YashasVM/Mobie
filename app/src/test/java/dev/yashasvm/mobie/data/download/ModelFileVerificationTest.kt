package dev.yashasvm.mobie.data.download

import java.io.File
import java.util.Properties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `legacy metadata without installed length remains readable`() {
        val file = File.createTempFile("mobie-legacy-length", ".litertlm")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertTrue(ModelFileVerification.matchesInstalledLength(Properties(), file))
        } finally {
            file.delete()
        }
    }
}
