package dev.yashasvm.mobie.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.data.download.DownloadFilePolicy
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RecentsNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun recentsRestoresBothModelsAfterComposerSwitchAndEmptyChat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context)
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val modelIds = listOf("mobie-test/navigation-first", "mobie-test/navigation-second")
        val directories = modelIds.map { File(File(context.filesDir, "models"), DownloadFilePolicy.storageKey(it)) }
        val transcripts = listOf(
            listOf(HistoryMessage(true, "First question"), HistoryMessage(false, "First saved answer")),
            listOf(HistoryMessage(true, "Second question"), HistoryMessage(false, "Second saved answer")),
        )
        try {
            modelIds.indices.forEach { index ->
                writeInstalledFixture(modelIds[index], directories[index])
                container.chatHistory.clear(modelIds[index])
                container.chatHistory.write(modelIds[index], transcripts[index])
            }
            container.lastUsedModel.write(modelIds[1])
            composeRule.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    MobieTheme { MobieApp(container) }
                }
            }
            awaitText("Second saved answer")
            assertRuntimeIdle(owner)
            openRecents()
            composeRule.onNodeWithText("First question").performClick()
            awaitText("First saved answer")
            assertRuntimeIdle(owner)
            composeRule.onNodeWithText("navigation-first").performClick()
            composeRule.onNodeWithText("navigation-second").performClick()
            awaitText("What can I help with?")
            assertRuntimeIdle(owner)
            composeRule.onNodeWithContentDescription("Start a new chat").performClick()
            openRecents()
            composeRule.onNodeWithText("First question").assertIsDisplayed()
            composeRule.onNodeWithText("Second question").assertIsDisplayed().performClick()
            awaitText("Second saved answer")
            assertRuntimeIdle(owner)
            composeRule.onNodeWithText("navigation-second").assertIsDisplayed()
            openRecents()
            composeRule.onNodeWithText("First question").performClick()
            awaitText("First saved answer")
            composeRule.runOnIdle {
                modelIds.indices.forEach { index ->
                    assertEquals(transcripts[index], container.chatHistory.sessions(modelIds[index])
                        .single { it.messages.isNotEmpty() }.messages)
                }
            }
        } finally {
            composeRule.runOnUiThread { owner.viewModelStore.clear() }
            modelIds.forEach(container.chatHistory::clear)
            directories.forEach { it.deleteRecursively() }
            container.lastUsedModel.write("")
        }
    }

    private fun openRecents() {
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithTag("nav_recents").performClick()
    }

    private fun assertRuntimeIdle(owner: ViewModelStoreOwner) {
        composeRule.runOnIdle {
            val state = ViewModelProvider(owner)[MobieViewModel::class.java].state.value
            assertEquals(RuntimeState.IDLE, state.runtimeState)
            assertNull(state.error)
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
