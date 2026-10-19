package dev.yashasvm.mobie

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.InferenceStallGuardRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.RuntimeAdapter
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt in with -e litertReconfigureBenchmark true on a debug x86 emulator. Optional modelPath is
 * relative to filesDir; otherwise reuse the checksum-pinned E2E fixture's completed download.
 *
 * Every trial is preconditioned, outside timing, to seed 0 and the same restored history. Apply
 * changes only the seed to 7, retaining greedy sampling and a fixed output cap. The baseline
 * configures settings then loads over the existing engine, exactly as the old Apply path did;
 * it does not add an explicit unload. The candidate replaces only the native conversation.
 *
 * One warmup per condition precedes four alternating-order pairs. These are warm-cache API-path
 * measurements, not tap-to-pixel latency, cold startup, physical-phone or Gallery comparisons.
 * Only correctness and metric validity are asserted, never a timing or speedup threshold.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtConversationReconfigureBenchmarkTest {
    @Test
    fun comparesReloadWithRetainedWeightsOnEmulator(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("litertReconfigureBenchmark") == "true")
        assertTrue("Identity checks require the debug APK", BuildConfig.DEBUG)
        assertTrue(
            "This probe is scoped to x86 emulator mechanism evidence, not phone performance",
            Build.SUPPORTED_ABIS.any { it == "x86" || it == "x86_64" } &&
                (Build.HARDWARE in listOf("goldfish", "ranchu") ||
                    Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.contains("sdk_gphone")),
        )
        withTimeout(30 * 60_000L) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val evidenceDir = File(checkNotNull(context.getExternalFilesDir(null)), "benchmarks")
            // An early fixture/hash failure must not leave an earlier successful run as evidence.
            withContext(Dispatchers.IO) {
                listOf("json", "csv", "json.part", "csv.part").forEach { extension ->
                    val previous = File(evidenceDir, "$BASENAME.$extension")
                    check(!previous.exists() || previous.delete()) { "Cannot clear prior benchmark evidence" }
                }
            }
            val model = verifiedFixture(context, arguments.getString("modelPath"))
            val report = withContext(Dispatchers.IO) {
                JSONObject()
                    .put("protocol", "mobie-conversation-reconfigure-emulator-v1")
                    .put("status", "running")
                    .put("paired_comparison_valid", false)
                    .put("evidence_scope", "x86_emulator_correctness_and_api_path_mechanism_only")
                    .put("phone_or_gallery_speed_claim", false)
                    .put("ui_latency_measured", false)
                    .put("setup_plus_ttft_definition", "setup_ms + runtime_first_nonempty_sdk_chunk_ms; not tap-to-pixel")
                    .put("preconditioning", "untimed reconfigure to seed 0 and identical restored history before every trial")
                    .put("baseline", "configureCompute + configureSampler + load on existing engine; no extra unload")
                    .put("candidate", "reconfigureConversation with unchanged engine-level settings")
                    .put("adapter", "LiteRtLmRuntimeAdapter wrapped in InferenceStallGuardRuntimeAdapter")
                    .put("engine_identity_definition", "same-process Java Engine reference identity, not a native pointer")
                    .put("warmups_per_condition", 1)
                    .put("measured_pairs", PAIRS)
                    .put("order", "reload/reconfigure, reconfigure/reload, reload/reconfigure, reconfigure/reload")
                    .put("os_page_cache_controlled", false)
                    .put("cache_notes", "fixture hash read and both warmups precede measurement; persistent cache retained")
                    .put("model_id", MODEL_ID).put("model_revision", REVISION)
                    .put("model_file", FILE_NAME).put("model_bytes", model.length())
                    .put("model_sha256", SHA256).put("quantization", "INT4")
                    .put("app_apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
                    .put("test_apk_sha256", sha256(File(instrumentation.context.applicationInfo.sourceDir)))
                    .put("split_apks", JSONArray().apply {
                        context.applicationInfo.splitSourceDirs.orEmpty().forEach { path ->
                            put(JSONObject().put("name", File(path).name).put("sha256", sha256(File(path))))
                        }
                    })
                    .put("runtime_version", BuildConfig.LITERT_LM_VERSION)
                    .put("application_id", BuildConfig.APPLICATION_ID)
                    .put("app_version_name", BuildConfig.VERSION_NAME)
                    .put("app_version_code", BuildConfig.VERSION_CODE)
                    .put("build_type", BuildConfig.BUILD_TYPE).put("debuggable_build", BuildConfig.DEBUG)
                    .put("build_revision", arguments.getString("buildRevision") ?: JSONObject.NULL)
                    .put("device_fingerprint", Build.FINGERPRINT)
                    .put("device_model", Build.MODEL).put("android_sdk", Build.VERSION.SDK_INT)
                    .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                    .put("requested_backend", "CPU").put("requested_cpu_threads", 1)
                    .put("requested_context_tokens", CONTEXT_TOKENS)
                    .put("output_cap", OUTPUT_CAP)
                    .put("temperature", 0.0).put("top_k", 1).put("top_p", 1.0)
                    .put("prior_seed", 0).put("target_seed", 7)
                    .put("thinking_enabled", false).put("activation_type", "AUTO")
                    .put("speculative_decoding_request", JSONObject.NULL)
                    .put("prefill_preface_on_init", false)
                    .put("prompt", PROMPT).put("prompt_sha256", sha256(PROMPT))
                    .put("output_hash_definition", "trimmed visible output, UTF-8 SHA-256")
                    .put("history", historyJson()).put("history_sha256", sha256(historyJson().toString()))
                    .put("samples", JSONArray())
            }
            val native = LiteRtLmRuntimeAdapter(context)
            val runtime: RuntimeAdapter = InferenceStallGuardRuntimeAdapter(native)
            val options = ComputeOptions(
                backend = ComputeBackend.CPU,
                cpuThreads = 1,
                artifactIdentity = "$MODEL_ID/$FILE_NAME@$REVISION#$SHA256",
                contextTokens = CONTEXT_TOKENS,
                maxOutputTokens = OUTPUT_CAP,
                thinkingEnabled = false,
            )
            var primaryFailure: Throwable? = null
            try {
                writeEvidence(evidenceDir, report)
                runtime.configureCompute(options)
                runtime.configureSampler(PRIOR_SAMPLER)
                withTimeout(5 * 60_000L) { runtime.load(model.path, history = HISTORY).getOrThrow() }
                for ((position, condition) in Condition.entries.withIndex()) {
                    runTrial(runtime, native, model, options, condition, "warmup", -1, position, report, evidenceDir)
                }
                var equivalentPairs = true
                repeat(PAIRS) { pair ->
                    val order = if (pair % 2 == 0) Condition.entries.toList() else Condition.entries.reversed()
                    for ((position, condition) in order.withIndex()) {
                        runTrial(runtime, native, model, options, condition, "measured", pair, position, report, evidenceDir)
                    }
                    val samples = report.getJSONArray("samples")
                    val first = samples.getJSONObject(samples.length() - 2)
                    val second = samples.getJSONObject(samples.length() - 1)
                    val sameOutput = first.getString("output_sha256") == second.getString("output_sha256")
                    val sameCount = first.getInt("native_decode_token_count") == second.getInt("native_decode_token_count")
                    for (row in listOf(first, second)) {
                        row.put("paired_output_sha256_match", sameOutput).put("paired_decode_token_count_match", sameCount)
                    }
                    equivalentPairs = equivalentPairs && sameOutput && sameCount
                }
                report.put("paired_comparison_valid", equivalentPairs)
                if (!equivalentPairs) report.put("comparison_warning", "Outputs or decode counts differ within a pair; do not treat this run as a matched-work performance comparison")
                report.put("status", "complete")
                assertEquals("Missing benchmark trials", 2 + PAIRS * 2, report.getJSONArray("samples").length())
            } catch (error: Throwable) {
                primaryFailure = error
                report.put("status", "failed").put("error", error.toString())
                throw error
            } finally {
                withContext(NonCancellable) {
                    try {
                        runtime.unload()
                        report.put("cleanup_status", "complete")
                    } catch (error: Throwable) {
                        report.put("status", "failed").put("cleanup_error", error.toString())
                        val failure = primaryFailure
                        if (failure != null) failure.addSuppressed(error) else throw error
                    } finally {
                        writeEvidence(evidenceDir, report)
                        Log.i(TAG, "Evidence: ${File(evidenceDir, "$BASENAME.json").absolutePath}; status=${report.getString("status")}")
                    }
                }
            }
        }
    }

    private suspend fun runTrial(
        runtime: RuntimeAdapter,
        native: LiteRtLmRuntimeAdapter,
        model: File,
        options: ComputeOptions,
        condition: Condition,
        phase: String,
        pair: Int,
        position: Int,
        report: JSONObject,
        evidenceDir: File,
    ) {
        val row = JSONObject().put("phase", phase).put("pair", pair).put("position", position)
            .put("condition", condition.name).put("status", "running")
        report.getJSONArray("samples").put(row)
        writeEvidence(evidenceDir, report)
        try {
            withTimeout(5 * 60_000L) {
                // Equalize prior state, including after the previous trial's generated turn.
                runtime.reconfigureConversation(options, PRIOR_SAMPLER, HISTORY).getOrThrow()
                assertEquals(HISTORY, nativeField(native, "committedHistory"))
                assertEquals(PRIOR_SAMPLER, nativeField(native, "activeSampler"))
                val before = checkNotNull(nativeField(native, "engine"))
                val setupStart = SystemClock.elapsedRealtimeNanos()
                when (condition) {
                    Condition.RELOAD -> {
                        runtime.configureCompute(options)
                        runtime.configureSampler(TARGET_SAMPLER)
                        runtime.load(model.path, history = HISTORY).getOrThrow()
                    }
                    Condition.RECONFIGURE -> runtime.reconfigureConversation(options, TARGET_SAMPLER, HISTORY).getOrThrow()
                }
                val setupMs = (SystemClock.elapsedRealtimeNanos() - setupStart) / 1_000_000.0
                val after = checkNotNull(nativeField(native, "engine"))
                row.put("setup_ms", setupMs).put("engine_retained", before === after)
                    .put("engine_before_identity", System.identityHashCode(before))
                    .put("engine_after_identity", System.identityHashCode(after))
                if (condition == Condition.RECONFIGURE) assertSame("Candidate replaced the engine", before, after)
                else assertNotSame("Baseline did not reload the engine", before, after)
                assertEquals(HISTORY, nativeField(native, "committedHistory"))
                assertEquals(TARGET_SAMPLER, nativeField(native, "activeSampler"))
                val events = runtime.generate(PROMPT, config = GenerationConfig(OUTPUT_CAP, TARGET_SAMPLER)).toList()
                val errors = events.filterIsInstance<InferenceEvent.Error>()
                assertTrue("Generation failed: ${errors.joinToString { it.message }}", errors.isEmpty())
                val output = events.filterIsInstance<InferenceEvent.Token>().filterNot { it.thinking }
                    .joinToString("") { it.text }.trim()
                assertTrue("Generation produced no visible answer", output.isNotBlank())
                assertTrue("Generation did not complete", events.any { it is InferenceEvent.Complete })
                val stats = checkNotNull(events.filterIsInstance<InferenceEvent.Stats>().lastOrNull()) {
                    "Generation reported no native statistics"
                }.value
                val compute = checkNotNull(stats.compute)
                row.put("runtime_ttft_ms", stats.timeToFirstTokenMs)
                    .put("setup_plus_runtime_ttft_ms", setupMs + stats.timeToFirstTokenMs)
                    .put("runtime_first_visible_ms", stats.timeToFirstVisibleOutputMs ?: JSONObject.NULL)
                    .put("runtime_generation_ms", stats.totalGenerationMs)
                    .put("native_decode_tokens_per_second", stats.tokensPerSecond)
                    .put("native_decode_token_count", stats.decodeTokenCount)
                    .put("native_prefill_tokens_per_second", stats.prefillTokensPerSecond)
                    .put("native_prefill_token_count", stats.prefillTokenCount)
                    .put("actual_backend", compute.backend.name).put("actual_cpu_threads", compute.cpuThreads)
                    .put("actual_context_tokens", compute.contextTokens)
                    .put("output_characters", output.length).put("output_sha256", sha256(output))
                assertEquals(ComputeBackend.CPU, compute.backend)
                assertEquals(1, compute.cpuThreads)
                assertEquals(CONTEXT_TOKENS, compute.contextTokens)
                assertTrue("Invalid setup duration", setupMs.isFinite() && setupMs >= 0.0)
                assertTrue("Invalid runtime TTFT", stats.timeToFirstTokenMs >= 0)
                assertTrue("Invalid runtime generation duration", stats.totalGenerationMs >= stats.timeToFirstTokenMs)
                assertTrue("Invalid native decode count", stats.decodeTokenCount > 0)
                assertTrue("Invalid native decode rate", stats.tokensPerSecond.isFinite() && stats.tokensPerSecond > 0.0)
                assertTrue("Invalid native prefill count", stats.prefillTokenCount > 0)
                assertTrue("Invalid native prefill rate", stats.prefillTokensPerSecond.isFinite() && stats.prefillTokensPerSecond > 0.0)
                assertSame("Generation unexpectedly replaced the engine", after, nativeField(native, "engine"))
                row.put("status", "success")
            }
        } catch (error: Throwable) {
            row.put("status", "failed").put("error", error.toString())
            throw error
        } finally {
            withContext(NonCancellable) { writeEvidence(evidenceDir, report) }
        }
    }

    private suspend fun verifiedFixture(context: Context, stagedPath: String?): File {
        val artifact = ModelArtifact(
            fileName = FILE_NAME,
            downloadUrl = "https://huggingface.co/$MODEL_ID/resolve/$REVISION/$FILE_NAME",
            sizeBytes = SIZE_BYTES,
            sha256 = SHA256,
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
        )
        val model = if (stagedPath != null) {
            File(context.filesDir, stagedPath).canonicalFile.also {
                require(it.path.startsWith(context.filesDir.canonicalPath + "/") && it.isFile) {
                    "modelPath must identify an existing file inside the app's private files directory"
                }
            }
        } else {
            val downloads = ModelDownloadManager(context)
            downloads.completedFile(MODEL_ID, artifact) ?: run {
                val requestId = downloads.enqueue(MODEL_ID, artifact)
                val completed = withTimeout(20 * 60_000L) { downloads.observe(requestId).first { it.state.isFinished } }
                assertEquals("Pinned fixture download failed: ${completed.error}", WorkInfo.State.SUCCEEDED, completed.state)
                File(checkNotNull(completed.localPath))
            }
        }
        withContext(Dispatchers.IO) {
            assertEquals("Unexpected fixture size", SIZE_BYTES, model.length())
            assertEquals("Unexpected fixture SHA-256", SHA256, sha256(model))
        }
        return model
    }

    /** Preserve failures/divergence; comparison consumers require complete status and paired_comparison_valid. */
    private suspend fun writeEvidence(directory: File, report: JSONObject) = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create benchmark evidence directory" }
        fun replace(extension: String, content: String) {
            val target = File(directory, "$BASENAME.$extension")
            val temporary = File(directory, "$BASENAME.$extension.part")
            temporary.writeText(content)
            check(temporary.renameTo(target)) { "Cannot finalize benchmark evidence: ${target.name}" }
        }
        replace("json", report.toString(2) + "\n")
        fun cell(value: Any?): String = "\"${if (value == null || value == JSONObject.NULL) "" else value.toString().replace("\"", "\"\"")}\""
        val samples = report.getJSONArray("samples")
        replace("csv", buildString {
            appendLine(CSV_COLUMNS.joinToString(",", transform = ::cell))
            for (index in 0 until samples.length()) {
                val row = samples.getJSONObject(index)
                appendLine(CSV_COLUMNS.joinToString(",") { name ->
                    cell(when {
                        name == "report_status" -> report.opt("status")
                        row.has(name) -> row.opt(name)
                        else -> report.opt(name)
                    })
                })
            }
        })
    }

    private fun nativeField(runtime: LiteRtLmRuntimeAdapter, name: String): Any? =
        LiteRtLmRuntimeAdapter::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(runtime)

    private fun historyJson(): JSONArray = JSONArray().apply {
        HISTORY.forEach { put(JSONObject().put("from_user", it.fromUser).put("text", it.text)) }
    }

    private fun sha256(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1_024 * 1_024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private enum class Condition { RELOAD, RECONFIGURE }

    private companion object {
        const val TAG = "MobieReconfigureBench"
        const val BASENAME = "mobie-conversation-reconfigure-benchmark"
        const val PAIRS = 4
        const val CONTEXT_TOKENS = 1_280
        const val OUTPUT_CAP = 24
        const val PROMPT = "Reply with one word: ready."
        const val MODEL_ID = "litert-community/Qwen3-0.6B-int4"
        const val REVISION = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7"
        const val FILE_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        const val SIZE_BYTES = 347_251_840L
        const val SHA256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"
        val PRIOR_SAMPLER = SamplerSettings(temperature = 0.0, topK = 1, topP = 1.0, seed = 0)
        val TARGET_SAMPLER = PRIOR_SAMPLER.copy(seed = 7)
        val HISTORY = listOf(RuntimeMessage(true, "Reply with one short greeting."), RuntimeMessage(false, "Hello."))
        val CSV_COLUMNS = listOf(
            "phase", "pair", "position", "condition", "status", "report_status", "error", "setup_ms", "runtime_ttft_ms",
            "setup_plus_runtime_ttft_ms", "runtime_first_visible_ms", "runtime_generation_ms",
            "native_decode_tokens_per_second", "native_decode_token_count", "native_prefill_tokens_per_second",
            "native_prefill_token_count", "actual_backend", "actual_cpu_threads", "actual_context_tokens",
            "engine_retained", "engine_before_identity", "engine_after_identity", "output_characters", "output_sha256",
            "paired_output_sha256_match", "paired_decode_token_count_match", "paired_comparison_valid",
            "model_sha256", "app_apk_sha256", "test_apk_sha256", "runtime_version", "build_revision", "build_type",
            "prompt_sha256", "history_sha256", "device_fingerprint", "evidence_scope", "ui_latency_measured",
            "requested_backend", "requested_cpu_threads", "requested_context_tokens", "output_cap",
            "prior_seed", "target_seed", "temperature", "top_k", "top_p",
        )
    }
}
