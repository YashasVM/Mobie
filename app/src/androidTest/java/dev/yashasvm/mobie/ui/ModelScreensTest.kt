package dev.yashasvm.mobie.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.ui.theme.MobieTheme
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
            MobieTheme {
                CatalogScreen(
                    state = MobieUiState(models = listOf(model), loading = false),
                    onQuery = {},
                    onSearch = {},
                    onSelect = { selected = it == model },
                    onFeatured = {},
                )
            }
        }

        composeRule.onNodeWithText("Popular models").assertIsDisplayed()
        composeRule.onNodeWithText("Test Model").assertIsDisplayed().performClick()
        assertTrue(selected)
    }

    @Test
    fun modelPageShowsRequirementsAndStartsDownload() {
        var downloadRequested = false
        composeRule.setContent {
            MobieTheme {
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
                    onRequest = {},
                    onConfigureToken = {},
                )
            }
        }

        composeRule.onNodeWithText("Type: Text generation").assertIsDisplayed()
        composeRule.onNodeWithText("llama.cpp").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Q4_K_M").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download & Run").performScrollTo().performClick()
        assertTrue(downloadRequested)
    }

    private fun testModel() = AiModel(
        id = "example/test-model",
        title = "Test Model",
        author = "example",
        description = "A small test model",
        artifacts = listOf(
            ModelArtifact(
                fileName = "test-q4-k-m.gguf",
                downloadUrl = "https://example.invalid/test.gguf",
                sizeBytes = GIB,
                format = ModelFormat.GGUF,
                quantization = "Q4_K_M",
            ),
        ),
    )

    private companion object { const val GIB = 1024L * 1024L * 1024L }
}
