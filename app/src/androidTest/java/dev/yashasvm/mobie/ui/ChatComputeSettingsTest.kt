package dev.yashasvm.mobie.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The chat's one-tap route to the loaded model's run settings, wired the way MobieApp wires it. */
class ChatComputeSettingsTest {
    @get:Rule val composeRule = createComposeRule()

    private val model = AiModel(
        id = "example/test-model",
        title = "Test Model",
        author = "example",
        description = "A small test model",
        artifacts = listOf(ModelArtifact("test.litertlm", "https://example.invalid/test.litertlm", 344_671_744, format = ModelFormat.LITERT_LM)),
    )

    private val loaded = MobieUiState(
        selected = model,
        chatting = true,
        runtimeState = RuntimeState.READY,
        selectedArtifact = model.artifacts.first(),
        activeCompute = ActiveCompute(ComputeBackend.CPU, cpuThreads = 4),
        downloadedPath = "/private/test.litertlm",
    ).let { it.copy(appliedComputeOptions = it.effectiveComputeOptions) }

    @Test fun chatChipNamesTheActiveBackendAndOpensRunSettingsInOneTap() {
        var state by mutableStateOf(loaded)
        var show by mutableStateOf(false)
        var applied = 0
        composeRule.setContent {
            MobieTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ChatScreen(state = state, onSend = { _, _ -> true }, onComputeSettings = { show = true })
                    if (show && state.chatting) {
                        ComputeSettingsSheet(
                            state,
                            onOptionsChange = { state = state.copy(computeOptions = it) },
                            onSamplerChange = { state = state.copy(sampler = it); null },
                            onApply = { applied++; show = false },
                            onReset = {},
                            onClose = { show = false },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("compute_sheet").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Run settings. Running on CPU · 4 threads").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("run settings").assertIsDisplayed()
        composeRule.onNodeWithText("Test Model · running on CPU · 4 threads").assertIsDisplayed()
        composeRule.onNodeWithTag("backend_AUTO").assertIsSelected()
        composeRule.onNodeWithText("These settings are in use.").performScrollTo().assertIsDisplayed()
        captureUiFixture("chat-run-settings-sheet-fixture.png")

        // Changing the backend from chat asks for an explicit reload, and applying closes the sheet.
        composeRule.onNodeWithTag("backend_GPU").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ComputeBackend.GPU, state.computeOptions.backend) }
        composeRule.onNodeWithTag("apply_compute").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, applied); assertTrue(!show) }
        composeRule.onNodeWithTag("compute_sheet").assertDoesNotExist()

        // Close leaves settings untouched.
        composeRule.onNodeWithTag("open_compute_settings").performClick()
        composeRule.onNodeWithContentDescription("Close run settings").performClick()
        composeRule.runOnIdle { assertTrue(!show); assertEquals(1, applied) }
    }

    @Test fun chipStillOffersRunSettingsBeforeTheEngineReportsABackend() {
        var opened = 0
        composeRule.setContent {
            MobieTheme {
                ChatScreen(state = loaded.copy(activeCompute = null), onSend = { _, _ -> true }, onComputeSettings = { opened++ })
            }
        }
        composeRule.onNodeWithContentDescription("Run settings").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun fallbackReasonIsShownUnderTheChip() {
        composeRule.setContent {
            MobieTheme {
                ChatScreen(
                    state = loaded.copy(activeCompute = ActiveCompute(ComputeBackend.CPU, 4, "GPU could not load this model. Using CPU.")),
                    onSend = { _, _ -> true },
                )
            }
        }
        composeRule.onNodeWithTag("chat_compute_fallback").assertIsDisplayed()
        composeRule.onNodeWithText("GPU could not load this model. Using CPU.").assertIsDisplayed()
        captureUiFixture("chat-compute-chip-fixture.png")
    }

    private fun captureUiFixture(name: String) {
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(checkNotNull(context.getExternalFilesDir(null)), name)
        file.outputStream().use { output ->
            assertTrue(checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
