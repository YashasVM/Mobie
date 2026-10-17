package dev.yashasvm.mobie.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Rule
import org.junit.Test

class ChatScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun streamingKeepsTheOlderMessageTheReaderScrolledToVisible() {
        var state by mutableStateOf(
            MobieUiState(
                selected = AiModel("example/test", "Test model", "example", "Scroll fixture"),
                chatting = true,
                runtimeState = RuntimeState.GENERATING,
                messages = List(30) { index ->
                    ChatMessage(index % 2 == 0, "Message $index. " + "A paragraph in this conversation. ".repeat(8))
                },
            ),
        )
        compose.setContent {
            MobieTheme { ChatScreen(state = state, onSend = { _, _ -> false }) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("chat_messages").performScrollToIndex(0)
        compose.onNodeWithText("Message 0.", substring = true).assertIsDisplayed()

        compose.runOnIdle {
            state = state.copy(messages = state.messages.dropLast(1) + state.messages.last().copy(
                text = state.messages.last().text + "More streamed text. ".repeat(20),
            ))
        }
        compose.waitForIdle()

        compose.onNodeWithText("Message 0.", substring = true).assertIsDisplayed()
    }
}
