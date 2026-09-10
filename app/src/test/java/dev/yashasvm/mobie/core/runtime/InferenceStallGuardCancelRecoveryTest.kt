package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceStallGuardCancelRecoveryTest {
    @Test
    fun failedExplicitCancelBlocksFurtherGenerationUntilUnloadSucceeds() = runBlocking {
        val delegate = CancelFailingRuntimeAdapter()
        val adapter = InferenceStallGuardRuntimeAdapter(
            delegate = delegate,
            firstEventTimeoutMs = 100L,
            activeIdleTimeoutMs = 100L,
            cancellationTimeoutMs = 100L,
            lifecycleTimeoutMs = 100L,
        )

        val cancelFailure = runCatching { adapter.cancel() }.exceptionOrNull()
        assertTrue(cancelFailure is IllegalStateException)

        val blockedEvents = adapter.generate("must not run").toList()
        assertEquals(0, delegate.generateCalls)
        val blockedError = blockedEvents.single() as InferenceEvent.Error
        assertTrue(blockedError.requiresReload)
        assertTrue(blockedError.message.contains("reload", ignoreCase = true))

        delegate.cancelFailure = null
        adapter.unload()

        val recoveredEvents = adapter.generate("runs after cleanup").toList()
        assertEquals(1, delegate.generateCalls)
        assertEquals(listOf(InferenceEvent.Complete), recoveredEvents)
    }

    private class CancelFailingRuntimeAdapter : RuntimeAdapter {
        override val format: ModelFormat = ModelFormat.LITERT_LM
        var cancelFailure: Exception? = IllegalStateException("native cancel failed")
        var generateCalls = 0

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
            generateCalls += 1
            return flowOf(InferenceEvent.Complete)
        }

        override suspend fun cancel() {
            cancelFailure?.let { throw it }
        }

        override suspend fun unload() = Unit
    }
}
