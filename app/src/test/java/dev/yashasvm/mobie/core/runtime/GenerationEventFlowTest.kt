package dev.yashasvm.mobie.core.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GenerationEventFlowTest {
    /** Stands in for a collector that throws during an emit, such as a cancelled or broken consumer. */
    private class DownstreamFailure : RuntimeException("collector failed during emit")

    @Test
    fun downstreamFailureDuringEmitPropagatesUnchangedAndIsNotFollowedByAnotherEmit() = runBlocking {
        val failure = DownstreamFailure()
        var interrupted = false
        val stream = generationEventFlow {
            // Mirrors LiteRT generate: emits sit inside a try, and its catch only cleans up and rethrows.
            try {
                emit(InferenceEvent.Token("hello"))
                emit(InferenceEvent.Complete)
            } catch (error: Throwable) {
                interrupted = true
                throw error
            }
        }
        val collector = FlowCollector<InferenceEvent> { event ->
            if (event is InferenceEvent.Token) throw failure
        }

        val caught = try {
            stream.collect(collector)
            fail("The downstream failure must reach the caller")
            null
        } catch (error: Throwable) {
            error
        }

        // A second emit after a failed emit would surface as a Flow exception-transparency IllegalStateException.
        assertSame(failure, caught)
        assertTrue("Native cleanup still runs for a downstream failure", interrupted)
    }

    @Test
    fun upstreamFailureBecomesOneTerminalErrorAfterTheTokensAlreadyEmitted() = runBlocking {
        val stream = generationEventFlow {
            emit(InferenceEvent.Token("partial"))
            throw IllegalStateException("native decode failed")
        }

        val events = stream.toList()

        assertEquals(
            listOf(
                InferenceEvent.Token("partial"),
                InferenceEvent.Error("native decode failed", requiresReload = false),
            ),
            events,
        )
    }

    @Test
    fun nonFatalNativeErrorNeedsReloadWhenItIsNotAnException() = runBlocking {
        val stream = generationEventFlow { throw LinkageError("native library mismatch") }

        val events = stream.toList()

        assertEquals(listOf(InferenceEvent.Error("native library mismatch", requiresReload = true)), events)
    }

    @Test
    fun cancellationPropagatesWithoutAnErrorEvent() = runBlocking {
        val stream = generationEventFlow {
            emit(InferenceEvent.Token("partial"))
            throw CancellationException("Generation cancelled")
        }
        val seen = mutableListOf<InferenceEvent>()

        try {
            stream.collect { seen += it }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("Generation cancelled", expected.message)
        }

        assertFalse(seen.any { it is InferenceEvent.Error })
    }
}
