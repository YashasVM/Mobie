package dev.yashasvm.mobie

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.GenerationConfig
import dev.yashasvm.mobie.core.runtime.InferenceEvent
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.LiteRtLmRuntimeAdapter
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import dev.yashasvm.mobie.ui.ChatMessage
import dev.yashasvm.mobie.ui.ChatScreen
import dev.yashasvm.mobie.ui.MobieUiState
import dev.yashasvm.mobie.ui.RuntimeState
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiteRtEndToEndTest {
    private companion object {
        const val TAG = "MobieRuntimeE2E"
        const val PROMPT = "Hey, who are you? Reply briefly."
        const val FOLLOW_UP_PROMPT = "Reply with one short sentence confirming you can answer a second prompt."
        const val RELOAD_PROMPT = "Reply with one short sentence confirming the model was reloaded."
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadsLoadsGeneratesRepeatedlyAndReloadsRealLiteRtModel() = runBlocking {
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

        val runtime = LiteRtLmRuntimeAdapter(context)
        runtime.load(path).getOrThrow()
        val firstEvents = generate(runtime, PROMPT)
        assertSuccessfulGeneration("first prompt", firstEvents)
        val output = firstEvents.visibleOutput()
        Log.i(TAG, "Prompt: $PROMPT")
        Log.i(TAG, "Qwen response: $output")
        logStats("first prompt", firstEvents)

        val followUpEvents = generate(runtime, FOLLOW_UP_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("second prompt", followUpEvents)
        Log.i(TAG, "Follow-up response: ${followUpEvents.visibleOutput()}")
        logStats("second prompt", followUpEvents)

        runtime.unload()
        runtime.load(path).getOrThrow()
        val reloadEvents = generate(runtime, RELOAD_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("post-reload prompt", reloadEvents)
        Log.i(TAG, "Reload response: ${reloadEvents.visibleOutput()}")
        logStats("post-reload prompt", reloadEvents)
        runtime.unload()

        persistMetrics(
            instrumentation = instrumentation,
            context = context,
            artifact = artifact,
            first = firstEvents.stats(),
            followUp = followUpEvents.stats(),
            reload = reloadEvents.stats(),
        )

        val model = AiModel(
            id = "litert-community/Qwen3-0.6B-int4",
            title = "Qwen3 0.6B INT4 (no-think)",
            author = "litert-community",
            description = "Qwen3 running locally with LiteRT-LM",
            artifacts = listOf(artifact),
        )
        val stats = firstEvents.stats()
        composeRule.setContent {
            MobieTheme {
                ChatScreen(
                    state = MobieUiState(
                        selected = model,
                        chatting = true,
                        runtimeState = RuntimeState.READY,
                        messages = listOf(ChatMessage(true, PROMPT), ChatMessage(false, output)),
                        stats = stats,
                    ),
                    onSend = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
        runCatching {
            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val screenshotFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-qwen-e2e.png")
            FileOutputStream(screenshotFile).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            copyToSharedStorage(instrumentation, screenshotFile, "mobie-qwen-e2e.png")
        }.onFailure { Log.w(TAG, "Runtime passed, but screenshot capture failed", it) }
        Unit
    }

    private suspend fun generate(
        runtime: LiteRtLmRuntimeAdapter,
        prompt: String,
        maxNewTokens: Int = 96,
    ): List<InferenceEvent> = withTimeout(15 * 60 * 1000L) {
        runtime.generate(prompt, config = GenerationConfig(maxNewTokens = maxNewTokens)).toList()
    }

    private fun assertSuccessfulGeneration(label: String, events: List<InferenceEvent>) {
        val output = events.visibleOutput()
        val errors = events.filterIsInstance<InferenceEvent.Error>().joinToString { it.message }
        assertTrue("LiteRT-LM produced no output for $label. Runtime errors: $errors", output.isNotBlank())
        assertTrue("Generation did not complete for $label", events.any { it is InferenceEvent.Complete })
        val stats = events.filterIsInstance<InferenceEvent.Stats>().lastOrNull()?.value
        assertTrue("No measured runtime stats for $label", stats != null)
        stats ?: return
        assertTrue("Generation duration was not measured for $label", stats.totalGenerationMs > 0)
        assertTrue(
            "Invalid TTFT for $label: ${stats.timeToFirstTokenMs}ms of ${stats.totalGenerationMs}ms",
            stats.timeToFirstTokenMs in 0..stats.totalGenerationMs,
        )
    }

    private fun logStats(label: String, events: List<InferenceEvent>) {
        val stats = events.stats()
        Log.i(
            TAG,
            "$label metrics: ${stats.tokensPerSecond} tokens/s, TTFT=${stats.timeToFirstTokenMs}ms, total=${stats.totalGenerationMs}ms, RAM=${stats.ramBytes} bytes",
        )
    }

    private fun persistMetrics(
        instrumentation: androidx.test.platform.app.InstrumentationRegistry.Companion,
        context: android.content.Context,
        artifact: ModelArtifact,
        first: InferenceStats,
        followUp: InferenceStats,
        reload: InferenceStats,
    ) {
        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-qwen-e2e-metrics.txt")
        metricsFile.writeText(
            buildString {
                appendLine("model=litert-community/Qwen3-0.6B-int4")
                appendLine("artifact=${artifact.fileName}")
                appendLine("artifact_bytes=${artifact.sizeBytes}")
                appendLine(metricsLine("first", first))
                appendLine(metricsLine("second", followUp))
                appendLine(metricsLine("reload", reload))
            },
        )
        copyToSharedStorage(instrumentation, metricsFile, "mobie-qwen-e2e-metrics.txt")
    }

    private fun metricsLine(label: String, stats: InferenceStats): String =
        "$label.tokens_per_second=${stats.tokensPerSecond};$label.ttft_ms=${stats.timeToFirstTokenMs};$label.total_ms=${stats.totalGenerationMs};$label.ram_bytes=${stats.ramBytes}"

    private fun copyToSharedStorage(
        instrumentation: androidx.test.platform.app.InstrumentationRegistry.Companion,
        source: File,
        destinationName: String,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cp ${source.absolutePath} /sdcard/$destinationName",
            ),
        ).use { it.readBytes() }
    }

    private fun List<InferenceEvent>.stats(): InferenceStats =
        filterIsInstance<InferenceEvent.Stats>().last().value

    private fun List<InferenceEvent>.visibleOutput(): String =
        filterIsInstance<InferenceEvent.Token>()
            .filterNot { it.thinking }
            .joinToString("") { it.text }
}
