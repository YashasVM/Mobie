package dev.yashasvm.mobie.ui

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import dev.yashasvm.mobie.core.model.*
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Rule
import org.junit.Test

class ChatDraftNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val screen = mutableStateOf(AppScreen.CHAT)
    private val model = AiModel("fixture/draft", "Draft fixture", "fixture", "Local UI fixture", type = ModelType.VISION,
        artifacts = listOf(ModelArtifact("draft.litertlm", "", 1024, format = ModelFormat.LITERT_LM)))
    private val state = mutableStateOf(MobieUiState(selected = model, chatting = true, sessionId = "draft-session",
        runtimeState = RuntimeState.READY, imageInputReady = true))

    @Test fun promptSurvivesCatalogNavigationAndRotationWithoutLeakingToAnotherChat() {
        val restoration = showHome()
        compose.onNodeWithTag("chat_input").performTextInput("A draft to keep")
        visitModelsAndReturn()
        assertDraft("A draft to keep")
        restoration.emulateSavedInstanceStateRestore()
        assertDraft("A draft to keep")
        compose.runOnIdle { state.value = state.value.copy(sessionId = "another-chat") }
        assertDraft("")
    }

    @Test fun photoSurvivesNavigationAndReloadBeforeItsCapabilityIsKnownAgain() {
        val restoration = showHome()
        compose.onNodeWithContentDescription("Add to message").performClick()
        compose.onNodeWithText("Photo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Remove image").fetchSemanticsNodes().isNotEmpty() }
        visitModelsAndReturn(loading = true)
        compose.onNodeWithContentDescription("Remove image").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(runtimeState = RuntimeState.READY, imageInputReady = true) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Remove image").assertIsDisplayed().performClick()
    }

    private fun showHome(): StateRestorationTester {
        val picker = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Uri.parse("content://dev.yashasvm.mobie.test.documents/photo"))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = picker }
        return StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                MobieTheme {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                        AppScreenHost(screen.value) { current ->
                            if (current == AppScreen.CHAT) ChatHome(
                                state = state.value, onSend = { _, _ -> true }, onStop = {}, onNewChat = {},
                                onOpenThread = {}, onRenameChat = { _, _ -> }, onDeleteChat = {}, onPickModel = {},
                                onOpenModels = { screen.value = AppScreen.CATALOG }, onGetModel = {},
                            ) else Button(onClick = { screen.value = AppScreen.CHAT }) { Text("Return to chat") }
                        }
                    }
                }
            }
        }
    }

    private fun visitModelsAndReturn(loading: Boolean = false) {
        compose.onNodeWithTag("open_chats").performClick()
        compose.onNodeWithTag("drawer_models").performClick()
        compose.onNodeWithText("Return to chat").assertIsDisplayed()
        if (loading) compose.runOnIdle { state.value = state.value.copy(runtimeState = RuntimeState.LOADING, imageInputReady = false) }
        compose.onNodeWithText("Return to chat").performClick()
    }

    private fun assertDraft(value: String) {
        compose.onNodeWithTag("chat_input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    }
}
