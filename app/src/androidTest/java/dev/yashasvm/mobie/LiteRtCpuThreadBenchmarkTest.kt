package dev.yashasvm.mobie

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    }

    @Test
    fun comparesLiteRtDefaultCpuThreadsAgainstAllAvailableProcessors() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertE2E") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val artifact = ModelArtifact(
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
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
        val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        ExperimentalFlags.enableBenchmark = true
        val defaultThreads = runCase(context, path, threadCount = null)
        val allProcessors = runCase(context, path, threadCount = processorCount)

        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-cpu-thread-benchmark.txt")
        metricsFile.writeText(
            buildString {
                appendLine("available_processors=$processorCount")
                appendLine(defaultThreads.toLine("default"))
                appendLine(allProcessors.toLine("all_processors"))
                appendLine("decode_speedup=${ratio(allProcessors.decodeTokensPerSecond, defaultThreads.decodeTokensPerSecond)}")
                appendLine("prefill_speedup=${ratio(allProcessors.prefillTokensPerSecond, defaultThreads.prefillTokensPerSecond)}")
            },
        )
        instrumentation.uiAutomation.executeShellCommand(
            "cp ${metricsFile.absolutePath} /sdcard/mobie-cpu-thread-benchmark.txt",
        ).close()
    }

    private suspend fun runCase(context: Context, modelPath: String, threadCount: Int?): BenchmarkResult {
        val modelFile = File(modelPath)
        val cacheDirectory = File(checkNotNull(modelFile.parentFile), ".litert-cache")
        val engine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = if (threadCount == null) Backend.CPU() else Backend.CPU(threadCount = threadCount),
                maxNumTokens = 1_280,
                cacheDir = cacheDirectory.absolutePath,
            ),
        )
        try {
            engine.initialize()
            val conversation = engine.createConversation()
            try {
                val output = StringBuilder()
                withTimeout(5 * 60 * 1000L) {
                    conversation.sendMessageAsync(Contents.of(PROMPT), maxOutputToken = MAX_OUTPUT_TOKENS)
                        .collect { output.append(it.toString()) }
                }
                assertTrue("LiteRT produced no output for threadCount=$threadCount", output.isNotBlank())
                val benchmark = conversation.getBenchmarkInfo()
                assertTrue("Invalid decode throughput for threadCount=$threadCount", benchmark.lastDecodeTokensPerSecond > 0.0)
                assertTrue("Invalid prefill throughput for threadCount=$threadCount", benchmark.lastPrefillTokensPerSecond > 0.0)
                return BenchmarkResult(
                    threadCount = threadCount,
                    decodeTokensPerSecond = benchmark.lastDecodeTokensPerSecond,
                    prefillTokensPerSecond = benchmark.lastPrefillTokensPerSecond,
                    decodeTokenCount = benchmark.lastDecodeTokenCount,
                    prefillTokenCount = benchmark.lastPrefillTokenCount,
                )
            } finally {
                conversation.close()
            }
        } finally {
            if (engine.isInitialized()) engine.close()
        }
    }

    private fun ratio(candidate: Double, baseline: Double): Double =
        if (baseline > 0.0) candidate / baseline else 0.0

    private data class BenchmarkResult(
        val threadCount: Int?,
        val decodeTokensPerSecond: Double,
        val prefillTokensPerSecond: Double,
        val decodeTokenCount: Int,
        val prefillTokenCount: Int,
    ) {
        fun toLine(label: String): String =
            "$label.thread_count=${threadCount ?: "runtime_default"};" +
                "$label.decode_tokens_per_second=$decodeTokensPerSecond;" +
                "$label.prefill_tokens_per_second=$prefillTokensPerSecond;" +
                "$label.decode_token_count=$decodeTokenCount;" +
                "$label.prefill_token_count=$prefillTokenCount"
    }
}
