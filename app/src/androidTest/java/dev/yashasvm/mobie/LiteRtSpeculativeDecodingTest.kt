package dev.yashasvm.mobie

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.catalog.LiteRtHeaderInspector
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
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real SDK/artifact CPU plumbing probe. Emulator throughput and text equality are observations only. */
@RunWith(AndroidJUnit4::class)
class LiteRtSpeculativeDecodingTest {
    @Test
    fun downloadsGemmaAndExercisesExplicitAndAutomaticDrafterSettings(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        // Keep this before any network, fixture, or native work in the default smoke suite.
        assumeTrue(arguments.getString("litertMtpE2E") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = LiteRtLmRuntimeAdapter(context)
        val runs = JSONArray()
        val loads = JSONArray()
        val report = JSONObject()
            .put("protocol", "mobie-mtp-emulator-e2e-v1").put("status", "running")
            .put("evidence_scope", "x86_64_emulator_CPU_correctness_and_plumbing_only")
            .put("speed_claim", false).put("text_equality_required", false)
            .put("model_id", MODEL_ID).put("artifact_revision", REVISION)
            .put("artifact_file", FILE_NAME).put("artifact_bytes", SIZE_BYTES)
            .put("artifact_sha256", SHA256).put("artifact_url", URL)
            .put("artifact_speculative_drafter", true)
            .put("runtime_version", BuildConfig.LITERT_LM_VERSION)
            .put("build_revision", arguments.getString("buildRevision") ?: JSONObject.NULL)
            .put("emulator_fingerprint", Build.FINGERPRINT)
            .put("device_model", Build.MODEL).put("hardware", Build.HARDWARE)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("prompt", PROMPT).put("prompt_sha256", sha256(PROMPT.toByteArray()))
            .put("requested_backend", "CPU").put("requested_cpu_threads", 2)
            .put("requested_context_tokens", 1_024).put("output_cap", 48)
            .put("vision", false).put("thinking_enabled", false)
            .put("temperature", 0.0).put("top_k", 1).put("top_p", 1.0).put("seed", 0)
            .put("greedy_mtp_equals_non_mtp", JSONObject.NULL)
            .put("loads", loads).put("runs", runs)
        val options = ComputeOptions(
            backend = ComputeBackend.CPU,
            cpuThreads = 2,
            artifactIdentity = "$MODEL_ID/$FILE_NAME@$REVISION#$SHA256",
            contextTokens = 1_024,
            maxOutputTokens = 48,
            thinkingEnabled = false,
        )
        val sampler = SamplerSettings(temperature = 0.0, topK = 1, topP = 1.0, seed = 0)
        try {
            withTimeout(60 * 60_000L) {
                withContext(Dispatchers.IO) {
                    shellOutput("rm -f /sdcard/$EVIDENCE_FILE")
                    assertTrue("Could not clear previous MTP evidence", shellOutput("cat /sdcard/$EVIDENCE_FILE").isEmpty())
                    report.put("app_apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
                        .put("test_apk_sha256", sha256(File(instrumentation.context.applicationInfo.sourceDir)))
                }
                assertEquals("This probe requires the pinned real SDK", "0.18.0", BuildConfig.LITERT_LM_VERSION)
                assertTrue("This probe requires an x86_64 emulator", "x86_64" in Build.SUPPORTED_ABIS &&
                    (Build.HARDWARE in listOf("goldfish", "ranchu") || Build.PRODUCT.contains("sdk_gphone")))
                withContext(Dispatchers.IO) {
                    val inspector = LiteRtHeaderInspector(OkHttpClient()) { null }
                    val header = checkNotNull(inspector.inspect(URL)) { "Generic artifact header unavailable" }
                    report.put("generic_header", JSONObject().put("is_llm", header.isLlm)
                        .put("has_speculative_drafter", header.hasSpeculativeDrafter)
                        .put("accelerator_requirement", header.acceleratorRequirement ?: JSONObject.NULL))
                    assertTrue("Generic artifact is not an LLM", header.isLlm)
                    assertTrue("Generic artifact has no MTP drafter", header.hasSpeculativeDrafter)
                    assertNull("Generic artifact requires an accelerator", header.acceleratorRequirement)
                    val gpuHeader = checkNotNull(inspector.inspect(GPU_URL)) { "GPU sibling header unavailable" }
                    report.put("gpu_sibling_url", GPU_URL)
                        .put("gpu_accelerator_requirement", gpuHeader.acceleratorRequirement ?: JSONObject.NULL)
                    assertEquals("GPU-only artisan build", gpuHeader.acceleratorRequirement)
                }
                val artifact = ModelArtifact(
                    fileName = FILE_NAME,
                    downloadUrl = URL,
                    sizeBytes = SIZE_BYTES,
                    sha256 = SHA256,
                    format = ModelFormat.LITERT_LM,
                    quantization = "INT4",
                    speculativeDrafter = true,
                )
                val downloads = ModelDownloadManager(context)
                val requestId = downloads.enqueue(MODEL_ID, artifact)
                val completed = withTimeout(25 * 60_000L) {
                    downloads.observe(requestId).first { it.state.isFinished }
                }
                assertEquals("Pinned download failed: ${completed.error}", WorkInfo.State.SUCCEEDED, completed.state)
                val model = File(checkNotNull(completed.localPath))
                withContext(Dispatchers.IO) {
                    assertEquals("Artifact size mismatch", SIZE_BYTES, model.length())
                    assertEquals("Artifact checksum mismatch", SHA256, sha256(model))
                    report.put("artifact_checksum_verified", true)
                }
                runtime.configureSampler(sampler)
                runtime.configureCompute(options.copy(speculativeDecoding = false))
                load(runtime, model, "explicit_off", loads)
                assertCompute(runtime, false)
                val baseline = generate(runtime, "explicit_off", sampler, runs)
                runtime.unload()

                runtime.configureCompute(options.copy(speculativeDecoding = true))
                load(runtime, model, "explicit_on", loads)
                assertCompute(runtime, true)
                val mtp = generate(runtime, "explicit_on", sampler, runs)
                report.put("greedy_mtp_equals_non_mtp", baseline == mtp)
                runtime.unload()

                runtime.configureCompute(options) // Auto on an explicit CPU backend must leave MTP off.
                load(runtime, model, "auto_cpu", loads)
                assertCompute(runtime, false)
                report.put("auto_cpu_speculative_decoding", runtime.activeCompute?.speculativeDecoding)
                val engine = checkNotNull(nativeField(runtime, "engine"))
                val conversation = checkNotNull(nativeField(runtime, "conversation"))
                runtime.reconfigureConversation(options.copy(speculativeDecoding = true), sampler, emptyList()).getOrThrow()
                report.put("reconfigure_engine_retained", engine === nativeField(runtime, "engine"))
                    .put("reconfigure_conversation_replaced", conversation !== nativeField(runtime, "conversation"))
                assertSame("Enabling MTP reloaded model weights", engine, nativeField(runtime, "engine"))
                assertNotSame("Enabling MTP retained the old conversation", conversation, nativeField(runtime, "conversation"))
                assertCompute(runtime, true)
                val reconfigured = generate(runtime, "auto_cpu_reconfigured_on", sampler, runs)
                assertSame("Generation replaced the retained engine", engine, nativeField(runtime, "engine"))
                report.put("reconfigured_mtp_equals_non_mtp", baseline == reconfigured)
                    .put("reconfigured_mtp_equals_explicit_mtp", mtp == reconfigured)
                    .put("status", "passed")
            }
        } catch (failure: Throwable) {
            report.put("status", "failed").put("error", "${failure.javaClass.name}: ${failure.message}")
            throw failure
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    val source = File(checkNotNull(context.getExternalFilesDir(null)), EVIDENCE_FILE)
                    val temporary = File(source.parentFile, "$EVIDENCE_FILE.part")
                    temporary.writeText(report.toString(2) + "\n")
                    check(temporary.renameTo(source)) { "Could not finalize MTP evidence" }
                    // UiAutomation executes argv directly; these app-owned paths need no shell quoting.
                    check(source.absolutePath.matches(Regex("[A-Za-z0-9_./-]+")))
                    shellOutput("cp ${source.absolutePath} /sdcard/$EVIDENCE_FILE")
                    val exportedHash = shellOutput("sha256sum /sdcard/$EVIDENCE_FILE").trim().substringBefore(' ')
                    check(exportedHash == sha256(source)) { "MTP evidence export checksum mismatch" }
                }
            } finally {
                withContext(NonCancellable) { runtime.unload() }
            }
        }
    }

