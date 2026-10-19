package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConversationReconfigurationTest {
    @Test
    fun replacementWaitsForGenerationAndInvalidatesOlderPermits() = runBlocking {
        val engine = FakeConversationEngine()
        var current = engine.create("old")
        engine.events.clear()
        val state = RuntimeCancellationState()
        val generation = Mutex()
        val finish = CompletableDeferred<Unit>()
        var unavailable = false
        val generating = launch(start = CoroutineStart.UNDISPATCHED) {
            generation.withLock {
                state.beginGeneration()
                current.generating = true
                try {
                    finish.await()
                } finally {
                    current.generating = false
                    engine.events += "generation ended"
                    state.endGeneration()
                }
            }
        }
        val stalePermit = state.captureGenerationPermit()
        val reconfiguring = async(start = CoroutineStart.UNDISPATCHED) {
            reconfigureRuntimeConversation(state, generation, Mutex(), requestCancellation = {
                state.attempt { engine.events += "cancel" }
            }, invalidateConversation = { unavailable = true }) {
                replaceRuntimeResourceAfterClosingPrevious(
                    previous = current,
                    clearPrevious = {},
                    createReplacement = { engine.create("new") },
                    installReplacement = { current = it; unavailable = false },
                    closePrevious = { it.close() },
                )
            }
        }
        assertTrue(unavailable)
        assertFalse(reconfiguring.isCompleted)
        assertFalse(state.isGenerationPermitValid(stalePermit))
        assertEquals(listOf("cancel"), engine.events)
        finish.complete(Unit)
        generating.join()

        assertTrue(reconfiguring.await().isSuccess)
        assertFalse(unavailable)
        assertEquals(listOf("cancel", "generation ended", "close:old", "create:new"), engine.events)
        assertEquals(1, engine.peakSessions)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun cancellationFailureDoesNotInstallNewSettingsOrReuseOldConversation() = runBlocking {
        val state = RuntimeCancellationState()
        val failure = IllegalStateException("cancel failed")
        var unavailable = false
        var replaced = false
        val result = reconfigureRuntimeConversation(state, Mutex(), Mutex(), requestCancellation = { failure },
            invalidateConversation = { unavailable = true }) { replaced = true }

        assertSame(failure, result.exceptionOrNull())
        assertTrue(unavailable)
        assertFalse(replaced)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun rejectedCapabilitiesLeaveSettingsUnchangedAndOldConversationUnavailable() = runBlocking {
        val settings = ComputeOptions(artifactIdentity = "model")
        var active = settings
        var unavailable = false
        val result = reconfigureRuntimeConversation(RuntimeCancellationState(), Mutex(), Mutex(),
            requestCancellation = { null }, invalidateConversation = { unavailable = true }) {
            val requested = settings.copy(thinkingEnabled = true)
            ComputeCapabilities(2_048, true, false, false).validate(requested)
            active = requested
            unavailable = false
        }

        assertTrue(result.isFailure)
        assertEquals(settings, active)
        assertTrue(unavailable)
    }

    @Test
    fun failedAllocationCannotPublishSettingsOrKeepOldConversation() = runBlocking {
        val engine = FakeConversationEngine()
        var current: FakeConversationEngine.Conversation? = engine.create("old")
        val original = ComputeOptions(artifactIdentity = "model")
        var settings = original
        var unavailable = false
        engine.failNextCreate = true
        val result = reconfigureRuntimeConversation(RuntimeCancellationState(), Mutex(), Mutex(),
            requestCancellation = { null }, invalidateConversation = { unavailable = true }) {
            replaceRuntimeResourceAfterClosingPrevious(
                previous = current,
                clearPrevious = { current = null },
                createReplacement = { engine.create("new") },
                installReplacement = { current = it; settings = original.copy(maxOutputTokens = 128); unavailable = false },
                closePrevious = { it.close() },
            )
        }

        assertEquals("create failed", result.exceptionOrNull()!!.message)
        assertNull(current)
        assertEquals(original, settings)
        assertTrue(unavailable)
        assertEquals(0, engine.liveSessions)
    }

    @Test
    fun failedCloseRetainsOwnershipButNotPermissionToGenerate() = runBlocking {
        val engine = FakeConversationEngine()
        val original = engine.create("old").apply { failClose = true }
        var current: FakeConversationEngine.Conversation? = original
        var unavailable = false
        val result = reconfigureRuntimeConversation(RuntimeCancellationState(), Mutex(), Mutex(),
            requestCancellation = { null }, invalidateConversation = { unavailable = true }) {
            replaceRuntimeResourceAfterClosingPrevious(
                previous = current,
                clearPrevious = { current = null },
                createReplacement = { engine.create("new") },
                installReplacement = { current = it; unavailable = false },
                closePrevious = { it.close() },
            )
        }

        assertEquals("close failed", result.exceptionOrNull()!!.message)
        assertSame(original, current)
        assertTrue(unavailable)
        assertEquals(1, engine.liveSessions)
    }

    @Test
    fun cancelledWaitStillInvalidatesConversationAndEndsLifecycleTransition() = runBlocking {
        val state = RuntimeCancellationState()
        val generation = Mutex(locked = true)
        var unavailable = false
        var replaced = false
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            reconfigureRuntimeConversation(state, generation, Mutex(), requestCancellation = { null },
                invalidateConversation = { unavailable = true }) { replaced = true }
        }
        operation.cancelAndJoin()
        generation.unlock()

        assertTrue(unavailable)
        assertFalse(replaced)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun thrownCancellationPropagatesWithOldConversationInvalidated() = runBlocking {
        val state = RuntimeCancellationState()
        val failure = CancellationException("native cancel interrupted")
        var unavailable = false
        var replaced = false
        val actual = try {
            reconfigureRuntimeConversation(state, Mutex(), Mutex(), requestCancellation = { throw failure },
                invalidateConversation = { unavailable = true }) { replaced = true }
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(failure, actual)
        assertTrue(unavailable)
        assertFalse(replaced)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }
}
