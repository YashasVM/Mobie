package dev.yashasvm.mobie

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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
import dev.yashasvm.mobie.core.runtime.RuntimeMessage
import dev.yashasvm.mobie.data.download.ModelDownloadManager
import dev.yashasvm.mobie.ui.ChatMessage
import dev.yashasvm.mobie.ui.ChatScreen
import dev.yashasvm.mobie.ui.MobieUiState
import dev.yashasvm.mobie.ui.RuntimeState
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
        const val CANCEL_PROMPT = "Write a long numbered list of 100 different practical uses for a local AI model on a phone."
        const val RECOVERY_PROMPT = "Reply with one short sentence confirming generation recovered after cancellation."
        const val RESET_PROMPT = "Reply with one short sentence confirming a fresh conversation works."
        const val RELOAD_PROMPT = "Reply with one short sentence confirming the model was reloaded."
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadsLoadsGeneratesRecoversFromCancellationResetsAndReloadsRealLiteRtModel() = runBlocking {
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

        val cancelledEvents = mutableListOf<InferenceEvent>()
        val firstCancelledToken = CompletableDeferred<Unit>()
        val cancellationJob = launch {
            runCatching {
                runtime.generate(CANCEL_PROMPT, config = GenerationConfig(maxNewTokens = 512)).collect { event ->
                    cancelledEvents += event
                    if (event is InferenceEvent.Token) firstCancelledToken.complete(Unit)
                }
            }
        }
        withTimeout(2 * 60 * 1000L) { firstCancelledToken.await() }
        runtime.cancel()
        withTimeout(2 * 60 * 1000L) { cancellationJob.join() }
        assertTrue(
            "Cancelled generation was incorrectly committed as complete",
            cancelledEvents.none { it is InferenceEvent.Complete },
        )
        val recoveryEvents = generate(runtime, RECOVERY_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("post-cancellation prompt", recoveryEvents)
        Log.i(TAG, "Recovery response: ${recoveryEvents.visibleOutput()}")
        logStats("post-cancellation prompt", recoveryEvents)

        val resetStartedNs = SystemClock.elapsedRealtimeNanos()
        runtime.resetConversation(
            listOf(
                RuntimeMessage(fromUser = true, text = "The conversation was reset without unloading model weights."),
                RuntimeMessage(fromUser = false, text = "Understood."),
            ),
        ).getOrThrow()
        val resetSetupMs = elapsedMs(resetStartedNs)
        val resetEvents = generate(runtime, RESET_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("post-reset prompt", resetEvents)
        Log.i(TAG, "Reset response: ${resetEvents.visibleOutput()}")
        logStats("post-reset prompt", resetEvents)

        val reloadStartedNs = SystemClock.elapsedRealtimeNanos()
        runtime.unload()
        runtime.load(path).getOrThrow()
        val fullReloadSetupMs = elapsedMs(reloadStartedNs)
        val reloadEvents = generate(runtime, RELOAD_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("post-reload prompt", reloadEvents)
        Log.i(TAG, "Reload response: ${reloadEvents.visibleOutput()}")
        logStats("post-reload prompt", reloadEvents)
        Log.i(TAG, "Conversation setup: reset=${resetSetupMs}ms, unload+reload=${fullReloadSetupMs}ms")
        runtime.unload()

        persistMetrics(
            instrumentation = instrumentation,
            context = context,
            artifact = artifact,
            first = firstEvents.stats(),
            followUp = followUpEvents.stats(),
            recovery = recoveryEvents.stats(),
            reset = resetEvents.stats(),
            reload = reloadEvents.stats(),
            resetSetupMs = resetSetupMs,
            fullReloadSetupMs = fullReloadSetupMs,
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
        assertTrue("LiteRT reported no prefill tokens for $label", stats.prefillTokenCount > 0)
        assertTrue("LiteRT reported invalid prefill throughput for $label", stats.prefillTokensPerSecond > 0.0)
        assertTrue("LiteRT reported no decode tokens for $label", stats.decodeTokenCount > 0)
    }

    private fun logStats(label: String, events: List<InferenceEvent>) {
        val stats = events.stats()
        Log.i(
            TAG,
            "$label metrics: decode=${stats.tokensPerSecond} tokens/s (${stats.decodeTokenCount} tokens), prefill=${stats.prefillTokensPerSecond} tokens/s (${stats.prefillTokenCount} tokens), TTFT=${stats.timeToFirstTokenMs}ms, total=${stats.totalGenerationMs}ms, RAM=${stats.ramBytes} bytes",
        )
    }

    private fun persistMetrics(
        instrumentation: Instrumentation,
        context: Context,
        artifact: ModelArtifact,
        first: InferenceStats,
        followUp: InferenceStats,
        recovery: InferenceStats,
        reset: InferenceStats,
        reload: InferenceStats,
        resetSetupMs: Double,
        fullReloadSetupMs: Double,
    ) {
        val metricsFile = File(checkNotNull(context.getExternalFilesDir(null)), "mobie-qwen-e2e-metrics.txt")
        metricsFile.writeText(
            buildString {
                appendLine("model=litert-community/Qwen3-0.6B-int4")
                appendLine("artifact=${artifact.fileName}")
                appendLine("artifact_bytes=${artifact.sizeBytes}")
                appendLine("conversation_reset_ms=$resetSetupMs")
                appendLine("full_unload_reload_ms=$fullReloadSetupMs")
                appendLine(metricsLine("first", first))
                appendLine(metricsLine("second", followUp))
                appendLine(metricsLine("post_cancel", recovery))
                appendLine(metricsLine("reset", reset))
                appendLine(metricsLine("reload", reload))
            },
        )
        copyToSharedStorage(instrumentation, metricsFile, "mobie-qwen-e2e-metrics.txt")
    }

    private fun metricsLine(label: String, stats: InferenceStats): String =
        "$label.decode_tokens_per_second=${stats.tokensPerSecond};$label.decode_token_count=${stats.decodeTokenCount};$label.prefill_tokens_per_second=${stats.prefillTokensPerSecond};$label.prefill_token_count=${stats.prefillTokenCount};$label.ttft_ms=${stats.timeToFirstTokenMs};$label.total_ms=${stats.totalGenerationMs};$label.ram_bytes=${stats.ramBytes}"

    private fun elapsedMs(startedNs: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000.0

    private fun copyToSharedStorage(
        instrumentation: Instrumentation,
        source: File,
        destinationName: String,
    ) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${source.absolutePath} /sdcard/$destinationName"),
        ).use { it.readBytes() }
    }

    private fun List<InferenceEvent>.stats(): InferenceStats =
        filterIsInstance<InferenceEvent.Stats>().last().value

    private fun List<InferenceEvent>.visibleOutput(): String =
        filterIsInstance<InferenceEvent.Token>()
            .filterNot { it.thinking }
            .joinToString("") { it.text }
}
