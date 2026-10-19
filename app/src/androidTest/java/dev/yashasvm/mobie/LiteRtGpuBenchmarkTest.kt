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
import java.util.concurrent.atomic.AtomicReference
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
        // The short fixture stays the default so existing invocations keep their prompt ID and bytes.
        val fixture = PromptFixture.fromArg(args.getString("promptFixture"))
        val minDecodeTokens = (args.getString("minDecodeTokens")?.toInt() ?: fixture.defaultMinDecodeTokens).also {
            require(it in fixture.minDecodeTokensFloor..outputTokens) {
                "minDecodeTokens=$it must be in ${fixture.minDecodeTokensFloor}..outputTokens($outputTokens) for ${fixture.argName}"
            }
        }
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
        // Hash outside timed regions. The APK identity is exact even without Git metadata in a build.
        val sha = sha256(model)
        val appApkSha = sha256(File(context.applicationInfo.sourceDir))
        val testApkSha = sha256(File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir))
        val splitApks = JSONArray().apply {
            context.applicationInfo.splitSourceDirs.orEmpty().forEach { path ->
                put(JSONObject().put("name", File(path).name).put("sha256", sha256(File(path))))
            }
        }
        val promptBytes = fixture.prompt.toByteArray(Charsets.UTF_8)
        val promptSha = MessageDigest.getInstance("SHA-256").digest(promptBytes)
            .joinToString("") { "%02x".format(it) }
        val sustainedSeconds = (args.getString("sustainedSeconds")?.toInt() ?: 0).also { require(it in 0..600) }
        val coolStart = args.getString("coolStart") == "true"
        val holdSeconds = (args.getString("holdSeconds")?.toInt() ?: 0).also { require(it in 0..120) }
        val resultFile = File(context.filesDir, "gpu-benchmark.jsonl")
        val ineligibleIterations = mutableListOf<Int>()
        repeat(repetitions) { iteration ->
            if (coolStart) {
                val power = context.getSystemService(PowerManager::class.java)
                val deadline = SystemClock.elapsedRealtime() + 180_000L
                while (power.currentThermalStatus != PowerManager.THERMAL_STATUS_NONE && SystemClock.elapsedRealtime() < deadline) {
                    delay(1000)
                }
                check(power.currentThermalStatus == PowerManager.THERMAL_STATUS_NONE) { "Phone did not cool to thermal status NONE" }
            }
            val result = measure(context, model, options, sampler, outputTokens, thinkingOff, sustainedSeconds, holdSeconds,
                fixture, minDecodeTokens)
                .put("iteration", iteration)
                .put("benchmark_protocol", "mobie-production-adapter-v3")
                .put("comparison_target", "mobie_adapter_only")
                .put("os_page_cache_controlled", false)
                .put("persistent_cache_state", args.getString("cacheState") ?: "uncontrolled")
                .put("application_id", BuildConfig.APPLICATION_ID)
                .put("app_version_name", BuildConfig.VERSION_NAME)
                .put("app_version_code", BuildConfig.VERSION_CODE)
                .put("build_type", BuildConfig.BUILD_TYPE)
                .put("debuggable_build", BuildConfig.DEBUG)
                .put("build_revision", args.getString("buildRevision") ?: JSONObject.NULL)
                .put("app_apk_sha256", appApkSha)
                .put("test_apk_sha256", testApkSha)
                .put("split_apks", splitApks)
                .put("device_fingerprint", Build.FINGERPRINT)
                .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("prompt_fixture", fixture.argName)
                .put("fixture_role", fixture.role)
                .put("prompt_id", fixture.id)
                .put("prompt_sha256", promptSha)
                .put("prompt_bytes", promptBytes.size)
                .put("min_native_decode_tokens", minDecodeTokens)
                .put("model", model.name)
                .put("artifact_bytes", model.length())
                .put("artifact_sha256", sha)
                .put("quantization", inferArtifactQuantization(model.name) ?: "unknown")
                .put("requested_context_tokens", options.contextTokens)
                .put("activation_type", options.activationType.name)
                .put("speculative_decoding", options.speculativeDecoding ?: JSONObject.NULL)
                .put("temperature", sampler.temperature).put("top_k", sampler.topK).put("top_p", sampler.topP)
                .put("seed", sampler.seed)
                .put("thinking_enabled_request", options.thinkingEnabled ?: JSONObject.NULL)
                .put("thinking_budget", options.thinkingTokenBudget ?: JSONObject.NULL)
                .put("prefill_preface_on_init", options.prefillPrefaceOnInit)
                .put("gallery_equivalent", args.getString("galleryEquivalent") == "true")
                .put("gallery_sampler_only", args.getString("galleryEquivalent") == "true")
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
            if (fixture.role == ROLE_SUSTAINED && !result.optBoolean("all_generations_throughput_eligible")) {
                ineligibleIterations += iteration
            }
        }
        // Short outputs are kept as raw rows; the run still fails so it is not mistaken for sustained evidence.
        assertTrue(
            "Sustained-decode fixture produced throughput-ineligible generations in iterations $ineligibleIterations; " +
                "rows are retained in gpu-benchmark.jsonl",
            ineligibleIterations.isEmpty(),
        )
    }

    private suspend fun measure(context: Context, model: File, options: ComputeOptions, sampler: SamplerSettings, outputTokens: Int,
        thinkingOff: Boolean, sustainedSeconds: Int, holdSeconds: Int, fixture: PromptFixture, minDecodeTokens: Int): JSONObject =
        withContext(Dispatchers.Default) {
            coroutineScope {
                val power = context.getSystemService(PowerManager::class.java)
                fun thermal() = power.currentThermalStatus
                val thermalStart = thermal()
                val thermalMax = AtomicInteger(thermalStart)
                val peakPss = AtomicLong(Debug.getPss() * 1024L)
                val activeWindow = AtomicReference<MeasurementWindow?>(null)
                val monitor = launch {
                    while (isActive) {
                        // One page-map walk feeds both load-wide and per-generation peaks.
                        val pss = Debug.getPss() * 1024L
                        val thermalStatus = thermal()
                        peakPss.accumulateAndGet(pss, ::maxOf)
                        thermalMax.accumulateAndGet(thermalStatus, ::maxOf)
                        activeWindow.get()?.record(pss, thermalStatus)
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
                        var visibleChunks = 0
                        val visible = StringBuilder()
                        val sample = JSONObject().put("thermal_start", thermal())
                        val window = MeasurementWindow(Debug.getPss() * 1024L, thermal())
                        peakPss.accumulateAndGet(window.peakPss.get(), ::maxOf)
                        thermalMax.accumulateAndGet(window.thermalMax.get(), ::maxOf)
                        activeWindow.set(window)
                        try {
                        withTimeout(5 * 60_000L) {
                        adapter.generate(fixture.prompt, config = GenerationConfig(maxNewTokens = outputTokens, sampler = sampler)).collect { event ->
                            when (event) {
                                is InferenceEvent.Token -> {
                                    chunks++
                                    if (event.thinking) thinkingChunks++ else { visibleChunks++; visible.append(event.text) }
                                }
                                is InferenceEvent.Error -> sample.put("generation_error", event.message)
                                is InferenceEvent.Stats -> {
                                    val stats = event.value
                                    // decode_tokens is kept for older readers; native_decode_tokens names its source.
                                    sample.put("decode_tps", stats.tokensPerSecond)
                                        .put("prefill_tps", stats.prefillTokensPerSecond)
                                        .put("decode_tokens", stats.decodeTokenCount)
                                        .put("native_decode_tokens", stats.decodeTokenCount)
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
                            // An endpoint read covers replies shorter than the monitor interval.
                            val pss = Debug.getPss() * 1024L
                            val thermalStatus = thermal()
                            window.record(pss, thermalStatus)
                            peakPss.accumulateAndGet(pss, ::maxOf)
                            thermalMax.accumulateAndGet(thermalStatus, ::maxOf)
                            activeWindow.compareAndSet(window, null)
                        }
                        // SDK callback chunks are not tokens; a chunk can carry several native tokens.
                        sample.put("thermal_end", thermal()).put("thinking_chunks", thinkingChunks)
                            .put("sdk_chunks", chunks).put("visible_chunks", visibleChunks)
                            .put("thermal_max", window.thermalMax.get()).put("peak_pss_bytes", window.peakPss.get())
                            .put("visible_sha256", MessageDigest.getInstance("SHA-256").digest(visible.toString().toByteArray())
                                .joinToString("") { "%02x".format(it) })
                        check(!sample.has("generation_error")) { sample.getString("generation_error") }
                        check(chunks > 0 && sample.optDouble("decode_tps") > 0.0) { "No measured output" }
                        if (thinkingOff) check(thinkingChunks == 0) { "Reasoning appeared despite thinking-off configuration" }
                        recordThroughputEligibility(sample, fixture, minDecodeTokens, outputTokens)
                        return sample
                    }
                    val first = generateSample()
                    for (key in first.keys()) result.put(key, first.get(key))
                    var allEligible = first.getBoolean("throughput_eligible")
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
                            allEligible = allEligible && sample.getBoolean("throughput_eligible")
                            android.util.Log.i("MobieGpuSustained", sample.toString())
                        } while (SystemClock.elapsedRealtime() - sustainedStart < sustainedSeconds * 1000L)
                        result.put("samples", samples).put("measured_duration_ms", SystemClock.elapsedRealtime() - sustainedStart)
                    }
                    // Covers the warm-up and every sustained sample; an early EOS anywhere disqualifies the row.
                    result.put("all_generations_throughput_eligible", allEligible)
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

    private class MeasurementWindow(initialPss: Long, initialThermal: Int) {
        val peakPss = AtomicLong(initialPss)
        val thermalMax = AtomicInteger(initialThermal)

        fun record(pss: Long, thermal: Int) {
            peakPss.accumulateAndGet(pss, ::maxOf)
            thermalMax.accumulateAndGet(thermal, ::maxOf)
        }
    }

    /**
     * Labels whether a generation can count as native decode-throughput evidence. Nothing is
     * dropped or retried here: ineligible generations stay in the row with their reason.
     */
    private fun recordThroughputEligibility(sample: JSONObject, fixture: PromptFixture, minDecodeTokens: Int, outputTokens: Int) {
        val nativeTokens = if (sample.has("native_decode_tokens")) sample.getInt("native_decode_tokens") else null
        val rate = sample.optDouble("decode_tps", Double.NaN)
        val reason = when {
            fixture.role == ROLE_LATENCY -> "latency_probe_fixture"
            nativeTokens == null -> "native_decode_tokens_missing"
            nativeTokens < minDecodeTokens -> "native_decode_tokens_below_minimum"
            !rate.isFinite() || rate <= 0.0 -> "native_decode_rate_invalid"
            else -> null
        }
        sample.put("min_native_decode_tokens", minDecodeTokens)
            .put("short_output", nativeTokens != null && nativeTokens < minDecodeTokens)
            .put("stopped_before_output_budget", if (nativeTokens == null) JSONObject.NULL else nativeTokens < outputTokens)
            .put("throughput_eligible", reason == null)
            .put("throughput_ineligible_reason", reason ?: JSONObject.NULL)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Fixed prompt workloads. Changing any prompt text changes its recorded SHA-256 and must come
     * with a new ID; the summarizer refuses to mix prompt identities.
     */
    private enum class PromptFixture(
        val argName: String,
        val id: String,
        val role: String,
        val prompt: String,
        val defaultMinDecodeTokens: Int,
        val minDecodeTokensFloor: Int,
    ) {
        /** Original prompt and ID, unchanged. It can stop after a couple of tokens, so it only probes latency. */
        SHORT_LATENCY(
            argName = "short-latency",
            id = "local-ai-benefits-v1",
            role = ROLE_LATENCY,
            prompt = "Explain in a few concise sentences why local AI inference can be useful on a phone. /no_think",
            defaultMinDecodeTokens = 0,
            minDecodeTokensFloor = 0,
        ),

        /** A longer fixed task intended to keep native decode running well past the minimum token count. */
        SUSTAINED_DECODE(
            argName = "sustained-decode",
            id = "phone-care-twelve-tips-v1",
            role = ROLE_SUSTAINED,
            prompt = "Write a numbered list of exactly twelve practical tips for keeping a phone running smoothly. " +
                "For each tip, write two complete sentences that explain what to do and why it helps. " +
                "Do not add an introduction or a conclusion. /no_think",
            defaultMinDecodeTokens = 64,
            minDecodeTokensFloor = 64,
        ),
        ;

        companion object {
            fun fromArg(value: String?): PromptFixture =
                if (value == null) SHORT_LATENCY
                else requireNotNull(entries.firstOrNull { it.argName == value }) {
                    "Unknown promptFixture=$value; expected one of ${entries.map { it.argName }}"
                }
        }
    }

    private companion object {
        const val ROLE_LATENCY = "latency_probe"
        const val ROLE_SUSTAINED = "sustained_decode"
    }
}
