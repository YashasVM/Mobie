package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeImageCapabilityTest {
    @Test
    fun `guards follow the loaded image encoder through load reset fallback and unload`() = runBlocking {
        val native = CapabilityRuntime()
        val runtime = ThermalGuardRuntimeAdapter(
            delegate = InferenceStallGuardRuntimeAdapter(native),
            thermalStatusProvider = { 0 },
        )

        assertFalse(runtime.supportsImageInput)
        runtime.load("model.litertlm", vision = true).getOrThrow()
        assertTrue(runtime.supportsImageInput)
        runtime.resetConversation().getOrThrow()
        assertTrue(runtime.supportsImageInput)
        runtime.unload()
        assertFalse(runtime.supportsImageInput)

        native.imageEncoderAvailable = false
        runtime.load("model.litertlm", vision = true).getOrThrow()
        assertFalse(runtime.supportsImageInput)
    }

    @Test
    fun `uncertain cancellation disables image capability until successful recovery`() = runBlocking {
        val native = CapabilityRuntime()
        val runtime = InferenceStallGuardRuntimeAdapter(native)
        runtime.load("model.litertlm", vision = true).getOrThrow()
        assertTrue(runtime.supportsImageInput)

        native.failCancellation = true
        assertTrue(runCatching { runtime.cancel() }.isFailure)
        assertFalse(runtime.supportsImageInput)

        runtime.unload()
        native.failCancellation = false
        runtime.load("model.litertlm", vision = true).getOrThrow()
        assertTrue(runtime.supportsImageInput)
    }

    private class CapabilityRuntime : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        override var supportsImageInput = false
            private set
        var imageEncoderAvailable = true
        var failCancellation = false

        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>): Result<Unit> {
            supportsImageInput = vision && imageEncoderAvailable
            return Result.success(Unit)
        }

        override suspend fun resetConversation(history: List<RuntimeMessage>) = Result.success(Unit)

        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> =
            flowOf(InferenceEvent.Complete)

        override suspend fun cancel() {
            check(!failCancellation) { "Cancellation failed" }
        }

        override suspend fun unload() {
            supportsImageInput = false
        }
    }
}
