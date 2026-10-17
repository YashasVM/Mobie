package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MobieSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun chatHomeShown(): Boolean =
        composeRule.onAllNodesWithTag("chat_home_empty").fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("chat_screen").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun appLaunchesIntoChatHomeAndStoresTokenSecurely() {
        // The first-run welcome waits for the user; continue past it when present.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Continue", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() || chatHomeShown()
        }
        if (composeRule.onAllNodesWithText("Continue", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Continue", ignoreCase = true).performClick()
        }
        // The app opens on the chat home, never on the model catalog.
        composeRule.waitUntil(timeoutMillis = 10_000) { chatHomeShown() }
        assertTrue(composeRule.onAllNodesWithText("Recommended").fetchSemanticsNodes().isEmpty())

        // Settings live in the chats drawer.
        composeRule.onNodeWithTag("open_chats").performClick()
        composeRule.onNodeWithTag("drawer_settings").performClick()
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

        // Back from the Models screens returns to the chat home.
        composeRule.onNodeWithTag("catalog_back").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { chatHomeShown() }
    }
}
