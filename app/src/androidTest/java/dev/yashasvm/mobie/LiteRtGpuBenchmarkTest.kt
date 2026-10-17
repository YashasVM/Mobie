package dev.yashasvm.mobie

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.model.inferArtifactQuantization
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in benchmark of the production adapter. Never evidence from an emulator. */
@RunWith(AndroidJUnit4::class)
class LiteRtGpuBenchmarkTest {
    @Test
    fun measuresProductionBackendOnPhysicalArm64(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("gpuBenchmark") == "true")
        assertTrue(
            "A physical ARM64 phone is required",
            "arm64-v8a" in Build.SUPPORTED_ABIS &&
                !Build.FINGERPRINT.startsWith("generic") &&
                Build.HARDWARE !in listOf("goldfish", "ranchu") &&
                !Build.PRODUCT.contains("sdk_gphone"),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = checkNotNull(args.getString("modelPath"))
        val model = File(context.filesDir, relativePath).canonicalFile
        require(model.path.startsWith(context.filesDir.canonicalPath + "/") && model.isFile)
        val backend = ComputeBackend.valueOf(args.getString("backend") ?: "AUTO")
        val threads = args.getString("cpuThreads")?.toInt()
        val repetitions = (args.getString("repetitions")?.toInt() ?: 3).also { require(it in 1..20) }
        val outputTokens = (args.getString("outputTokens")?.toInt() ?: 64).also { require(it in 1..1024) }
        val thinkingOff = args.getString("thinkingOff") == "true"
        val sampler = if (args.getString("galleryEquivalent") == "true") {
            SamplerSettings(temperature = 1.0, topK = 64, topP = 0.95, seed = 0)
        } else SamplerSettings(temperature = 0.0, topK = 1, topP = 1.0, seed = 0)
        val options = ComputeOptions(
            backend = backend,
            cpuThreads = threads,
            contextTokens = args.getString("contextTokens")?.toInt(),
            maxOutputTokens = outputTokens,
            thinkingEnabled = if (thinkingOff) false else null,
            thinkingTokenBudget = args.getString("thinkingBudget")?.toInt(),
            activationType = ActivationType.valueOf(args.getString("activationType") ?: "AUTO"),
            speculativeDecoding = args.getString("speculativeDecoding")?.toBooleanStrict(),
        )
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val sustainedSeconds = (args.getString("sustainedSeconds")?.toInt() ?: 0).also { require(it in 0..600) }
        val coolStart = args.getString("coolStart") == "true"
        val holdSeconds = (args.getString("holdSeconds")?.toInt() ?: 0).also { require(it in 0..120) }
        val resultFile = File(context.filesDir, "gpu-benchmark.jsonl")
        repeat(repetitions) { iteration ->
            if (coolStart) {
                val power = context.getSystemService(PowerManager::class.java)
                val deadline = SystemClock.elapsedRealtime() + 180_000L
                while (power.currentThermalStatus != PowerManager.THERMAL_STATUS_NONE && SystemClock.elapsedRealtime() < deadline) {
                    delay(1000)
                }
                check(power.currentThermalStatus == PowerManager.THERMAL_STATUS_NONE) { "Phone did not cool to thermal status NONE" }
            }
            val result = measure(context, model, options, sampler, outputTokens, thinkingOff, sustainedSeconds, holdSeconds)
                .put("iteration", iteration)
                .put("model", model.name)
                .put("artifact_bytes", model.length())
                .put("artifact_sha256", sha)
                .put("quantization", inferArtifactQuantization(model.name) ?: "unknown")
                .put("requested_context_tokens", options.contextTokens)
                .put("activation_type", options.activationType.name)
                .put("speculative_decoding", options.speculativeDecoding)
                .put("temperature", sampler.temperature).put("top_k", sampler.topK).put("top_p", sampler.topP)
                .put("gallery_equivalent", args.getString("galleryEquivalent") == "true")
                .put("device", Build.MODEL)
                .put("android", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("runtime_version", BuildConfig.LITERT_LM_VERSION)
                .put("output_budget", outputTokens)
                .put("thinking_off", thinkingOff)
                .put("sustained_seconds", sustainedSeconds)
            resultFile.appendText(result.toString() + "\n")
            android.util.Log.i("MobieGpuBenchmark", result.toString())
            assertTrue(result.toString(), !result.has("error"))
            if (backend != ComputeBackend.AUTO) {
                assertTrue("Requested backend must be used: $result", result.getString("active_backend") == backend.name)
            }
        }
    }

    private suspend fun measure(context: Context, model: File, options: ComputeOptions, sampler: SamplerSettings, outputTokens: Int,
        thinkingOff: Boolean, sustainedSeconds: Int, holdSeconds: Int): JSONObject =
        withContext(Dispatchers.Default) {
            coroutineScope {
                val power = context.getSystemService(PowerManager::class.java)
                fun thermal() = power.currentThermalStatus
                val thermalStart = thermal()
                val thermalMax = AtomicInteger(thermalStart)
                val peakPss = AtomicLong(Debug.getPss() * 1024L)
                val monitor = launch {
                    while (isActive) {
                        peakPss.accumulateAndGet(Debug.getPss() * 1024L, ::maxOf)
                        thermalMax.accumulateAndGet(thermal(), ::maxOf)
                        delay(100)
                    }
                }
                val result = JSONObject().put("requested_backend", options.backend.name).put("cpu_threads", options.cpuThreads)
                val adapter = LiteRtLmRuntimeAdapter(context)
                adapter.configureCompute(options)
                adapter.configureSampler(sampler)
                if (thinkingOff) adapter.configureThinking(false)
                try {
                    val started = SystemClock.elapsedRealtime()
                    withTimeout(5 * 60_000L) { adapter.load(model.path).getOrThrow() }
                    result.put("load_ms", SystemClock.elapsedRealtime() - started)
                    result.put("active_backend", checkNotNull(adapter.activeCompute).backend.name)
                    result.put("active_cpu_threads", adapter.activeCompute?.cpuThreads)
                    result.put("fallback_reason", adapter.activeCompute?.fallbackReason)
                    result.put("context_tokens", adapter.activeCompute?.contextTokens)
                    result.put("artifact_max_context", adapter.computeCapabilities?.maxContextTokens)
                    result.put("dynamic_context", adapter.computeCapabilities?.dynamicContext)
                    result.put("supports_speculative", adapter.computeCapabilities?.supportsSpeculativeDecoding)
                    val samples = JSONArray()
                    suspend fun generateSample(): JSONObject {
                        var chunks = 0
                        var thinkingChunks = 0
                        val visible = StringBuilder()
                        val sample = JSONObject().put("thermal_start", thermal())
                        val sampleThermalMax = AtomicInteger(thermal())
                        val samplePeakPss = AtomicLong(Debug.getPss() * 1024L)
                        val sampleMonitor = launch {
                            while (isActive) {
                                sampleThermalMax.accumulateAndGet(thermal(), ::maxOf)
                                samplePeakPss.accumulateAndGet(Debug.getPss() * 1024L, ::maxOf)
                                delay(100)
                            }
                        }
                        try {
                        withTimeout(5 * 60_000L) {
                        adapter.generate(PROMPT, config = GenerationConfig(maxNewTokens = outputTokens, sampler = sampler)).collect { event ->
                            when (event) {
                                is InferenceEvent.Token -> { chunks++; if (event.thinking) thinkingChunks++ else visible.append(event.text) }
                                is InferenceEvent.Error -> sample.put("generation_error", event.message)
                                is InferenceEvent.Stats -> {
                                    val stats = event.value
                                    sample.put("decode_tps", stats.tokensPerSecond)
                                        .put("prefill_tps", stats.prefillTokensPerSecond)
                                        .put("decode_tokens", stats.decodeTokenCount)
                                        .put("prefill_tokens", stats.prefillTokenCount)
                                        .put("first_chunk_ms", stats.timeToFirstTokenMs)
                                        .put("first_visible_ms", stats.timeToFirstVisibleOutputMs)
                                        .put("generation_ms", stats.totalGenerationMs)
                                }
                                else -> Unit
                            }
                        }
                        }
                        } finally {
                            sampleMonitor.cancel()
                        }
                        sample.put("thermal_end", thermal()).put("thinking_chunks", thinkingChunks)
                            .put("thermal_max", sampleThermalMax.get()).put("peak_pss_bytes", samplePeakPss.get())
                            .put("visible_sha256", MessageDigest.getInstance("SHA-256").digest(visible.toString().toByteArray())
                                .joinToString("") { "%02x".format(it) })
                        check(!sample.has("generation_error")) { sample.getString("generation_error") }
                        check(chunks > 0 && sample.optDouble("decode_tps") > 0.0) { "No measured output" }
                        if (thinkingOff) check(thinkingChunks == 0) { "Reasoning appeared despite thinking-off configuration" }
                        return sample
                    }
                    val first = generateSample()
                    for (key in first.keys()) result.put(key, first.get(key))
                    if (holdSeconds > 0) {
                        android.util.Log.i("MobieGpuBenchmark", "Holding loaded engine for memory inspection; pid=${android.os.Process.myPid()}")
                        delay(holdSeconds * 1000L)
                    }
                    if (sustainedSeconds > 0) {
                        result.put("warmup", first)
                        val sustainedStart = SystemClock.elapsedRealtime()
                        do {
                            adapter.resetConversation().getOrThrow()
                            val sample = generateSample().put("elapsed_ms", SystemClock.elapsedRealtime() - sustainedStart)
                            samples.put(sample)
                            android.util.Log.i("MobieGpuSustained", sample.toString())
                        } while (SystemClock.elapsedRealtime() - sustainedStart < sustainedSeconds * 1000L)
                        result.put("samples", samples).put("measured_duration_ms", SystemClock.elapsedRealtime() - sustainedStart)
                    }
                } catch (failure: Exception) {
                    result.put("error", failure.message ?: failure.javaClass.simpleName)
                } finally {
                    adapter.unload()
                    monitor.cancel()
                    result.put("peak_pss_bytes", peakPss.get())
                        .put("thermal_start", thermalStart).put("thermal_max", thermalMax.get())
                        .put("thermal_end", thermal())
                }
                result
            }
        }

    private companion object {
        const val PROMPT = "Explain in a few concise sentences why local AI inference can be useful on a phone. /no_think"
    }
}
