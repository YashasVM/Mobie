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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeBackend
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

        composeRule.onNodeWithText("Models").assertIsDisplayed()
        composeRule.onNodeWithText("Download models").assertIsDisplayed()
        composeRule.onNodeWithText("Test Model").assertIsDisplayed().performClick()
        assertTrue(selected)
    }

    @Test
    fun modelsShowDeviceMemoryAndModelEstimateWithoutStats() {
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(models = listOf(testModel()), loading = false,
                        device = DeviceProfile(totalRamBytes = 8L * GIB, availableRamBytes = 6L * GIB,
                            availableStorageBytes = 20L * GIB, supportedAbis = listOf("arm64-v8a"), sdkInt = 35)),
                    onQuery = {}, onSearch = {}, onSelect = {}, onFeatured = {},
                )
            }
        }
        composeRule.onNodeWithText("Available RAM").assertIsDisplayed()
        composeRule.onNodeWithText("Free storage").assertIsDisplayed()
        composeRule.onNodeWithText("Estimated RAM", substring = true).performScrollTo().assertIsDisplayed()
        captureEvidence("mobie-models-memory.png")
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

        composeRule.onNodeWithText("Text generation").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Model needs"))
        composeRule.onNodeWithText("Model needs").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Download size"))
        composeRule.onNodeWithText("Download size").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Quantization"))
        composeRule.onNodeWithText("Quantization").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("License"))
        composeRule.onNodeWithText("License").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Access"))
        composeRule.onNodeWithText("Access").assertIsDisplayed()
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
                    onSend = { prompt, _ -> sent = prompt; true },
                )
            }
        }

        composeRule.onNodeWithTag("chat_input").performTextInput("Hello locally")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.runOnIdle { assertTrue(sent == "Hello locally") }
    }

    @Test
    fun terminalChatKeepsConversationVisibleAndOpensRunSettings() {
        var settingsOpened = false
        var menuOpened = false
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(
                        selected = testModel(),
                        runtimeState = RuntimeState.READY,
                        activeCompute = ActiveCompute(backend = ComputeBackend.GPU),
                        statsForNerds = true,
                        messages = listOf(
                            ChatMessage(fromUser = true, text = "Where do my messages go?"),
                            ChatMessage(fromUser = false, text = "This is a UI fixture. Inference runs locally on your phone."),
                        ),
                    ),
                    onSend = { _, _ -> true },
                    onMenu = { menuOpened = true },
                    onComputeSettings = { settingsOpened = true },
                )
            }
        }
        composeRule.onNodeWithText("Where do my messages go?").assertIsDisplayed()
        composeRule.onNodeWithTag("open_menu").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(menuOpened) }
        composeRule.onNodeWithText("This is a UI fixture. Inference runs locally on your phone.").assertIsDisplayed()
        composeRule.onNodeWithTag("open_compute_settings").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(settingsOpened) }
        captureEvidence("mobie-terminal-chat.png")
    }

    @Test
    fun textOnlyFallbackDisablesPhotoInput() {
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(selected = testModel().copy(type = ModelType.VISION), runtimeState = RuntimeState.READY),
                    onSend = { _, _ -> true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
        captureEvidence("mobie-text-only-fallback.png")
    }

    @Test
    fun activeVisionEnablesPhotoInput() {
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(
                        selected = testModel().copy(type = ModelType.VISION),
                        runtimeState = RuntimeState.READY,
                        imageInputReady = true,
                    ),
                    onSend = { _, _ -> true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
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
                    page = CatalogPage.MODELS,
                )
            }
        }

        composeRule.onNodeWithText("Models").assertIsDisplayed()
        composeRule.onNodeWithText("Test Model").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
        composeRule.onNodeWithContentDescription("Delete Test Model").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun cloudStoreRequiresDisclosureConfirmationAndShowsPrivacyTerms() {
        val cloud = dev.yashasvm.mobie.core.model.CloudModel("fixture-vision", "Vision fixture", true, "Fixture privacy")
        var selected = false
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(cloudModels = listOf(cloud), cloudConnectionUrl = "https://fixture.invalid"),
                    onQuery = {}, onSearch = {}, onSelect = {}, onFeatured = {},
                    onSelectCloud = { selected = it == cloud },
                )
            }
        }
        composeRule.onNodeWithText("Cloud").assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("cloud_model_${cloud.id}"))
        composeRule.onNodeWithTag("cloud_model_${cloud.id}").performClick()
        composeRule.runOnIdle { assertTrue(!selected) }
        composeRule.onNodeWithText("Privacy terms").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertTrue(!selected) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("cloud_model_${cloud.id}"))
        composeRule.onNodeWithTag("cloud_model_${cloud.id}").performClick()
        composeRule.onNodeWithTag("confirm_cloud_model").performClick()
        composeRule.runOnIdle { assertTrue(selected) }
        captureEvidence("mobie-cloud-store.png")
    }

    @Test
    fun cloudChatLabelsRemoteRoutingAndEnablesSupportedPhotos() {
        val cloud = dev.yashasvm.mobie.core.model.CloudModel("fixture-vision", "Vision fixture", true, "Fixture privacy")
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(state = MobieUiState(selected = cloud.asAiModel(),
                    runtimeState = RuntimeState.READY, imageInputReady = true), onSend = { _, _ -> true })
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertIsDisplayed()
        composeRule.onNodeWithText("Cloud · OpenCode").assertIsDisplayed()
        composeRule.onNodeWithText("offline · on this phone").assertDoesNotExist()
        composeRule.onNodeWithTag("open_compute_settings").assertDoesNotExist()
        captureEvidence("mobie-cloud-chat.png")
    }

    @Test
    fun textOnlyCloudModelDisablesPhotoInput() {
        val cloud = dev.yashasvm.mobie.core.model.CloudModel("fixture-text", "Text fixture", false, "Fixture privacy")
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(state = MobieUiState(selected = cloud.asAiModel(),
                    runtimeState = RuntimeState.READY), onSend = { _, _ -> true })
            }
        }
        composeRule.onNodeWithContentDescription("Attach image").assertDoesNotExist()
        composeRule.onNodeWithText("Cloud · OpenCode").assertIsDisplayed()
    }

    @Test
    fun modelsMenuOpensWithoutBottomTabs() {
        var menuOpened = false
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(loading = false),
                    onQuery = {}, onSearch = {}, onSelect = {}, onFeatured = {},
                    onMenu = { menuOpened = true },
                )
            }
        }
        composeRule.onNodeWithTag("open_menu").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(menuOpened) }
        composeRule.onNodeWithTag("bottom_nav_discover").assertDoesNotExist()
        composeRule.onNodeWithTag("bottom_nav_cloud").assertDoesNotExist()
    }

    @Test
    fun chatModelHeaderOpensPickerAndStatsAreHiddenByDefault() {
        var pickerOpened = false
        composeRule.setContent {
            ScreenTheme {
                ChatScreen(
                    state = MobieUiState(
                        selected = testModel(), runtimeState = RuntimeState.READY,
                        activeCompute = ActiveCompute(backend = ComputeBackend.CPU),
                    ),
                    onSend = { _, _ -> true },
                    onModelPicker = { pickerOpened = true },
                )
            }
        }
        composeRule.onNodeWithTag("chat_model_picker").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(pickerOpened) }
        composeRule.onNodeWithTag("run_details_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("open_compute_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_controls").assertIsDisplayed()
    }

    @Test
    fun settingsExposeRuntimeControlsWithoutEnablingStats() {
        var statsEnabled = false
        var runtimeOpened = false
        composeRule.setContent {
            ScreenTheme {
                CatalogScreen(
                    state = MobieUiState(cloudConnectionUrl = "https://fixture.invalid"),
                    page = CatalogPage.SETTINGS,
                    onQuery = {}, onSearch = {}, onSelect = {}, onFeatured = {},
                    onStatsForNerdsChange = { statsEnabled = it },
                    onAdvancedSettings = { runtimeOpened = true },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("catalog_title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("advanced_settings").assertDoesNotExist()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("runtime_settings"))
        composeRule.onNodeWithTag("runtime_settings").performClick()
        composeRule.runOnIdle { assertTrue(runtimeOpened) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("stats_for_nerds"))
        composeRule.onNodeWithTag("stats_for_nerds").performClick()
        composeRule.runOnIdle { assertTrue(statsEnabled) }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("runtime_settings"))
        composeRule.onNodeWithTag("runtime_settings").performClick()
        composeRule.runOnIdle { assertTrue(runtimeOpened) }
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
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
