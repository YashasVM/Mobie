package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGuardRuntimeAdapterTest {
    @Test
    fun severeThermalStatusCapsGenerationLength() = runBlocking {
        val delegate = RecordingRuntimeAdapter()
        val adapter = ThermalGuardRuntimeAdapter(delegate) { 3 }

        val events = adapter.generate("hello", config = GenerationConfig(maxNewTokens = 1024)).toList()

        assertEquals(256, delegate.lastGenerationConfig?.maxNewTokens)
        assertEquals(listOf(InferenceEvent.Complete), events)
    }

    @Test
    fun moderateThermalStatusKeepsRequestedGenerationLength() = runBlocking {
        val delegate = RecordingRuntimeAdapter()
        val adapter = ThermalGuardRuntimeAdapter(delegate) { 2 }

        adapter.generate("hello", config = GenerationConfig(maxNewTokens = 777)).toList()

        assertEquals(777, delegate.lastGenerationConfig?.maxNewTokens)
    }

    @Test
    fun criticalAndHotterStatusesBlockInferenceBeforeRuntimeExecution() = runBlocking {
        for (status in 4..6) {
            val delegate = RecordingRuntimeAdapter()
            val adapter = ThermalGuardRuntimeAdapter(delegate) { status }

            val events = adapter.generate("hello").toList()

            assertFalse(delegate.generateCalled)
            assertEquals(1, events.size)
            val error = events.single() as InferenceEvent.Error
            assertTrue(error.message.contains("too hot", ignoreCase = true))
        }
    }

    @Test
    fun criticalEscalationDuringGenerationCancelsRuntimeAndStopsForwardingTokens() = runBlocking {
        var thermalStatus = 2
        val delegate = RecordingRuntimeAdapter(onAfterFirstToken = { thermalStatus = 4 })
        val adapter = ThermalGuardRuntimeAdapter(delegate) { thermalStatus }

        val events = adapter.generate("hello").toList()

        assertTrue(delegate.cancelCalled)
        assertEquals(2, events.size)
        assertEquals(InferenceEvent.Token("first"), events.first())
        val error = events.last() as InferenceEvent.Error
        assertTrue(error.message.contains("too hot", ignoreCase = true))
    }

    @Test
    fun policyNeverExpandsSmallUserGenerationLimit() {
        val decision = ThermalInferencePolicy.decide(thermalStatus = 3, requestedMaxNewTokens = 64)

        assertTrue(decision.allowed)
        assertEquals(64, decision.maxNewTokens)
    }

    private class RecordingRuntimeAdapter(
        private val onAfterFirstToken: (() -> Unit)? = null,
    ) : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        var generateCalled = false
        var cancelCalled = false
        var lastGenerationConfig: GenerationConfig? = null

        override suspend fun load(
            modelPath: String,
            vision: Boolean,
            history: List<RuntimeMessage>,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun resetConversation(history: List<RuntimeMessage>): Result<Unit> = Result.success(Unit)

        override fun generate(
            prompt: String,
            imagePath: String?,
            config: GenerationConfig,
        ): Flow<InferenceEvent> {
            generateCalled = true
            lastGenerationConfig = config
            val callback = onAfterFirstToken ?: return flowOf(InferenceEvent.Complete)
            return flow {
                emit(InferenceEvent.Token("first"))
                callback()
                emit(InferenceEvent.Token("second"))
                emit(InferenceEvent.Complete)
            }
        }

        override suspend fun cancel() {
            cancelCalled = true
        }

        override suspend fun unload() = Unit
    }
}
