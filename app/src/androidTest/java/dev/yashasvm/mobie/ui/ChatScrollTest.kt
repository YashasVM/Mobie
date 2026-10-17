package dev.yashasvm.mobie.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

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

    @Test
    fun sendingAfterScrollingUpFollowsTheNewPromptAndReply() {
        var state by mutableStateOf(historyState(RuntimeState.READY))
        compose.setContent {
            MobieTheme {
                ChatScreen(state = state, onSend = { prompt, _ ->
                    state = state.copy(
                        messages = state.messages + listOf(ChatMessage(true, prompt), ChatMessage(false, "")),
                        runtimeState = RuntimeState.GENERATING,
                    )
                    true
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("chat_messages").performScrollToIndex(0)
        compose.onNodeWithTag("chat_input").performTextInput("A new question")
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A new question").assertIsDisplayed()
    }

    @Test
    fun finishingAShortReplyScrollsItsFinalLinesIntoView() {
        var state by mutableStateOf(historyState(RuntimeState.GENERATING).let {
            it.copy(messages = it.messages.dropLast(1) + ChatMessage(false, "Short reply."))
        })
        compose.setContent {
            MobieTheme { ChatScreen(state = state, onSend = { _, _ -> false }) }
        }
        compose.waitForIdle()
        val finalText = "Short reply.\nOne more line.\nFinal answer."
        compose.runOnIdle {
            state = state.copy(
                messages = state.messages.dropLast(1) + ChatMessage(false, finalText),
                runtimeState = RuntimeState.READY,
            )
        }
        compose.waitForIdle()

        val replyBottom = compose.onNodeWithText(finalText).getUnclippedBoundsInRoot().bottom
        val viewportBottom = compose.onNodeWithTag("chat_messages").getUnclippedBoundsInRoot().bottom
        assertTrue("The completed reply must fit above the composer", replyBottom <= viewportBottom)
    }

    private fun historyState(runtimeState: RuntimeState) = MobieUiState(
        selected = AiModel("example/test", "Test model", "example", "Scroll fixture"),
        chatting = true,
        runtimeState = runtimeState,
        messages = List(30) { index ->
            ChatMessage(index % 2 == 0, "Message $index. " + "A paragraph in this conversation. ".repeat(8))
        },
    )
}
