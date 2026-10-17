package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RuntimeOwnershipReviewTest {
    @Test
    fun nativeCancellationMustRunEvenWhenDefaultWorkersAreBlocked() = runBlocking {
        val count = maxOf(2, Runtime.getRuntime().availableProcessors())
        val started = CountDownLatch(count)
        val release = CountDownLatch(1)
        val blockers = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repeat(count) {
            blockers.launch { started.countDown(); release.await(10, TimeUnit.SECONDS) }
        }
        val delegate = FakeRuntime()
        val guard = InferenceStallGuardRuntimeAdapter(delegate, cancellationTimeoutMs = 100)
        try {
            assertTrue("Could not saturate Default dispatcher", started.await(5, TimeUnit.SECONDS))
            runCatching { guard.cancel() }
        } finally {
            release.countDown()
            blockers.cancel()
        }
        delay(200)
        assertEquals("Native cancellation was queued on the saturated dispatcher and discarded", 1, delegate.cancels.get())
    }

    @Test
    fun reloadRequiredNativeErrorMustBlockFurtherGeneration() = runBlocking {
        val delegate = FakeRuntime()
        val guard = InferenceStallGuardRuntimeAdapter(delegate)
        assertTrue((guard.generate("first").toList().single() as InferenceEvent.Error).requiresReload)
        guard.generate("second").toList()
        assertEquals("A second prompt reached native state that requires reload", 1, delegate.generations.get())
    }

    @Test
    fun criticalThermalStatePassesApplyPreflightButNativeLoadRefusesIt() {
        val gib = 1024L * 1024L * 1024L
        val artifact = dev.yashasvm.mobie.core.model.ModelArtifact(
            "Qwen3-0.6B.litertlm", "", 344_671_744L,
            format = ModelFormat.LITERT_LM, contextWindowTokens = 4096,
        )
        val device = dev.yashasvm.mobie.core.model.DeviceProfile(
            totalRamBytes = 12 * gib, availableRamBytes = 8 * gib,
            availableStorageBytes = 20 * gib, supportedAbis = listOf("arm64-v8a"),
            sdkInt = 35, thermalStatus = 4,
        )
        val preflight = dev.yashasvm.mobie.core.device.CompatibilityResolver().resolve(
            artifact, device, backend = ComputeBackend.CPU,
        )
        // These are applyComputeSettings' exact refusal conditions.
        assertFalse(preflight.status == dev.yashasvm.mobie.core.model.Compatibility.INCOMPATIBLE ||
            preflight.memoryTier == dev.yashasvm.mobie.core.model.MemoryAdmissionTier.FITS_AFTER_RECLAIM)
        assertEquals(dev.yashasvm.mobie.core.model.Compatibility.WARNING, preflight.status)
        val nativeRefusal = RuntimeLoadMemoryPolicy.blockReason(
            modelWeightsBytes = artifact.sizeBytes, totalRamBytes = device.totalRamBytes,
            availableRamBytes = device.availableRamBytes, lowMemoryThresholdBytes = 0,
            isLowMemory = false, isLowRamDevice = false, thermalStatus = 4,
        )
        assertNotNull(nativeRefusal)
        assertTrue(nativeRefusal!!.contains("critical thermal"))
    }

    private class FakeRuntime : RuntimeAdapter {
        override val format = ModelFormat.LITERT_LM
        val cancels = AtomicInteger()
        val generations = AtomicInteger()
        override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>) = Result.success(Unit)
        override suspend fun resetConversation(history: List<RuntimeMessage>) = Result.success(Unit)
        override fun generate(prompt: String, imagePath: String?, config: GenerationConfig): Flow<InferenceEvent> {
            generations.incrementAndGet()
            return generationEventFlow { throw OutOfMemoryError("native allocation failed") }
        }
        override suspend fun cancel() { cancels.incrementAndGet() }
        override suspend fun unload() = Unit
    }
}
