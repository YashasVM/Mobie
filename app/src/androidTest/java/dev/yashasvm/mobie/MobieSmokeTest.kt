package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MobieSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appInstallsLaunchesAndStoresTokenSecurely() {
        // The first-run welcome waits for the user; continue past it when present.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("continue").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Connect Hugging Face").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Recommended").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("continue").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("continue").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Connect Hugging Face").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Recommended").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("Connect Hugging Face").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Connect Hugging Face").assertIsDisplayed()
            composeRule.onNodeWithText("Use public models without a token").performClick()
        }
        composeRule.onNodeWithText("Recommended").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithText("Hugging Face access").assertIsDisplayed()
        val tokenAction = if (composeRule.onAllNodesWithText("Add token").fetchSemanticsNodes().isNotEmpty()) {
            "Add token"
        } else {
            "Change token"
        }
        composeRule.onNodeWithText(tokenAction).performClick()
        composeRule.onNodeWithTag("hf_token_input").performTextInput("hf_test_only_not_a_real_token")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.onNodeWithText("A token is securely stored on this device.", substring = true).assertIsDisplayed()
    }
}
