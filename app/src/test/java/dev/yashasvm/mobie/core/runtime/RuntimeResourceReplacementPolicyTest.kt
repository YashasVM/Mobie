package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeResourceReplacementPolicyTest {
    @Test
    fun closesPreviousConversationBeforeCreatingReplacement() {
        val engine = FakeConversationEngine()
        var current: FakeConversationEngine.Conversation? = engine.create("old chat")
        engine.events.clear()

        replaceRuntimeResourceAfterClosingPrevious(
            previous = current,
            clearPrevious = { current = null },
            createReplacement = { engine.create("new chat") },
            installReplacement = { current = it },
            closePrevious = { it.close() },
        )

        assertEquals(listOf("close:old chat", "create:new chat"), engine.events)
        assertEquals("new chat", current!!.history)
        assertEquals(1, engine.liveSessions)
        assertEquals(1, engine.peakSessions)
    }

    @Test
    fun failedCreationLeavesNoConversationAndNextSendCanRetryRequestedHistory() {
        val engine = FakeConversationEngine()
        var current: FakeConversationEngine.Conversation? = engine.create("old chat")
        var committedHistory = "old chat"
        var dirty = false
        engine.events.clear()
        engine.failNextCreate = true

        fun rebuild() {
            dirty = true
            replaceRuntimeResourceAfterClosingPrevious(
                previous = current,
                clearPrevious = { current = null },
                createReplacement = { engine.create(committedHistory) },
                installReplacement = { current = it; dirty = false },
                closePrevious = { it.close() },
            )
        }

        committedHistory = "new chat"
        val failedReset = recoverableRuntimeResult { rebuild() }
        assertEquals("create failed", failedReset.exceptionOrNull()!!.message)
        assertNull(current)
        assertTrue(dirty)
        assertEquals(0, engine.liveSessions)

        val nextSend = recoverableRuntimeResult { if (current == null || dirty) rebuild() }
        assertTrue(nextSend.isSuccess)
        assertEquals("new chat", current!!.history)
        assertFalse(dirty)
        assertEquals(listOf("close:old chat", "create:new chat", "create:new chat"), engine.events)
        assertEquals(1, engine.peakSessions)
    }

    @Test
    fun closeFailureRetainsOwnershipAndPreventsReplacementAllocation() {
        val engine = FakeConversationEngine()
        val previous = engine.create("old chat")
        var current: FakeConversationEngine.Conversation? = previous
        previous.failClose = true
        engine.events.clear()

        val result = recoverableRuntimeResult {
            replaceRuntimeResourceAfterClosingPrevious(
                previous = current,
                clearPrevious = { current = null },
                createReplacement = { engine.create("new chat") },
                installReplacement = { current = it },
                closePrevious = { it.close() },
            )
        }

        assertEquals("close failed", result.exceptionOrNull()!!.message)
        assertSame(previous, current)
        assertEquals(listOf("close:old chat"), engine.events)
        assertEquals(1, engine.liveSessions)
    }

    @Test
    fun missingConversationCanBeCreatedWithoutClosingAgain() {
        val engine = FakeConversationEngine()
        var current: FakeConversationEngine.Conversation? = null
        replaceRuntimeResourceAfterClosingPrevious(
            previous = null,
            clearPrevious = { current = null },
            createReplacement = { engine.create("new chat") },
            installReplacement = { current = it },
            closePrevious = { it.close() },
        )

        assertEquals("new chat", current!!.history)
        assertEquals(listOf("create:new chat"), engine.events)
    }
}

/** Fails if a replacement overlaps a live session or close races active generation. */
internal class FakeConversationEngine {
    val events = mutableListOf<String>()
    var liveSessions = 0
        private set
    var peakSessions = 0
        private set
    var failNextCreate = false

    fun create(history: String): Conversation {
        events += "create:$history"
        check(liveSessions == 0) { "Native sessions overlap" }
        if (failNextCreate) {
            failNextCreate = false
            throw IllegalStateException("create failed")
        }
        liveSessions++
        peakSessions = maxOf(peakSessions, liveSessions)
        return Conversation(history)
    }

    inner class Conversation(val history: String) {
        var generating = false
        var failClose = false
        fun close() {
            events += "close:$history"
            check(!generating) { "Closed during generation" }
            check(!failClose) { "close failed" }
            liveSessions--
        }
    }
}
