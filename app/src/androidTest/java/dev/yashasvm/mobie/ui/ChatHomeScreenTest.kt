package dev.yashasvm.mobie.ui

import android.os.ParcelFileDescriptor
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.ChatThread
import dev.yashasvm.mobie.data.history.HistoryMessage
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var openedThread: ChatThread? = null
    private var renamed: Pair<ChatThread, String>? = null
    private var deleted: ChatThread? = null
    private var picked: InstalledModelEntry? = null
    private var openedModels: CatalogTab? = null
    private var gotModel: String? = null
    private var newChats = 0
    private var reclaimRetries = 0

    @Test
    fun newChatShowsGreetingWhileModelLoads() {
        setHome(MobieUiState(selected = model, chatting = true, runtimeState = RuntimeState.LOADING))
        composeRule.onNodeWithText("What can I help with?").assertIsDisplayed()
        composeRule.onNodeWithText("Loading Test Model. The first load can take a while.").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").assertIsDisplayed()
        captureEvidence("mobie-chat-home.png")
    }

    @Test
    fun chatsDrawerGroupsChatsAcrossModelsAndOpensOne() {
        setHome(loadedState())
        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithTag("chats_drawer").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Older").assertIsDisplayed()
        composeRule.onNodeWithText("Other Model").assertIsDisplayed()
        captureEvidence("mobie-chats-drawer.png")

        composeRule.onNodeWithTag("chat_search").performTextInput("poem")
        composeRule.onNodeWithText("Trip ideas").assertDoesNotExist()
        composeRule.onNodeWithText("Old poem").performClick()
        composeRule.runOnIdle { assertEquals(oldChat, openedThread) }
    }

    @Test
    fun chatCanBeRenamedAndDeletedFromTheDrawer() {
        setHome(loadedState())
        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithContentDescription("More options for Trip ideas").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag("rename_chat_input").performTextClearance()
        composeRule.onNodeWithTag("rename_chat_input").performTextInput("Lisbon")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.runOnIdle { assertEquals(todayChat to "Lisbon", renamed) }

        composeRule.onNodeWithContentDescription("More options for Trip ideas").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete this chat?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle { assertEquals(todayChat, deleted) }
    }

    @Test
    fun drawerFooterOpensModelsAndNewChat() {
        setHome(loadedState())
        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithTag("drawer_models").performClick()
        composeRule.runOnIdle { assertEquals(CatalogTab.LIBRARY, openedModels) }
        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithTag("drawer_new_chat").performClick()
        composeRule.runOnIdle { assertEquals(1, newChats) }
    }

    @Test
    fun modelPickerListsInstalledModelsAndFindsMore() {
        setHome(loadedState())
        composeRule.onNodeWithTag("model_picker_button").performClick()
        composeRule.onNodeWithText("Models on this phone").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Current model").assertIsDisplayed()
        captureEvidence("mobie-model-picker.png")
        composeRule.onNodeWithText("Second Model").performClick()
        composeRule.runOnIdle { assertEquals(secondEntry, picked) }

        composeRule.onNodeWithTag("model_picker_button").performClick()
        composeRule.onNodeWithTag("find_more_models").performClick()
        composeRule.runOnIdle { assertEquals(CatalogTab.DISCOVER, openedModels) }
    }

    @Test
    fun chatForRemovedModelIsReadOnlyAndOffersTheModel() {
        setHome(
            MobieUiState(
                readOnlyChat = ReadOnlyChat("example/other", "Other Model"),
                messages = listOf(ChatMessage(true, "Write a poem"), ChatMessage(false, "Roses are red")),
                sessionId = oldChat.session.id,
                chats = listOf(oldChat),
            ),
        )
        composeRule.onNodeWithTag("read_only_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_input").assertDoesNotExist()
        composeRule.onNodeWithText("Roses are red").assertIsDisplayed()
        captureEvidence("mobie-read-only-chat.png")
        composeRule.onNodeWithText("Get the model").performClick()
        composeRule.runOnIdle { assertEquals("example/other", gotModel) }
    }

    @Test
    fun homeWithoutModelsExplainsHowToStart() {
        setHome(MobieUiState())
        composeRule.onNodeWithText("Download a model to start chatting").assertIsDisplayed()
        captureEvidence("mobie-chat-home-empty.png")
        composeRule.onNodeWithText("Browse models").performClick()
        composeRule.runOnIdle { assertEquals(CatalogTab.DISCOVER, openedModels) }
    }

    @Test
    fun switchingChatsClearsTheDraft() {
        val state = mutableStateOf(loadedState())
        setHome { state.value }
        composeRule.onNodeWithTag("chat_input").performTextInput("Draft for the first chat")
        composeRule.runOnIdle {
            state.value = state.value.copy(sessionId = "different-chat", messages = emptyList())
        }
        composeRule.onNodeWithTag("chat_input").assertTextEquals("")
    }

    @Test
    fun failedModelLoadKeepsSavedMessagesReadable() {
        setHome(loadedState().copy(runtimeState = RuntimeState.ERROR, error = "Not enough free memory"))
        composeRule.onNodeWithText("Here is a plan").assertIsDisplayed()
        composeRule.onNodeWithText("Not enough free memory").assertIsDisplayed()
    }

    @Test
    fun savedChatOffersMemoryReclaimRetryWithoutHidingMessages() {
        setHome(loadedState().copy(
            runtimeState = RuntimeState.ERROR,
            error = "Free memory before loading",
            compatibility = CompatibilityResult(
                status = Compatibility.WARNING,
                reason = "Free memory before loading",
                estimatedRamBytes = 4_000_000_000L,
                memoryTier = MemoryAdmissionTier.FITS_AFTER_RECLAIM,
            ),
        ))
        composeRule.onNodeWithText("Here is a plan").assertIsDisplayed()
        composeRule.onNodeWithText("Load anyway").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, reclaimRetries) }
    }

    private fun setHome(state: MobieUiState) = setHome { state }

    private fun setHome(state: () -> MobieUiState) {
        composeRule.setContent {
            MobieTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ChatHome(
                        state = state(),
                        onSend = { _, _ -> true },
                        onStop = {},
                        onNewChat = { newChats++ },
                        onOpenThread = { openedThread = it },
                        onRenameChat = { thread, title -> renamed = thread to title },
                        onDeleteChat = { deleted = it },
                        onPickModel = { picked = it },
                        onOpenModels = { openedModels = it },
                        onGetModel = { gotModel = it },
                        onLoadAnyway = { reclaimRetries++ },
                    )
                }
            }
        }
    }

    private fun loadedState() = MobieUiState(
        selected = model,
        chatting = true,
        runtimeState = RuntimeState.READY,
        installedModels = listOf(InstalledModelEntry(model, "/private/test.litertlm"), secondEntry),
        chats = listOf(todayChat, oldChat),
        sessionId = todayChat.session.id,
        messages = listOf(ChatMessage(true, "Plan a trip"), ChatMessage(false, "Here is a plan")),
    )

    private val model = testModel("example/test-model", "Test Model")
    private val secondEntry = InstalledModelEntry(testModel("example/second", "Second Model"), "/private/second.litertlm")
    private val todayChat = ChatThread(
        "example/test-model",
        ChatHistorySession("a", "Trip ideas", System.currentTimeMillis(), listOf(HistoryMessage(true, "Plan a trip"))),
    )
    private val oldChat = ChatThread(
        "example/other",
        ChatHistorySession("b", "Old poem", 0L, listOf(HistoryMessage(true, "Write a poem")), modelTitle = "Other Model"),
    )

    private fun testModel(id: String, title: String) = AiModel(
        id = id,
        title = title,
        author = "example",
        description = "A small test model",
        artifacts = listOf(
            ModelArtifact(
                fileName = "test.litertlm",
                downloadUrl = "https://example.invalid/test.litertlm",
                sizeBytes = 1024L * 1024L * 1024L,
                format = ModelFormat.LITERT_LM,
            ),
        ),
    )

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        file.outputStream().use { output ->
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/$name"),
        ).use { it.readBytes() }
    }
}
