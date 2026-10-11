package dev.yashasvm.mobie

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalApi::class)
class LiteRtCpuThreadBenchmarkTest {
    private companion object {
        const val PROMPT = "Explain in a few concise sentences why local AI inference can be useful on a phone."
        const val MAX_OUTPUT_TOKENS = 64
        const val CONTEXT_TOKENS = 1_280
        val THREAD_COUNTS = listOf(1, 2, 4)
    }

    @Test
    fun comparesRepeatedCpuThreadConfigurationsOnPhysicalArm64(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertPhysicalBenchmark") == "true")
        val fingerprint = Build.FINGERPRINT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        assertTrue(
            "This benchmark requires a physical ARM64 phone; emulator measurements are not phone evidence.",
            Build.SUPPORTED_ABIS.contains("arm64-v8a") &&
                !fingerprint.startsWith("generic") && "emulator" !in fingerprint &&
                hardware != "goldfish" && hardware != "ranchu" &&
                "sdk_gphone" !in Build.PRODUCT.lowercase(),
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val artifact = ModelArtifact(
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            sizeBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            format = ModelFormat.LITERT_LM,
            quantization = "INT4",
        )
        val downloads = ModelDownloadManager(context)
        val requestId = downloads.enqueue("litert-community/Qwen3-0.6B-int4", artifact)
        val completed = withTimeout(20 * 60 * 1000L) {
            downloads.observe(requestId).first { it.state.isFinished }
        }
        assertTrue("Model download failed: ${completed.error}", completed.state == WorkInfo.State.SUCCEEDED)
        val path = checkNotNull(completed.localPath)

        ExperimentalFlags.enableBenchmark = true
        // Warm each configuration before measurement so the first candidate cannot win merely
        // because the baseline built LiteRT's optimized cache for it.
        for (threads in THREAD_COUNTS) runCase(context, path, threads)
        val results = mutableListOf<Pair<Int, BenchmarkResult>>()
        // Every permutation occurs once. Each thread count appears in each position twice.
        val orders = listOf(
            listOf(1, 2, 4), listOf(4, 2, 1), listOf(2, 4, 1),
            listOf(1, 4, 2), listOf(4, 1, 2), listOf(2, 1, 4),
        )
        for ((round, order) in orders.withIndex()) {
            for (threads in order) results += round to runCase(context, path, threads)
        }
        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-cpu-thread-benchmark.txt")
        metricsFile.writeText(
            buildString {
                appendLine("model=${artifact.fileName}")
                appendLine("model_sha256=${artifact.sha256}")
                appendLine("model_revision=6aa2daf8aba4aa456797fb8040b36a3948bcfda7")
                appendLine("quantization=INT4;backend=CPU;context_tokens=$CONTEXT_TOKENS;output_budget=$MAX_OUTPUT_TOKENS")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL};android=${Build.VERSION.RELEASE};sdk=${Build.VERSION.SDK_INT}")
                appendLine("soc=${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unknown"}")
                appendLine("available_processors=${Runtime.getRuntime().availableProcessors()}")
                appendLine("prompt=$PROMPT")
                appendLine("sampler=top_k_1,top_p_1,temperature_0,seed_0;warmup_runs_per_configuration=1;measured_runs_per_configuration=6")
                appendLine("latency_milestones=nonempty_SDK_chunks_not_exact_token_indices;pss_sampling_interval_ms=100")
                results.forEach { (round, result) -> appendLine("round=$round;${result.toLine()}") }
                val baseline = results.filter { it.second.threadCount == 2 }.map { it.second.decodeTokensPerSecond }.median()
                for (threads in THREAD_COUNTS) {
                    val runs = results.filter { it.second.threadCount == threads }.map { it.second }
                    val throughput = runs.map { it.decodeTokensPerSecond }.median()
                    appendLine("summary.thread_count=$threads;median_decode_tokens_per_second=$throughput;decode_speedup_vs_production_2_threads=${throughput / baseline}")
                    appendLine("summary.thread_count=$threads;median_first_chunk_ms=${runs.map { it.firstChunkMs.toDouble() }.median()};median_first_visible_ms=${runs.map { it.firstVisibleMs.toDouble() }.median()}")
                }
                appendLine("thermal_notes=inspect_each_run_thermal_start_max_end_and_sustained_throughput;external_power_and_ambient_temperature_not_measured")
            },
        )
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "cp ${metricsFile.absolutePath} /sdcard/mobie-cpu-thread-benchmark.txt",
            ),
        ).use { it.readBytes() }
        Unit
    }

    private suspend fun runCase(context: Context, modelPath: String, threadCount: Int): BenchmarkResult =
        withContext(Dispatchers.Default) {
            coroutineScope {
                val manager = context.getSystemService(ActivityManager::class.java)
                fun pssBytes(): Long = manager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
                    .firstOrNull()?.totalPss?.toLong()?.times(1024L) ?: 0L
                fun thermalStatus(): Int = if (Build.VERSION.SDK_INT >= 29) {
                    context.getSystemService(PowerManager::class.java).currentThermalStatus
                } else 0
                val thermalStart = thermalStatus()
                val thermalMax = AtomicInteger(thermalStart)
                val sampledPeakPss = AtomicLong(pssBytes())
                val monitor = launch {
                    while (isActive) {
                        sampledPeakPss.accumulateAndGet(pssBytes(), ::maxOf)
                        thermalMax.accumulateAndGet(thermalStatus(), ::maxOf)
                        delay(100)
                    }
                }
                val modelFile = File(modelPath)
                val engine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(threadCount = threadCount),
                        maxNumTokens = CONTEXT_TOKENS,
                        cacheDir = File(checkNotNull(modelFile.parentFile), ".litert-cache").absolutePath,
                    ),
                )
                try {
                    val loadStarted = SystemClock.elapsedRealtime()
                    engine.initialize()
                    val loadMs = SystemClock.elapsedRealtime() - loadStarted
                    val conversation = engine.createConversation(
                        ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)),
                    )
                    try {
                        var nonemptyChunks = 0
                        var firstChunkMs: Long? = null
                        var secondChunkMs: Long? = null
                        var firstVisibleMs: Long? = null
                        val output = StringBuilder()
                        val started = SystemClock.elapsedRealtime()
                        withTimeout(5 * 60 * 1000L) {
                            conversation.sendMessageAsync(Contents.of(PROMPT), maxOutputToken = MAX_OUTPUT_TOKENS)
                                .collect { chunk ->
                                    val text = chunk.toString()
                                    output.append(text)
                                    if (text.isNotEmpty() || chunk.channels.values.any { it.isNotEmpty() }) {
                                        nonemptyChunks += 1
                                        val elapsed = SystemClock.elapsedRealtime() - started
                                        if (firstChunkMs == null) firstChunkMs = elapsed
                                        if (nonemptyChunks == 2) secondChunkMs = elapsed
                                        if (text.isNotEmpty() && firstVisibleMs == null) firstVisibleMs = elapsed
                                    }
                                }
                        }
                        val generationMs = SystemClock.elapsedRealtime() - started
                        assertTrue("LiteRT produced no visible output for threadCount=$threadCount", firstVisibleMs != null)
                        val benchmark = conversation.getBenchmarkInfo()
                        assertTrue("Invalid decode throughput for threadCount=$threadCount", benchmark.lastDecodeTokensPerSecond > 0.0)
                        assertTrue("Invalid prefill throughput for threadCount=$threadCount", benchmark.lastPrefillTokensPerSecond > 0.0)
                        sampledPeakPss.accumulateAndGet(pssBytes(), ::maxOf)
                        BenchmarkResult(
                            threadCount, benchmark.lastDecodeTokensPerSecond, benchmark.lastPrefillTokensPerSecond,
                            benchmark.lastDecodeTokenCount, benchmark.lastPrefillTokenCount,
                            checkNotNull(firstChunkMs), secondChunkMs, checkNotNull(firstVisibleMs),
                            loadMs, generationMs, sampledPeakPss.get(), thermalStart, thermalMax.get(), thermalStatus(),
                            output.toString(),
                        )
                    } finally {
                        conversation.close()
                    }
                } finally {
                    monitor.cancel()
                    if (engine.isInitialized()) engine.close()
                }
            }
        }

    private fun List<Double>.median(): Double = sorted().let { (it[(size - 1) / 2] + it[size / 2]) / 2 }

    private data class BenchmarkResult(
        val threadCount: Int,
        val decodeTokensPerSecond: Double,
        val prefillTokensPerSecond: Double,
        val decodeTokenCount: Int,
        val prefillTokenCount: Int,
        val firstChunkMs: Long,
        val secondChunkMs: Long?,
        val firstVisibleMs: Long,
        val loadMs: Long,
        val generationMs: Long,
        val sampledPeakPssBytes: Long,
        val thermalStart: Int,
        val thermalMax: Int,
        val thermalEnd: Int,
        val output: String,
    ) {
        fun toLine(): String =
            "thread_count=$threadCount;decode_tokens_per_second=$decodeTokensPerSecond;" +
                "prefill_tokens_per_second=$prefillTokensPerSecond;decode_token_count=$decodeTokenCount;prefill_token_count=$prefillTokenCount;" +
                "first_chunk_ms=$firstChunkMs;second_chunk_ms=$secondChunkMs;first_visible_ms=$firstVisibleMs;" +
                "load_ms=$loadMs;generation_ms=$generationMs;sampled_peak_pss_bytes=$sampledPeakPssBytes;" +
                "thermal_start=$thermalStart;thermal_max=$thermalMax;thermal_end=$thermalEnd;" +
                "output=${output.replace("\n", "\\n").replace("\r", "\\r")}"
    }
}
