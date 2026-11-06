package dev.yashasvm.mobie.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatDrawerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedChatOpensDirectlyAndRedundantDestinationsAreAbsent() {
        var selected = ""
        var newChat = false
        composeRule.setContent {
            MobieTheme {
                ChatDrawer(
                    sessions = listOf(
                        ChatHistorySession("saved", "Plan a trip", 1L, listOf(HistoryMessage(true, "Plan a trip"))),
                        ChatHistorySession("empty", "Unsaved chat", 2L),
                    ),
                    onNewChat = { newChat = true },
                    onSelectHistory = { selected = it.id },
                    onModels = {},
                    onSettings = {},
                )
            }
        }
        composeRule.onNodeWithText("Chat history").assertIsDisplayed()
        composeRule.onNodeWithText("Plan a trip").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("saved", selected) }
        composeRule.onNodeWithText("Unsaved chat").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_chat").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_library").assertDoesNotExist()
        composeRule.onNodeWithTag("drawer_new_chat").performClick()
        composeRule.runOnIdle { assertTrue(newChat) }
    }

    @Test
    fun modelAndSettingsActionsStayVisibleWhileHistoryScrolls() {
        var models = false
        var settings = false
        composeRule.setContent {
            MobieTheme {
                ChatDrawer(
                    sessions = List(60) { index ->
                        ChatHistorySession("chat-$index", "Saved chat $index", index.toLong(), listOf(HistoryMessage(true, "Hello")))
                    },
                    onNewChat = {},
                    onSelectHistory = {},
                    onModels = { models = true },
                    onSettings = { settings = true },
                )
            }
        }
        composeRule.onNodeWithTag("drawer_history").performScrollToIndex(59)
        composeRule.onNodeWithTag("drawer_models").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("drawer_settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("drawer_new_chat").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(models && settings) }
    }

    @Test
    fun chatsWithSameSessionIdKeepTheirModelAssociation() {
        var selectedModel: String? = null
        composeRule.setContent {
            MobieTheme {
                ChatDrawer(
                    sessions = listOf(
                        ChatHistorySession("legacy", "Local chat", 1L, listOf(HistoryMessage(true, "Local")), "litert-community/local"),
                        ChatHistorySession("legacy", "Cloud chat", 2L, listOf(HistoryMessage(true, "Cloud")), "opencode/provider/model"),
                    ),
                    onNewChat = {},
                    onSelectHistory = { selectedModel = it.modelId },
                    onModels = {},
                    onSettings = {},
                )
            }
        }
        composeRule.onNodeWithText("Cloud chat").performClick()
        composeRule.runOnIdle { assertEquals("opencode/provider/model", selectedModel) }
        composeRule.onNodeWithText("Local chat").performClick()
        composeRule.runOnIdle { assertEquals("litert-community/local", selectedModel) }
    }

    @Test
    fun emptyHistoryExplainsThereAreNoChats() {
        composeRule.setContent {
            MobieTheme {
                ChatDrawer(emptyList(), onNewChat = {}, onSelectHistory = {}, onModels = {}, onSettings = {})
            }
        }
        composeRule.onNodeWithText("No saved chats yet").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_models").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_settings").assertIsDisplayed()
    }
}
