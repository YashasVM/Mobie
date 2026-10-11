package dev.yashasvm.mobie.ui

import android.os.ParcelFileDescriptor
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.ui.theme.MobieTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.work.WorkInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogShowsRunnableModelAndOpensIt() {
        val model = testModel()
        var selected = false
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(models = listOf(model), loading = false),
                    onQuery = {},
                    onSearch = {},
                    onSelect = { selected = it == model },
                    onFeatured = {},
                )
            }
        }

        composeRule.onNodeWithText("Recommended").assertIsDisplayed()
        composeRule.onNodeWithText("Test Model").assertIsDisplayed().performClick()
        assertTrue(selected)
    }

    @Test
    fun modelPageShowsRequirementsAndStartsDownload() {
        var downloadRequested = false
        composeRule.setContent {
            ScreenTheme {
                ModelScreen(
                    state = MobieUiState(
                        selected = testModel(),
                        compatibility = CompatibilityResult(
                            status = Compatibility.COMPATIBLE,
                            reason = "Expected to run on this device.",
                            estimatedRamBytes = 2L * GIB,
                        ),
                        device = DeviceProfile(
                            totalRamBytes = 8L * GIB,
                            availableRamBytes = 6L * GIB,
                            availableStorageBytes = 20L * GIB,
                            supportedAbis = listOf("arm64-v8a"),
                            sdkInt = 35,
                        ),
                    ),
                    onDownload = { downloadRequested = true },
                    onRun = {},
                )
            }
        }

        composeRule.onNodeWithText("Type: Text generation").assertIsDisplayed()
        composeRule.onNodeWithTag("model_primary_action").assertIsDisplayed().performClick()
        assertTrue(downloadRequested)
    }

    @Test
    fun gatedModelExplainsWhyDownloadIsDisabled() {
        composeRule.setContent {
            ScreenTheme {
                ModelScreen(
                    state = MobieUiState(
                        selected = testModel().copy(gated = true),
                        compatibility = CompatibilityResult(
                            status = Compatibility.COMPATIBLE,
                            reason = "Expected to run on this device.",
                            estimatedRamBytes = 2L * GIB,
                        ),
                    ),
                    onDownload = {},
                    onRun = {},
                )
            }
        }

        composeRule.onNodeWithText("Hugging Face access required").assertIsDisplayed()
        composeRule.onNodeWithText("Download requires token").assertIsDisplayed()
    }

    @Test
    fun activeDownloadCanBeCancelled() {
        var cancelled = false
        composeRule.setContent {
            ScreenTheme {
                ModelScreen(
                    state = MobieUiState(
                        selected = testModel(),
                        compatibility = CompatibilityResult(
                            status = Compatibility.COMPATIBLE,
                            reason = "Ready",
                            estimatedRamBytes = 2L * GIB,
                        ),
                        download = DownloadProgress(WorkInfo.State.RUNNING, 50, 100, 10),
                    ),
                    onDownload = {},
                    onRun = {},
                    onCancelDownload = { cancelled = true },
                )
            }
        }

        composeRule.onNodeWithTag("cancel_download").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun chatComposerSendsTypedMessage() {
        var sent = ""
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(selected = testModel(), runtimeState = RuntimeState.READY),
                    onSend = { prompt, _ -> sent = prompt },
                )
            }
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Hello locally")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.runOnIdle { assertTrue(sent == "Hello locally") }
    }

    @Test
    fun textOnlyFallbackExplainsAndDisablesPhotoInput() {
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(selected = testModel().copy(type = ModelType.VISION), runtimeState = RuntimeState.READY),
                    onSend = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertDoesNotExist()
        composeRule.onNodeWithText("This phone loaded text chat only. Photo input is unavailable for this session.").assertIsDisplayed()
        captureEvidence("mobie-text-only-fallback.png")
    }

    @Test
    fun activeVisionEnablesPhotoInputAndExplainsImageGenerationLimit() {
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(
                        selected = testModel().copy(type = ModelType.VISION),
                        runtimeState = RuntimeState.READY,
                        imageInputReady = true,
                    ),
                    onSend = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertIsDisplayed()
        composeRule.onNodeWithText("Chat about text or an attached photo on this phone. Image generation isn't available.").assertIsDisplayed()
        captureEvidence("mobie-image-input.png")
    }

    @Test
    fun installedModelStartsChatAndCanBeDeleted() {
        val entry = InstalledModelEntry(testModel(), "/private/test.litertlm")
        var opened = false
        var deleted = false
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(installedModels = listOf(entry), loading = false),
                    onQuery = {},
                    onSearch = {},
                    onSelect = {},
                    onFeatured = {},
                    onOpenInstalled = { opened = it == entry },
                    onDeleteInstalled = { deleted = it == entry },
                )
            }
        }

        composeRule.onNodeWithTag("bottom_nav_installed").performClick()
        composeRule.onNodeWithText("Test Model").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
        composeRule.onNodeWithContentDescription("Delete Test Model").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    private fun testModel() = AiModel(
        id = "example/test-model",
        title = "Test Model",
        author = "example",
        description = "A small test model",
        artifacts = listOf(
            ModelArtifact(
                fileName = "test.litertlm",
                downloadUrl = "https://example.invalid/test.litertlm",
                sizeBytes = GIB,
                format = ModelFormat.LITERT_LM,
            ),
        ),
    )

    private companion object { const val GIB = 1024L * 1024L * 1024L }

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        file.outputStream().use { output ->
            assertTrue(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/$name"),
        ).use { it.readBytes() }
    }
}

/** Matches the content color that MobieApp's backdrop supplies to standalone screen tests. */
@Composable
private fun ScreenTheme(content: @Composable () -> Unit) {
    MobieTheme {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}
