package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class RuntimeCleanupDispatchTest {
    @Test
    fun cleanupSurvivesADispatcherHopWhileDefaultIsBlocked() = runBlocking {
        val count = maxOf(2, Runtime.getRuntime().availableProcessors())
        val started = CountDownLatch(count)
        val release = CountDownLatch(1)
        val blockers = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repeat(count) { blockers.launch { started.countDown(); release.await(10, TimeUnit.SECONDS) } }
        val calls = AtomicInteger()
        val runtime = object : RuntimeAdapter {
            override val format = ModelFormat.LITERT_LM
            override suspend fun load(modelPath: String, vision: Boolean, history: List<RuntimeMessage>) = Result.success(Unit)
            override suspend fun resetConversation(history: List<RuntimeMessage>) = Result.success(Unit)
            override fun generate(prompt: String, imagePath: String?, config: GenerationConfig) = flowOf(InferenceEvent.Complete)
            override suspend fun cancel() = withContext(Dispatchers.IO) { calls.incrementAndGet(); Unit }
            override suspend fun unload() = withContext(Dispatchers.IO) { calls.incrementAndGet(); Unit }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val guard = InferenceStallGuardRuntimeAdapter(runtime, cancellationTimeoutMs = 100, lifecycleTimeoutMs = 100)
            runCatching { guard.cancel() }
            runCatching { guard.unload() }
        } finally {
            release.countDown()
            blockers.cancel()
        }
        delay(200)
        assertEquals(2, calls.get())
    }
}
