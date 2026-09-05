package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGuardRuntimeAdapterTest {
    @Test
    fun severeThermalStatusCapsGenerationLength() = runTest {
        val delegate = RecordingRuntimeAdapter()
        val adapter = ThermalGuardRuntimeAdapter(delegate) { 3 }

        val events = adapter.generate("hello", config = GenerationConfig(maxNewTokens = 1024)).toList()

        assertEquals(256, delegate.lastGenerationConfig?.maxNewTokens)
        assertEquals(listOf(InferenceEvent.Complete), events)
    }

    @Test
    fun moderateThermalStatusKeepsRequestedGenerationLength() = runTest {
        val delegate = RecordingRuntimeAdapter()
        val adapter = ThermalGuardRuntimeAdapter(delegate) { 2 }

        adapter.generate("hello", config = GenerationConfig(maxNewTokens = 777)).toList()

        assertEquals(777, delegate.lastGenerationConfig?.maxNewTokens)
    }

    @Test
    fun criticalAndHotterStatusesBlockInferenceBeforeRuntimeExecution() = runTest {
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
    fun policyNeverExpandsSmallUserGenerationLimit() {
        val decision = ThermalInferencePolicy.decide(thermalStatus = 3, requestedMaxNewTokens = 64)

        assertTrue(decision.allowed)
        assertEquals(64, decision.maxNewTokens)
    }

    private class RecordingRuntimeAdapter : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        var generateCalled = false
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
            return flowOf(InferenceEvent.Complete)
        }

        override suspend fun cancel() = Unit

        override suspend fun unload() = Unit
    }
}
