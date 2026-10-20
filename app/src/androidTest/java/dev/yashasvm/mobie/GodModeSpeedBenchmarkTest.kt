package dev.yashasvm.mobie

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtChannelText
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.LiteRtMessageText
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-phone comparison, never a performance claim without the exported exact-artifact run. */
@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class GodModeSpeedBenchmarkTest {
    @Test
    fun comparesGalleryConfigurationOnPhysicalArm64(): Unit = runBlocking(Dispatchers.Default) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("godModeBenchmark") == "true")
        assertTrue(
            "A physical ARM64 phone is required",
            "arm64-v8a" in Build.SUPPORTED_ABIS &&
                !Build.FINGERPRINT.startsWith("generic") &&
                Build.HARDWARE !in listOf("goldfish", "ranchu") &&
                !Build.PRODUCT.contains("sdk_gphone"),
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val relativePath = checkNotNull(args.getString("modelPath"))
        require(!File(relativePath).isAbsolute) { "modelPath must be relative to filesDir" }
        val model = File(context.filesDir, relativePath).canonicalFile
        require(model.path.startsWith(context.filesDir.canonicalPath + "/") && model.isFile)
        fun integer(name: String, default: Int, range: IntRange): Int =
            (args.getString(name)?.toInt() ?: default).also { require(it in range) { "Invalid $name" } }
        val repetitions = integer("repetitions", 3, 1..20)
        val rounds = integer("rounds", 2, 2..20)
        require(rounds % 2 == 0) { "Use even rounds for balanced forward/reverse order" }
        val outputTokens = integer("outputTokens", 256, 1..1024)
        val withVision = args.getString("withVision") == "true"
        val coolStart = args.getString("coolStart") == "true"
        require(BuildConfig.LITERT_LM_VERSION == "0.18.0") { "Revalidate Gallery equivalence for a new SDK" }

        // Hashes are outside timed regions. Reading the model warms the OS page cache.
        val modelSha = sha256(model)
        require(model.length() == MODEL_BYTES && modelSha == MODEL_SHA) { "Expected pinned Gemma 4 E2B artifact" }
        val identity = JSONObject()
            .put("artifact_sha256", modelSha).put("artifact_bytes", model.length())
            .put("artifact_revision", "41c5569623a7c189d6eec3e5225260c58cedbce7")
            .put("model", model.name).put("quantization", "unknown")
            .put("app_apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
            .put("test_apk_sha256", sha256(File(instrumentation.context.applicationInfo.sourceDir)))
            .put("split_apks", JSONArray().apply {
                context.applicationInfo.splitSourceDirs.orEmpty().forEach {
                    put(JSONObject().put("name", File(it).name).put("sha256", sha256(File(it))))
                }
            })
            .put("device_fingerprint", Build.FINGERPRINT).put("device", Build.MODEL)
            .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("runtime_version", BuildConfig.LITERT_LM_VERSION)
            .put("build_revision", args.getString("buildRevision") ?: JSONObject.NULL)
            .put("debuggable_build", BuildConfig.DEBUG)
            .put("benchmark_protocol", "mobie-gallery-phone-v1")
            .put("os_page_cache_controlled", false).put("persistent_cache_state", "uncontrolled")
            .put("benchmark_enabled", true).put("with_vision", withVision)
            .put("output_budget", outputTokens).put("thinking_enabled", false)
        val samples = JSONArray()
        val report = JSONObject(identity.toString()).put("status", "running")
            .put("rounds", rounds).put("repetitions", repetitions).put("cool_start", coolStart)
            .put("prompts", JSONArray().apply {
                PROMPTS.forEach { (id, text) ->
                    put(JSONObject().put("id", id).put("text", text).put("sha256", sha256(text.toByteArray(Charsets.UTF_8))))
                }
            }).put("samples", samples)
        val jsonl = File(context.filesDir, "$BASENAME.jsonl").apply { writeText("") }
        fun record(row: JSONObject) {
            identity.keys().forEach { row.put(it, identity.get(it)) }
            samples.put(row)
            jsonl.appendText(row.toString() + "\n")
            android.util.Log.i("MobieGodModeBenchmark", row.toString())
        }
        val previousBenchmark = ExperimentalFlags.enableBenchmark
        val previousSpeculation = ExperimentalFlags.enableSpeculativeDecoding
        try {
            shellOutput("rm -f /sdcard/$BASENAME.json /sdcard/$BASENAME.csv")
            repeat(rounds) { round ->
                // Projection onto any pair is ABBA across two rounds; each block loads a new engine.
                val order = if (round % 2 == 0) CONDITIONS else CONDITIONS.reversed()
                order.forEachIndexed { position, condition ->
                    measureBlock(context, model, condition, round, position, repetitions, outputTokens, withVision, coolStart, ::record)
                }
            }
            report.put("status", if ((0 until samples.length()).any {
                samples.getJSONObject(it).optString("status") != "complete"
            }) "failed" else "complete")
        } catch (failure: Exception) {
            report.put("status", "failed").put("error", failure.toString())
            throw failure
        } finally {
            ExperimentalFlags.enableBenchmark = previousBenchmark
            ExperimentalFlags.enableSpeculativeDecoding = previousSpeculation
            withContext(NonCancellable + Dispatchers.IO) { writeEvidence(context, report) }
        }
        assertTrue(report.toString(), report.getString("status") == "complete")
        assertTrue("Every block must produce a warmup and all prompt repetitions", samples.length() == rounds * CONDITIONS.size * (1 + repetitions * PROMPTS.size))
    }

    private suspend fun measureBlock(
        context: Context, model: File, condition: String, round: Int, position: Int,
        repetitions: Int, outputTokens: Int, withVision: Boolean, coolStart: Boolean,
        record: (JSONObject) -> Unit,
    ) = coroutineScope {
        val power = context.getSystemService(PowerManager::class.java)
        fun thermal(): Int = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1
        val coolStarted = SystemClock.elapsedRealtime()
        if (coolStart) {
            require(Build.VERSION.SDK_INT >= 29) { "coolStart needs Android 10 thermal reporting" }
            while (thermal() != PowerManager.THERMAL_STATUS_NONE && SystemClock.elapsedRealtime() - coolStarted < 180_000) delay(1000)
        }
        val coolTimedOut = coolStart && thermal() != PowerManager.THERMAL_STATUS_NONE
        val base = JSONObject().put("condition", condition).put("round", round).put("block_position", position)
            .put("cool_wait_ms", SystemClock.elapsedRealtime() - coolStarted).put("cool_start_timed_out", coolTimedOut)
        val blockWindow = Window(Debug.getPss() * 1024L, thermal())
        val activeWindow = AtomicReference<Window?>(null)
        // A single page-map walk feeds block-wide and generation peaks, with endpoint reads below.
        val monitor = launch(Dispatchers.IO) {
            while (isActive) {
                val pss = Debug.getPss() * 1024L
                val status = thermal()
                blockWindow.record(pss, status)
                activeWindow.get()?.record(pss, status)
                delay(100)
            }
        }
        val raw = condition.startsWith("gallery_")
        val sampler = if (condition == "mobie_default") SamplerSettings() else GALLERY_SAMPLER
        base.put("temperature", sampler.temperature).put("top_k", sampler.topK).put("top_p", sampler.topP).put("seed", sampler.seed)
        val adapter = if (raw) null else LiteRtLmRuntimeAdapter(context)
        var engine: Engine? = null
        var conversation: Conversation? = null
        var rawGenerationInFlight = false
        var currentPrompt = "__load__"
        var currentWarmup = true
        var currentRepetition = -1
        try {
            ExperimentalFlags.enableBenchmark = true
            val started = SystemClock.elapsedRealtime()
            withTimeout(5 * 60_000L) {
                if (raw) {
                    val speculation = condition == "gallery_mtp"
                    // Gallery only enables audioBackend=CPU for audio input. These are text workloads.
                    engine = Engine(EngineConfig(
                        modelPath = model.path, backend = Backend.GPU(),
                        visionBackend = if (withVision) Backend.GPU() else null,
                        audioBackend = null, maxNumTokens = 4000, cacheDir = null,
                    ))
                    ExperimentalFlags.enableSpeculativeDecoding = speculation
                    try { checkNotNull(engine).initialize() } finally { ExperimentalFlags.enableSpeculativeDecoding = null }
                    conversation = checkNotNull(engine).createConversation(galleryConversation())
                    base.put("active_backend", "GPU").put("drafter_active", speculation)
                        .put("drafter_reporting", "requested_engine_flag").put("context_tokens", 4000)
                        .put("vision_backend", if (withVision) "GPU" else JSONObject.NULL)
                        .put("cache_dir_policy", "sdk_default_null")
                } else {
                    val runtime = checkNotNull(adapter)
                    // Keep production Auto/backend/context/speculation defaults, with the shared cap and thinking off.
                    runtime.configureCompute(ComputeOptions(maxOutputTokens = outputTokens, thinkingEnabled = false))
                    runtime.configureSampler(sampler)
                    runtime.configureThinking(false)
                    runtime.load(model.path, vision = withVision).getOrThrow()
                    val active = checkNotNull(runtime.activeCompute)
                    // Test-only inspection, as in the reconfigure benchmark, records the encoder
                    // actually selected after production fallback without changing the runtime API.
                    val nativeEngine = LiteRtLmRuntimeAdapter::class.java.getDeclaredField("engine")
                        .also { it.isAccessible = true }.get(runtime) as Engine
                    val visionBackend = nativeEngine.engineConfig.visionBackend
                    base.put("active_backend", active.backend.name).put("drafter_active", active.speculativeDecoding)
                        .put("drafter_reporting", "adapter_active_compute").put("context_tokens", active.contextTokens)
                        .put("active_cpu_threads", active.cpuThreads).put("fallback_reason", active.fallbackReason ?: JSONObject.NULL)
                        .put("vision_ready", runtime.supportsImageInput).put("cache_dir_policy", "production_app_private")
                        .put("vision_backend", when (visionBackend) {
                            is Backend.GPU -> "GPU"
                            is Backend.CPU -> "CPU"
                            null -> JSONObject.NULL
                            else -> visionBackend.toString()
                        })
                    check(!withVision || visionBackend is Backend.GPU) { "Matched GPU vision comparison unavailable: production selected $visionBackend" }
                }
            }
            base.put("load_ms", SystemClock.elapsedRealtime() - started)
                .put("load_peak_pss_bytes", blockWindow.peakPss.get())
                .put("load_thermal_max", blockWindow.thermalMax.get())
            val tasks = listOf(Triple(PROMPTS.first(), true, -1)) +
                (0 until repetitions).flatMap { repetition -> PROMPTS.map { Triple(it, false, repetition) } }
            tasks.forEachIndexed { index, (prompt, warmup, repetition) ->
                currentPrompt = prompt.first
                currentWarmup = warmup
                currentRepetition = repetition
                if (index > 0) withTimeout(60_000L) {
                    if (raw) {
                        conversation?.close()
                        conversation = null
                        conversation = checkNotNull(engine).createConversation(galleryConversation())
                    } else checkNotNull(adapter).resetConversation().getOrThrow()
                }
                val row = JSONObject(base.toString()).put("prompt_id", prompt.first).put("prompt_category", prompt.first)
                    .put("prompt_sha256", sha256(prompt.second.toByteArray(Charsets.UTF_8)))
                    .put("warmup", warmup).put("repetition", repetition)
                val window = Window(Debug.getPss() * 1024L, thermal())
                val visible = StringBuilder()
                row.put("thermal_start", thermal())
                activeWindow.set(window)
                val generationStart = SystemClock.elapsedRealtime()
                try {
                    withTimeout(5 * 60_000L) {
                        if (raw) {
                            val active = checkNotNull(conversation)
                            rawGenerationInFlight = true
                            active.sendMessageAsync(prompt.second, extraContext = mapOf("enable_thinking" to false), maxOutputToken = outputTokens).collect { chunk ->
                                val split = LiteRtChannelText.split(chunk.channels, REASONING_CHANNELS)
                                val answer = LiteRtMessageText.visibleAnswer(chunk, split)
                                if (!row.has("first_chunk_ms") && (answer.isNotEmpty() || split.reasoning.isNotEmpty())) {
                                    row.put("first_chunk_ms", SystemClock.elapsedRealtime() - generationStart)
                                }
                                visible.append(answer)
                            }
                            rawGenerationInFlight = false
                            val finished = SystemClock.elapsedRealtime()
                            val stats = active.getBenchmarkInfo()
                            row.put("decode_tps", stats.lastDecodeTokensPerSecond).put("prefill_tps", stats.lastPrefillTokensPerSecond)
                                .put("decode_tokens", stats.lastDecodeTokenCount).put("prefill_tokens", stats.lastPrefillTokenCount)
                                .put("generation_ms", finished - generationStart)
                        } else {
                            checkNotNull(adapter).generate(prompt.second, config = GenerationConfig(outputTokens, sampler)).collect { event ->
                                when (event) {
                                    is InferenceEvent.Token -> if (!event.thinking) visible.append(event.text)
                                    is InferenceEvent.Error -> error(event.message)
                                    is InferenceEvent.Stats -> row.put("decode_tps", event.value.tokensPerSecond)
                                        .put("prefill_tps", event.value.prefillTokensPerSecond)
                                        .put("decode_tokens", event.value.decodeTokenCount).put("prefill_tokens", event.value.prefillTokenCount)
                                        .put("first_chunk_ms", event.value.timeToFirstTokenMs).put("generation_ms", event.value.totalGenerationMs)
                                    else -> Unit
                                }
                            }
                        }
                    }
                    check(visible.isNotBlank() && row.has("decode_tokens")) { "Generation did not complete with visible output and native statistics" }
                    val count = row.getInt("decode_tokens")
                    row.put("ms_per_output_token", if (count > 0) row.getDouble("generation_ms") / count else JSONObject.NULL)
                    row.put("status", "complete")
                } catch (failure: Exception) {
                    row.put("status", "failed").put("error", failure.toString())
                    throw failure
                } finally {
                    window.record(Debug.getPss() * 1024L, thermal())
                    activeWindow.set(null)
                    row.put("thermal_max", window.thermalMax.get()).put("thermal_end", thermal())
                        .put("peak_pss_bytes", window.peakPss.get())
                        .put("visible_sha256", sha256(visible.toString().toByteArray(Charsets.UTF_8)))
                        .put("visible_length", visible.length).put("visible_utf8_bytes", visible.toString().toByteArray(Charsets.UTF_8).size)
                    record(row)
                }
            }
        } catch (failure: Exception) {
            // Include load/reset failures too; the summary must never silently select surviving rows.
            record(JSONObject(base.toString()).put("status", "failed").put("error", failure.toString())
                .put("prompt_id", currentPrompt).put("warmup", currentWarmup).put("repetition", currentRepetition))
        } finally {
            withContext(NonCancellable) {
                try {
                    try {
                        // SDK Flow cancellation does not cancel native generation in 0.18.0.
                        try {
                            if (rawGenerationInFlight) conversation?.cancelProcess()
                        } finally { conversation?.close() }
                    } finally {
                        try { engine?.close() } finally { adapter?.unload() }
                    }
                } catch (failure: Exception) {
                    record(JSONObject(base.toString()).put("status", "failed").put("error", "Cleanup: $failure"))
                } finally {
                    monitor.cancelAndJoin()
                }
            }
        }
    }

    private fun galleryConversation() = ConversationConfig(
        samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
    )

    private fun writeEvidence(context: Context, report: JSONObject) {
        // Shell cannot read app-private files. Stage in external-files, then export before teardown.
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "benchmarks")
        check(directory.isDirectory || directory.mkdirs())
        val samples = report.getJSONArray("samples")
        val columns = linkedSetOf<String>()
        for (i in 0 until samples.length()) samples.getJSONObject(i).keys().forEach { columns.add(it) }
        fun cell(value: Any?): String = "\"${if (value == null || value == JSONObject.NULL) "" else value.toString().replace("\"", "\"\"")}\""
        val csv = buildString {
            appendLine((listOf("report_status") + columns).joinToString(",", transform = ::cell))
            for (i in 0 until samples.length()) {
                val row = samples.getJSONObject(i)
                appendLine((listOf(report.opt("status")) + columns.map { row.opt(it) }).joinToString(",", transform = ::cell))
            }
        }
        mapOf("json" to report.toString(2) + "\n", "csv" to csv).forEach { (extension, content) ->
            val target = File(directory, "$BASENAME.$extension")
            val temporary = File(directory, "$BASENAME.$extension.part")
            temporary.writeText(content)
            check(temporary.renameTo(target)) { "Cannot finalize evidence" }
            check(target.absolutePath.matches(Regex("[A-Za-z0-9_./-]+")))
            val destination = "/sdcard/$BASENAME.$extension"
            shellOutput("cp ${target.absolutePath} $destination")
            check(shellOutput("sha256sum $destination").trim().substringBefore(' ') == sha256(target)) { "Evidence export checksum mismatch" }
        }
    }

    private fun shellOutput(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private class Window(initialPss: Long, initialThermal: Int) {
        val peakPss = AtomicLong(initialPss)
        val thermalMax = AtomicInteger(initialThermal)
        fun record(pss: Long, thermal: Int) {
            peakPss.accumulateAndGet(pss, ::maxOf)
            thermalMax.accumulateAndGet(thermal, ::maxOf)
        }
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

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val BASENAME = "mobie-god-mode-benchmark"
        const val MODEL_BYTES = 2588147712L
        const val MODEL_SHA = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        val CONDITIONS = listOf("gallery_default", "gallery_mtp", "mobie_default", "mobie_gallery_sampler")
        val GALLERY_SAMPLER = SamplerSettings(topK = 64, topP = 0.95, temperature = 1.0)
        val REASONING_CHANNELS = setOf(
            "analysis", "thinking", "reasoning", "reasoning_content", "thought", "thoughts",
            "deliberation", "scratchpad", "chain_of_thought", "chain-of-thought", "cot",
        )
        // UTF-8, no trailing newline. Tasks mirror model-card categories, not Google's benchmark results.
        val PROMPTS = listOf(
            "summarize_text" to "Summarize this text in three sentences: A neighborhood library started lending tools as well as books. Residents can borrow drills, ladders, and gardening equipment for one week. Volunteers inspect each returned item and teach a monthly repair workshop. The program reduces duplicate purchases and helps neighbors finish small projects. Its main challenges are storage space, maintenance costs, and making popular tools available fairly.",
            "code_snippet" to "Write a short Kotlin function that takes a list of integers and returns the three most frequent values. Break ties by ascending numeric value. Include a small example and explain the time complexity.",
            "rewrite_tone" to "Rewrite this message in a friendly, professional tone while preserving every request: Your report is late again. Send the corrected sales totals by Thursday at noon, explain why the numbers changed, and tell me who will review the next draft.",
            "free_form" to "Design a small balcony garden for someone with two hours a week, morning sunlight, and a limited budget. Suggest plants, a simple watering routine, and one way to reuse household materials. Explain your choices.",
        )
    }
}
