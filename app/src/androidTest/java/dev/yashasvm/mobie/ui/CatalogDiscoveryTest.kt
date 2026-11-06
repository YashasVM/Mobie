package dev.yashasvm.mobie.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogDiscoveryTest {
    @get:Rule val compose = createComposeRule()
    private val text = AiModel("text", "Text fixture", "publisher", "", type = ModelType.TEXT_GENERATION)

    @Test
    fun capabilityFilterCanBeResetFromEmptyResults() {
        compose.setContent {
            MobieTheme {
                CatalogScreen(MobieUiState(models = listOf(text)), {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("catalog_filter_PHOTOS").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Show all results"))
        compose.onNodeWithText("Show all results").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Text fixture"))
        compose.onNodeWithText("Text fixture").assertIsDisplayed()
    }

    @Test
    fun searchFailureRetriesTheCurrentQuery() {
        var searched = false
        var featured = false
        compose.setContent {
            MobieTheme {
                CatalogScreen(MobieUiState(query = "gemma", catalogError = "Connection unavailable"),
                    {}, { searched = true }, {}, { featured = true })
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(searched && !featured) }
    }

    @Test
    fun installedModelCanAlsoAppearInDownloadResults() {
        compose.setContent {
            MobieTheme {
                CatalogScreen(MobieUiState(models = listOf(text),
                    installedModels = listOf(InstalledModelEntry(text, "/private/model.litertlm"))), {}, {}, {}, {})
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Download models"))
        compose.onNodeWithText("Download models").assertIsDisplayed()
    }
}
