package dev.yashasvm.mobie.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatRedesignTest {
    @get:Rule val composeRule = createComposeRule()
    private val model = AiModel(id = "example/test", title = "Test Model", author = "example", description = "Test")

    @Test
    fun firstSendLoadsThenSendsDraftOnceReady() {
        val state = mutableStateOf(MobieUiState(selected = model, runtimeState = RuntimeState.IDLE))
        var loads = 0
        val sent = mutableListOf<String>()
        composeRule.setContent {
            MobieTheme {
                ChatScreen(state = state.value, onLoad = {
                    loads++
                    state.value = state.value.copy(runtimeState = RuntimeState.LOADING)
                }, onSend = { text, _ -> sent.add(text); true })
            }
        }
        composeRule.onNodeWithTag("chat_input").performTextInput("Keep this draft")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.runOnIdle { assertEquals(1, loads); assertEquals(emptyList<String>(), sent) }
        composeRule.onNodeWithTag("chat_input").assertTextContains("Keep this draft")
        composeRule.runOnIdle { state.value = state.value.copy(runtimeState = RuntimeState.READY) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf("Keep this draft"), sent) }
        composeRule.onNodeWithTag("chat_input").assertTextEquals("")
    }

    @Test
    fun failedLoadRetainsDraftAndModelPickerWorksDuringLoadingAndError() {
        val state = mutableStateOf(MobieUiState(selected = model, runtimeState = RuntimeState.IDLE))
        var switches = 0
        val sent = mutableListOf<String>()
        composeRule.setContent {
            MobieTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ChatScreen(state = state.value, onSwitchModel = { switches++ }, onLoad = {
                        state.value = state.value.copy(runtimeState = RuntimeState.LOADING)
                    }, onSend = { text, _ -> sent.add(text); true })
                }
            }
        }
        composeRule.onNodeWithTag("chat_input").performTextInput("Do not lose me")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.onNodeWithText("Test Model").performClick()
        composeRule.runOnIdle { state.value = state.value.copy(runtimeState = RuntimeState.ERROR, error = "Free some memory and try again.") }
        composeRule.onNodeWithTag("chat_input").assertTextContains("Do not lose me")
        composeRule.onNodeWithText("Test Model").performClick()
        composeRule.runOnIdle { assertEquals(2, switches); assertEquals(emptyList<String>(), sent) }
        composeRule.onNodeWithText("Free some memory and try again.").assertIsDisplayed()
    }

    @Test
    fun chatWithoutModelStillShowsComposerAndSwitchControl() {
        var switches = 0
        composeRule.setContent { MobieTheme {
            ChatScreen(state = MobieUiState(), onSend = { _, _ -> false }, onSwitchModel = { switches++ })
        } }
        composeRule.onNodeWithText("What can I help with?").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").performTextInput("A draft before choosing")
        composeRule.onNodeWithText("Choose model").performClick()
        composeRule.runOnIdle { assertEquals(1, switches) }
    }
}
