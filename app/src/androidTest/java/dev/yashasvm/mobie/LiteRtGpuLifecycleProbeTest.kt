package dev.yashasvm.mobie

import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Existing local artifact only. Exercises one adapter and process across backend changes. */
class LiteRtGpuLifecycleProbeTest {
    @Test fun cancelsReloadsAndSwitchesWithoutRestart(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("gpuLifecycleProbe") == "true")
        assertTrue("Physical ARM64 required", "arm64-v8a" in Build.SUPPORTED_ABIS && Build.HARDWARE !in listOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, checkNotNull(args.getString("modelPath"))).canonicalFile
        require(model.path.startsWith(context.filesDir.canonicalPath + "/") && model.isFile)
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val sampler = SamplerSettings(0.0, 1, 1.0, 0)
        val adapter = LiteRtLmRuntimeAdapter(context)
        val output = File(context.filesDir, "gpu-lifecycle-probe.jsonl")
        val cache = File(model.parentFile, ".litert-cache")
        fun cacheBytes() = cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        adapter.configureSampler(sampler)
        adapter.configureThinking(false)
        try {
            for ((index, backend) in listOf(ComputeBackend.GPU, ComputeBackend.GPU, ComputeBackend.CPU, ComputeBackend.GPU).withIndex()) {
                adapter.configureCompute(ComputeOptions(backend, cpuThreads = 4, contextTokens = 4_096))
                val peak = AtomicLong(Debug.getPss() * 1024L)
                val thermalMax = AtomicInteger(power.currentThermalStatus)
                val result = JSONObject().put("case", index).put("requested_backend", backend.name)
                    .put("model", model.name).put("artifact_sha256", sha).put("quantization", "INT4 block32 / FP32 artifact activations")
                    .put("device", Build.MODEL).put("android", Build.VERSION.RELEASE).put("runtime_version", BuildConfig.LITERT_LM_VERSION)
                    .put("pid", android.os.Process.myPid()).put("thermal_start", power.currentThermalStatus).put("cache_before_bytes", cacheBytes())
                coroutineScope {
                    val monitor = launch(Dispatchers.Default) {
                        while (isActive) {
                            peak.accumulateAndGet(Debug.getPss() * 1024L, ::maxOf)
                            thermalMax.accumulateAndGet(power.currentThermalStatus, ::maxOf)
                            delay(100)
                        }
                    }
                    try {
                        val loadStart = SystemClock.elapsedRealtime()
                        withTimeout(120_000) { adapter.load(model.path).getOrThrow() }
                        result.put("load_ms", SystemClock.elapsedRealtime() - loadStart)
                        assertEquals(backend, adapter.activeCompute?.backend)
                        if (index == 0) {
                            var chunks = 0
                            var complete = false
                            try {
                            withTimeout(120_000) {
                                adapter.generate("Write a long numbered list of 100 practical uses for local AI on a phone. /no_think",
                                    config = GenerationConfig(512, sampler)).collect { event ->
                                    when (event) {
                                        is InferenceEvent.Token -> if (++chunks == 4) adapter.cancel()
                                        InferenceEvent.Complete -> complete = true
                                        is InferenceEvent.Error -> error(event.message)
                                        else -> Unit
                                    }
                                }
                            }
                            } catch (cancelled: CancellationException) {
                                if (chunks < 4) throw cancelled
                            }
                            assertTrue("Cancellation must happen during output", chunks >= 4 && !complete)
                            result.put("cancel_mid_generation", true)
                        }
                        var visible = false
                        withTimeout(120_000) {
                            adapter.generate("Explain in a few concise sentences why local AI inference can be useful on a phone. /no_think",
                                config = GenerationConfig(64, sampler)).collect { event ->
                                when (event) {
                                    is InferenceEvent.Token -> if (!event.thinking && event.text.isNotEmpty()) visible = true
                                    is InferenceEvent.Error -> error(event.message)
                                    is InferenceEvent.Stats -> result.put("decode_tps", event.value.tokensPerSecond)
                                        .put("first_chunk_ms", event.value.timeToFirstTokenMs)
                                        .put("first_visible_ms", event.value.timeToFirstVisibleOutputMs)
                                    else -> Unit
                                }
                            }
                        }
                        assertTrue("Recovery/switch must produce a visible answer", visible)
                        result.put("loaded_pss_bytes", Debug.getPss() * 1024L)
                        adapter.unload()
                        assertEquals(null, adapter.activeCompute)
                        assertEquals(null, adapter.computeCapabilities)
                        delay(500)
                        result.put("unloaded_pss_bytes", Debug.getPss() * 1024L)
                            .put("cache_after_bytes", cacheBytes()).put("result", "passed")
                    } finally { monitor.cancel() }
                }
                result.put("peak_pss_bytes", peak.get()).put("thermal_max", thermalMax.get())
                    .put("thermal_end", power.currentThermalStatus)
                output.appendText(result.toString() + "\n")
                android.util.Log.i("MobieGpuLifecycle", result.toString())
            }
        } finally { adapter.unload() }
    }
}