    private suspend fun load(runtime: LiteRtLmRuntimeAdapter, model: File, label: String, loads: JSONArray) {
        val row = JSONObject().put("condition", label).put("memory_reclaim_retry", false).put("status", "running")
        loads.put(row)
        val started = SystemClock.elapsedRealtime()
        val initial = runtime.load(model.path, vision = false)
        val failure = initial.exceptionOrNull()
        if (failure != null) {
            row.put("initial_error", "${failure.javaClass.name}: ${failure.message}")
            // Admission currently returns an IllegalStateException with this user-facing explanation.
            // Only retry memory refusals; SDK initialization failures must remain failures.
            val reason = failure.message.orEmpty()
            val memoryRefusal = failure is IllegalStateException &&
                listOf("RAM", "memory", "close background apps", "Close other apps").any { reason.contains(it) }
            if (!memoryRefusal) initial.getOrThrow()
            row.put("memory_reclaim_retry", true)
            runtime.loadAllowingMemoryReclaim(model.path, vision = false).getOrThrow()
        }
        row.put("status", "loaded").put("load_ms", SystemClock.elapsedRealtime() - started)
            .put("actual_backend", runtime.activeCompute?.backend?.name)
            .put("actual_context_tokens", runtime.activeCompute?.contextTokens)
            .put("speculative_decoding", runtime.activeCompute?.speculativeDecoding)
    }

