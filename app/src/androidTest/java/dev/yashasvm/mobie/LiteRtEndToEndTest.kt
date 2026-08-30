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
        const val PROMPT = "Hey, who are you? Reply briefly. /no_think"
        const val FOLLOW_UP_PROMPT = "Reply with one short sentence confirming you can answer a second prompt. /no_think"
        const val RELOAD_PROMPT = "Reply with one short sentence confirming the model was reloaded. /no_think"
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadsLoadsGeneratesRepeatedlyAndReloadsRealLiteRtModel() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("litertE2E") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val artifact = ModelArtifact(
            fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            sizeBytes = 344_437_808L,
            sha256 = "e3e290109da4388d65a17510a0c66af91c8039f52d2c465868dbc43c09a776cf",
            format = ModelFormat.LITERT_LM,
        )
        val downloads = ModelDownloadManager(context)
        val requestId = downloads.enqueue("litert-community/Qwen3-0.6B", artifact)
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

        val followUpEvents = generate(runtime, FOLLOW_UP_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("second prompt", followUpEvents)
        Log.i(TAG, "Follow-up response: ${followUpEvents.visibleOutput()}")

        runtime.unload()
        runtime.load(path).getOrThrow()
        val reloadEvents = generate(runtime, RELOAD_PROMPT, maxNewTokens = 64)
        assertSuccessfulGeneration("post-reload prompt", reloadEvents)
        Log.i(TAG, "Reload response: ${reloadEvents.visibleOutput()}")
        runtime.unload()

        val model = AiModel(
            id = "litert-community/Qwen3-0.6B",
            title = "Qwen3 0.6B",
            author = "litert-community",
            description = "Qwen3 running locally with LiteRT-LM",
            artifacts = listOf(artifact),
        )
        val stats = firstEvents.filterIsInstance<InferenceEvent.Stats>().last().value
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
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "cp ${screenshotFile.absolutePath} /sdcard/mobie-qwen-e2e.png",
                ),
            ).use { it.readBytes() }
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
        assertTrue("No measured runtime stats for $label", events.any { it is InferenceEvent.Stats })
    }

    private fun List<InferenceEvent>.visibleOutput(): String =
        filterIsInstance<InferenceEvent.Token>()
            .filterNot { it.thinking }
            .joinToString("") { it.text }
}
