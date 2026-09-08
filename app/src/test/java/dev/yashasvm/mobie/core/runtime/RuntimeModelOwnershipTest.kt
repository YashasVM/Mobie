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
    fun clearingOwnershipReleasesPreviousModel() {
        val ownership = RuntimeModelOwnership()
        ownership.markLoaded("model-a")

        ownership.clear()

        assertFalse(ownership.owns("model-a"))
    }

    @Test
    fun newerLoadReplacesPreviousOwnership() {
        val ownership = RuntimeModelOwnership()
        ownership.markLoaded("model-a")

        ownership.markLoaded("model-b")

        assertFalse(ownership.owns("model-a"))
        assertTrue(ownership.owns("model-b"))
    }
}
