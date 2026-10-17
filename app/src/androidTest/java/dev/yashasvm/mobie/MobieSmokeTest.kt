package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MobieSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fourDestinationsAndAppearanceAndSecureToken() {
        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
        navigate("recents")
        composeRule.onNodeWithText("No chats yet").assertIsDisplayed()
        navigate("models")
        composeRule.onNodeWithText("Browse").assertIsDisplayed()
        navigate("settings")
        composeRule.onNodeWithTag("theme_light").performClick()
        composeRule.onNodeWithText("Color mode").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_dark").performClick()
        composeRule.onNodeWithText("Add token").performClick()
        composeRule.onNodeWithTag("hf_token_input").performTextInput("hf_test_only_not_a_real_token")
        composeRule.onNodeWithText("Save token").performClick()
        composeRule.onNodeWithText("Token saved securely").assertIsDisplayed()
        composeRule.onNodeWithText("Remove token").performClick()
        composeRule.onNodeWithText("No token added").assertIsDisplayed()
        navigate("chat")
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
    }

    private fun navigate(page: String) {
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithTag("nav_$page").performClick()
    }
}
