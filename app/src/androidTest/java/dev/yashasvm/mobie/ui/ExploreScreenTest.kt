package dev.yashasvm.mobie.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExploreScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exploreRoutesToPromptLab() {
        var selected: ExploreTask? = null
        compose.setContent { MobieTheme { ExploreScreen(MobieUiState(), {}, { selected = it }, {}) } }
        compose.onNodeWithText("What will you make today?").assertIsDisplayed()
        compose.onNodeWithTag("explore_LAB").performScrollTo().performClick()
        assertEquals(ExploreTask.LAB, selected)
    }

    @Test fun emptyVisionLibraryExplainsWhatToDownload() {
        var browsed = false
        compose.setContent { MobieTheme {
            TaskWorkspace(ExploreTask.IMAGE, MobieUiState(), {}, { browsed = true }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Install a vision model to ask about photos.").assertIsDisplayed()
        compose.onNodeWithText("Find a model").performClick()
        assertEquals(true, browsed)
        compose.onNodeWithTag("run_task").performScrollTo().assertIsNotEnabled()
    }

    @Test fun promptLabRequiresInputAndPassesTemplateToSelectedLocalModel() {
        val entry = InstalledModelEntry(AiModel("fixture", "Local fixture", "fixture", "",
            artifacts = listOf(ModelArtifact("fixture.litertlm", "https://fixture.invalid", 1024, format = ModelFormat.LITERT_LM))), "/private/fixture")
        var submitted: Pair<InstalledModelEntry, String?>? = null
        compose.setContent { MobieTheme {
            TaskWorkspace(ExploreTask.LAB, MobieUiState(installedModels = listOf(entry)), {}, {},
                { model, prompt -> submitted = model to prompt }, {})
        } }
        compose.onNodeWithTag("run_task").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Summarize").performScrollTo().performClick()
        compose.onNodeWithTag("lab_input").performScrollTo().performTextInput("Some notes")
        compose.onNodeWithTag("run_task").performScrollTo().performClick()
        assertEquals(entry, submitted?.first)
        assertEquals("Summarize the following text. Preserve key facts and avoid inventing details.\n\nSome notes", submitted?.second)
    }
}
