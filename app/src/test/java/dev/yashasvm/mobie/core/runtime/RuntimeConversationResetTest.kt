package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConversationResetTest {
    @Test
    fun resetDuringGenerationCancelsAndWaitsBeforeCloseAndCreate() = runBlocking {
        val engine = FakeConversationEngine()
        var conversation = engine.create("old chat")
        engine.events.clear()
        val state = RuntimeCancellationState()
        val generation = Mutex()
        val cancelReceived = CompletableDeferred<Unit>()
        val finishGeneration = CompletableDeferred<Unit>()
        val generating = launch(start = CoroutineStart.UNDISPATCHED) {
            generation.withLock {
                state.beginGeneration()
                conversation.generating = true
                try {
                    finishGeneration.await()
                } finally {
                    conversation.generating = false
                    engine.events += "generation ended"
                    state.endGeneration()
                }
            }
        }
        val oldPermit = state.captureGenerationPermit()
        val resetting = async(start = CoroutineStart.UNDISPATCHED) {
            resetRuntimeConversation(state, generation, Mutex(), requestCancellation = {
                state.attempt {
                    engine.events += "cancel"
                    cancelReceived.complete(Unit)
                }
            }) {
                replaceRuntimeResourceAfterClosingPrevious(
                    previous = conversation,
                    clearPrevious = {},
                    createReplacement = { engine.create("new chat") },
                    installReplacement = { conversation = it },
                    closePrevious = { it.close() },
                )
            }
        }

        cancelReceived.await()
        assertFalse(resetting.isCompleted)
        assertEquals(listOf("cancel"), engine.events)
        assertEquals(1, engine.liveSessions)
        assertFalse(state.isGenerationPermitValid(oldPermit))
        finishGeneration.complete(Unit)
        generating.join()
        assertTrue(resetting.await().isSuccess)
        assertEquals(listOf("cancel", "generation ended", "close:old chat", "create:new chat"), engine.events)
        assertEquals("new chat", conversation.history)
        assertEquals(1, engine.peakSessions)
        assertFalse(state.isGenerationActive())
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun failedResetSurfacesAllocationErrorAndReopensGenerationAdmission() = runBlocking {
        val engine = FakeConversationEngine()
        var conversation: FakeConversationEngine.Conversation? = engine.create("old chat")
        val state = RuntimeCancellationState()
        engine.failNextCreate = true

        val result = resetRuntimeConversation(state, Mutex(), Mutex(), requestCancellation = {
            state.attempt { error("Idle reset must not cancel native work") }
        }) {
            replaceRuntimeResourceAfterClosingPrevious(
                previous = conversation,
                clearPrevious = { conversation = null },
                createReplacement = { engine.create("new chat") },
                installReplacement = { conversation = it },
                closePrevious = { it.close() },
            )
        }

        assertEquals("create failed", result.exceptionOrNull()!!.message)
        assertEquals(null, conversation)
        assertEquals(0, engine.liveSessions)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }

    @Test
    fun cancellationFailureIsSurfacedAfterSafeReplacement() = runBlocking {
        val state = RuntimeCancellationState()
        val engine = FakeConversationEngine()
        val previous = engine.create("old chat")
        val cancellationFailure = IllegalStateException("cancel failed")
        val result = resetRuntimeConversation(state, Mutex(), Mutex(), requestCancellation = {
            cancellationFailure
        }) {
            replaceRuntimeResourceAfterClosingPrevious(
                previous = previous,
                clearPrevious = {},
                createReplacement = { engine.create("new chat") },
                installReplacement = {},
                closePrevious = { it.close() },
            )
        }

        assertSame(cancellationFailure, result.exceptionOrNull())
        assertEquals(1, engine.liveSessions)
        assertEquals(1, engine.peakSessions)
        assertTrue(state.isGenerationPermitValid(state.captureGenerationPermit()))
    }
}
