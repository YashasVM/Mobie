package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.junit.Rule
import org.junit.Test

class MobieSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fourDestinationsAndAppearanceAndSecureToken() {
        composeRule.onNodeWithTag("chat_screen").assertIsDisplayed()
        capture("chat-dark")
        composeRule.onNodeWithTag("chat_input").performTextInput("Draft stays here")
        closeSoftKeyboard()
        navigate("recents")
        composeRule.onNodeWithText("No chats yet").assertIsDisplayed()
        capture("recents-dark")
        navigate("models")
        composeRule.onNodeWithText("Browse").assertIsDisplayed()
        capture("models-dark")
        composeRule.onNodeWithTag("model_search").performTextInput("Qwen")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("bottom_nav_installed").performClick()
        navigate("settings")
        capture("settings-dark")
        composeRule.onNodeWithTag("theme_light").performClick()
        capture("settings-light")
        composeRule.onNodeWithText("Color mode").assertIsDisplayed()
        navigate("chat")
        composeRule.onNodeWithTag("chat_input").assertTextContains("Draft stays here")
        capture("chat-light")
        navigate("recents")
        composeRule.onNodeWithText("No chats yet").assertIsDisplayed()
        capture("recents-light")
        navigate("models")
        composeRule.onNodeWithTag("bottom_nav_installed").assertIsSelected()
        composeRule.onNodeWithTag("models_browse").performClick()
        composeRule.onNodeWithTag("model_search").assertTextContains("Qwen")
        capture("models-light")
        navigate("settings")
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

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { output ->
            check(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun navigate(page: String) {
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onAllNodes(SemanticsMatcher("Primary destination") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("nav_")
        }).assertCountEquals(4)
        composeRule.onNodeWithTag("nav_$page").performClick()
    }
}
