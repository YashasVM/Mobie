package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MobieSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun exploreOpensPromptLabAndSavedGenerationSettings() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("open_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("open_menu").performClick()
        composeRule.onNodeWithTag("drawer_explore").performClick()
        composeRule.onNodeWithTag("explore_LAB").performScrollTo().performClick()
        composeRule.onNodeWithTag("lab_input").performScrollTo().performTextInput("Explain a rainbow")
        composeRule.onNodeWithText("Generation settings").performScrollTo().performClick()
        composeRule.onNodeWithTag("compute_advanced").performScrollTo().performClick()
        composeRule.onNodeWithTag("sampler_temperature").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("close_compute_settings").performClick()
        composeRule.onNodeWithTag("lab_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Explore").performClick()
        composeRule.onNodeWithTag("explore_LAB").performScrollTo().performClick()
        composeRule.onNodeWithTag("lab_input").performScrollTo().assertTextContains("Explain a rainbow")
    }

    @Test
    fun appInstallsLaunchesAndStoresTokenSecurely() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("open_menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("open_menu").performClick()
        composeRule.onNodeWithTag("drawer_settings").performClick()
        composeRule.onNodeWithTag("catalog_title").assertIsDisplayed()
        composeRule.onNodeWithText("Hugging Face access").performScrollTo().assertIsDisplayed()
        val tokenAction = if (composeRule.onAllNodesWithText("Add token").fetchSemanticsNodes().isNotEmpty()) {
            "Add token"
        } else {
            "Change token"
        }
        composeRule.onNodeWithText(tokenAction).performScrollTo().performClick()
        composeRule.onNodeWithTag("hf_token_input").performScrollTo().performTextInput("hf_test_only_not_a_real_token")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithText("Saved securely on this phone.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("open_menu").performClick()
        composeRule.onNodeWithText("Chat history").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_chat").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_library").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_models").performClick()
        composeRule.onNodeWithTag("catalog_title").assertIsDisplayed()
        composeRule.onNodeWithTag("open_menu").performClick()
        composeRule.onNodeWithTag("drawer_settings").performClick()
        composeRule.onNodeWithText("Saved securely on this phone.").performScrollTo().assertIsDisplayed()
    }
}
