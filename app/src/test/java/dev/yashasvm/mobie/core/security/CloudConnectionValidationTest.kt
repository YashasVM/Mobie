package dev.yashasvm.mobie.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class CloudConnectionValidationTest {
    @Test fun `normalizes a secure address while preserving its path`() {
        assertEquals(CloudConnection("https://gateway.example/mobie", "pairing-key"),
            CloudConnectionStore.validatedConnection(" https://gateway.example/mobie/ ", " pairing-key "))
    }

    @Test fun `rejects addresses that could expose credentials or redirect request meaning`() {
        for (address in listOf("http://gateway.example", "not a URL", "https://user:password@gateway.example",
            "https://gateway.example?token=secret", "https://gateway.example#secret", "https://gateway.example?")) {
            try {
                CloudConnectionStore.validatedConnection(address, "pairing-key")
                fail("Expected invalid gateway address to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertFalse(expected.message.orEmpty().contains("password@"))
                assertFalse(expected.message.orEmpty().contains("token=secret"))
            }
        }
    }

    @Test fun `rejects missing or unsafe header keys`() {
        for (key in listOf("", " ", "secret\nheader", "secret key", "secret\u0000")) {
            try {
                CloudConnectionStore.validatedConnection("https://gateway.example", key)
                fail("Expected invalid pairing key to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertEquals("Enter the pairing key shown on your Linux box.", expected.message)
            }
        }
    }

    @Test fun `connection debug string never reveals pairing credentials`() {
        assertEquals("CloudConnection(credentials=redacted)",
            CloudConnection("https://private.example", "private-pairing-key").toString())
    }
}
