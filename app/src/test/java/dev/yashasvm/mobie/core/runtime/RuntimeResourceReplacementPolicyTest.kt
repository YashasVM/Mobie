package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeResourceReplacementPolicyTest {
    @Test
    fun installsReplacementBeforeClosingPreviousResource() {
        val events = mutableListOf<String>()
        var current = "old"

        replaceRuntimeResourceBeforeClosingPrevious(
            previous = current,
            createReplacement = {
                events += "create"
                "new"
            },
            installReplacement = { replacement ->
                current = replacement
                events += "install"
            },
            closePrevious = {
                events += "close"
            },
        )

        assertEquals("new", current)
        assertEquals(listOf("create", "install", "close"), events)
    }

    @Test
    fun recoverableCloseFailureLeavesReplacementInstalled() {
        var current = "old"

        try {
            replaceRuntimeResourceBeforeClosingPrevious(
                previous = current,
                createReplacement = { "new" },
                installReplacement = { replacement -> current = replacement },
                closePrevious = { throw IllegalStateException("close failed") },
            )
            fail("Close failure should still propagate")
        } catch (error: IllegalStateException) {
            assertEquals("close failed", error.message)
        }

        assertEquals("new", current)
    }

    @Test
    fun replacementCreationFailurePreservesPreviousResource() {
        var current = "old"
        var closeRan = false

        try {
            replaceRuntimeResourceBeforeClosingPrevious(
                previous = current,
                createReplacement = { throw IllegalStateException("create failed") },
                installReplacement = { replacement -> current = replacement },
                closePrevious = { closeRan = true },
            )
            fail("Replacement creation failure should propagate")
        } catch (error: IllegalStateException) {
            assertEquals("create failed", error.message)
        }

        assertEquals("old", current)
        assertFalse(closeRan)
    }

    @Test
    fun nullPreviousResourceStillInstallsReplacement() {
        var current: String? = null
        var closeRan = false

        replaceRuntimeResourceBeforeClosingPrevious(
            previous = null,
            createReplacement = { "new" },
            installReplacement = { replacement -> current = replacement },
            closePrevious = { closeRan = true },
        )

        assertEquals("new", current)
        assertTrue(!closeRan)
    }
}
