package dev.yashasvm.mobie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.data.history.HistoryAttachment
import dev.yashasvm.mobie.data.history.HistoryMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Drives MainActivity and the real ViewModel/store rather than callback-capturing composables. */
class ChatHistoryAppWiringTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun opensRenamesRecreatesAndDeletesTheCorrectCrossModelChat() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MobieApplication
        val history = app.container.chatHistory
        val alpha = "integration-fixture/alpha"
        val beta = "integration-fixture/beta"
        val alphaId = "integration-alpha-session"
        val betaId = "integration-beta-session"
        history.delete(alpha, alphaId)
        history.delete(beta, betaId)
        app.container.tokenStore.markWelcomeSeen()
        history.writeSession(alpha, alphaId, listOf(HistoryMessage(true, "Integration alpha chat")), "Fixture Alpha")
        history.writeSession(beta, betaId, listOf(
            HistoryMessage(true, "Integration beta chat", attachments = listOf(HistoryAttachment("fixture-notes.md", "text/markdown", 12, "Local notes"))),
            HistoryMessage(false, "A saved local answer for beta"),
        ), "Fixture Beta")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("open_chats")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("open_chats").performClick()
                compose.onNodeWithText("Integration beta chat").performClick()
                compose.onNodeWithTag("read_only_banner").assertIsDisplayed()
                compose.onNodeWithText("A saved local answer for beta").assertIsDisplayed()
                compose.onNodeWithText("fixture-notes.md").assertIsDisplayed()
                compose.onNodeWithTag("open_chats").performClick()
                compose.onNodeWithContentDescription("More options for Integration beta chat").performClick()
                compose.onNodeWithText("Rename").performClick()
                compose.onNodeWithTag("rename_chat_input").performTextClearance()
                compose.onNodeWithTag("rename_chat_input").performTextInput("Renamed beta fixture")
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(10_000) { runBlocking { history.session(beta, betaId)?.title == "Renamed beta fixture" } }
                scenario.recreate()
                compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("open_chats")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("open_chats").performClick()
                compose.onNodeWithText("Renamed beta fixture").assertIsDisplayed().performClick()
                compose.onNodeWithText("A saved local answer for beta").assertIsDisplayed()
                compose.onNodeWithTag("open_chats").performClick()
                compose.onNodeWithContentDescription("More options for Renamed beta fixture").performClick()
                compose.onNodeWithText("Delete").performClick()
                compose.onNodeWithText("Delete this chat?").assertIsDisplayed()
                compose.onNodeWithText("Delete").performClick()
                compose.waitUntil(10_000) { runBlocking { history.session(beta, betaId) == null } }
                assertNotNull(history.session(alpha, alphaId))
                assertEquals("Integration alpha chat", history.session(alpha, alphaId)?.title)
            }
        } finally {
            history.delete(alpha, alphaId)
            history.delete(beta, betaId)
        }
    }
}
