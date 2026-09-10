package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeModelOwnershipTest {
    @Test
    fun ownershipTracksNativeRuntimeInsteadOfUiSelection() {
        val ownership = RuntimeModelOwnership()

        ownership.markLoaded("model-a")

        assertTrue(ownership.owns("model-a"))
        assertFalse(ownership.owns("model-b"))
    }

    @Test
    fun uncertainOwnershipFailsClosedUntilNextSuccessfulLoad() {
        val ownership = RuntimeModelOwnership()
        ownership.markLoaded("model-a")

        ownership.clear()

        assertTrue(ownership.owns("model-a"))
        assertTrue(ownership.owns("model-b"))
    }

    @Test
    fun newerLoadReestablishesPreciseOwnership() {
        val ownership = RuntimeModelOwnership()
        ownership.markLoaded("model-a")
        ownership.clear()

        ownership.markLoaded("model-b")

        assertFalse(ownership.owns("model-a"))
        assertTrue(ownership.owns("model-b"))
    }
}