    private fun assertCompute(runtime: LiteRtLmRuntimeAdapter, speculation: Boolean) {
        assertEquals(ComputeBackend.CPU, runtime.activeCompute?.backend)
        assertEquals(speculation, runtime.activeCompute?.speculativeDecoding)
        assertEquals(1_024, runtime.activeCompute?.contextTokens)
    }

    private suspend fun generate(
        runtime: LiteRtLmRuntimeAdapter,
        label: String,
        sampler: SamplerSettings,
        runs: JSONArray,
    ): String = withTimeout(10 * 60_000L) {
        val row = JSONObject().put("condition", label).put("status", "running")
        runs.put(row)
        val events = runtime.generate(PROMPT, config = GenerationConfig(48, sampler)).toList()
        val text = events.filterIsInstance<InferenceEvent.Token>().filterNot { it.thinking }.joinToString("") { it.text }
        val errors = events.filterIsInstance<InferenceEvent.Error>()
        val stats = events.filterIsInstance<InferenceEvent.Stats>().lastOrNull()?.value
        row.put("text", text).put("text_sha256", sha256(text.toByteArray(Charsets.UTF_8)))
            .put("errors", JSONArray(errors.map { it.message }))
            .put("completed", events.any { it is InferenceEvent.Complete })
            .put("speculative_decoding", runtime.activeCompute?.speculativeDecoding)
        stats?.let {
            row.put("decode_tokens_per_second", it.tokensPerSecond).put("decode_token_count", it.decodeTokenCount)
                .put("prefill_tokens_per_second", it.prefillTokensPerSecond).put("prefill_token_count", it.prefillTokenCount)
                .put("ttft_ms", it.timeToFirstTokenMs).put("total_generation_ms", it.totalGenerationMs)
                .put("first_visible_output_ms", it.timeToFirstVisibleOutputMs ?: JSONObject.NULL)
                .put("second_chunk_ms", it.timeToSecondChunkMs ?: JSONObject.NULL)
                .put("ram_bytes", it.ramBytes).put("ram_definition", "end_of_generation_PSS_not_peak")
                .put("actual_backend", it.compute?.backend?.name)
                .put("actual_cpu_threads", it.compute?.cpuThreads).put("actual_context_tokens", it.compute?.contextTokens)
        }
        assertTrue("$label failed: ${errors.joinToString { it.message }}", errors.isEmpty())
        assertTrue("$label produced no visible output", text.isNotBlank())
        assertTrue("$label did not complete", events.any { it is InferenceEvent.Complete })
        val measured = checkNotNull(stats) { "$label reported no decode statistics" }
        assertTrue("$label reported no decode tokens", measured.decodeTokenCount > 0)
        assertTrue("$label reported invalid decode throughput", measured.tokensPerSecond.isFinite() && measured.tokensPerSecond > 0.0)
        assertTrue("$label reported no generation duration", measured.totalGenerationMs > 0)
        row.put("status", "passed")
        text
    }

    private fun nativeField(runtime: LiteRtLmRuntimeAdapter, name: String): Any? =
        LiteRtLmRuntimeAdapter::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(runtime)

    private fun shellOutput(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

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

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val EVIDENCE_FILE = "mobie-mtp-e2e.json"
        const val PROMPT = "Reply with one short sentence explaining what a rainbow is."
        const val MODEL_ID = "litert-community/gemma-4-E2B-it-litert-lm"
        const val REVISION = "41c5569623a7c189d6eec3e5225260c58cedbce7"
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val SIZE_BYTES = 2_588_147_712L
        const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val URL = "https://huggingface.co/$MODEL_ID/resolve/$REVISION/$FILE_NAME"
        const val GPU_URL = "https://huggingface.co/$MODEL_ID/resolve/$REVISION/gemma-4-E2B-it-gpu.litertlm"
    }
}
