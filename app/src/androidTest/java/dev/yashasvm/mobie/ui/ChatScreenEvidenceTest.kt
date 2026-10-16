package dev.yashasvm.mobie.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.attachment.DocumentAttachment
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.MobieTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Renders the chat screen, attach menu, and model switcher in dark and light with sample state,
 * and saves screenshots to /sdcard for PR evidence. Nothing here loads a model.
 */
class ChatScreenEvidenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatScreenInBothThemes() = forEachTheme { theme ->
        capture("mobie-chat-$theme.png")
    }

    @Test
    fun attachMenuInBothThemes() = forEachTheme { theme ->
        composeRule.onNodeWithContentDescription("Add to message").performClick()
        composeRule.onNodeWithText("File").assertExists()
        capture("mobie-attach-menu-$theme.png")
        // Close the menu before the next theme without opening the system file picker.
        Espresso.pressBack()
        composeRule.onNodeWithText("File").assertDoesNotExist()
    }

    @Test
    fun modelSwitcherInBothThemes() = forEachTheme(showSwitcher = true) { theme ->
        composeRule.onNodeWithText("switch model").assertExists()
        capture("mobie-model-switcher-$theme.png")
    }

    private fun forEachTheme(showSwitcher: Boolean = false, block: (String) -> Unit) {
        var dark by mutableStateOf(true)
        composeRule.setContent {
            MobieTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ChatScreen(state = sampleState(), onSend = { _, _ -> true })
                    }
                    if (showSwitcher) {
                        ModelSwitcherSheet(
                            installed = installed,
                            activeId = installed.first().model.id,
                            onPick = {},
                            onGetMore = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
        block("dark")
        dark = false
        composeRule.waitForIdle()
        block("light")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        // Let entrance animations settle so the screenshot shows the resting layout.
        Thread.sleep(800)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        file.outputStream().use { output ->
            assertTrue(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/$name"),
        ).use { it.readBytes() }
    }

    private val model = AiModel(
        id = "litert-community/Gemma3-1B-IT",
        title = "Gemma 3 1B IT",
        author = "litert-community",
        description = "",
        type = ModelType.VISION,
        artifacts = listOf(
            ModelArtifact(
                fileName = "gemma3-1b-it-int4.litertlm",
                downloadUrl = "https://example.invalid/gemma3-1b-it-int4.litertlm",
                sizeBytes = 584L * 1024 * 1024,
                format = ModelFormat.LITERT_LM,
                quantization = "INT4",
            ),
        ),
    )

    private val installed = listOf(
        InstalledModelEntry(model, "/private/gemma.litertlm"),
        InstalledModelEntry(
            model.copy(id = "litert-community/Qwen3-0.6B", title = "Qwen3 0.6B", type = ModelType.TEXT_GENERATION),
            "/private/qwen.litertlm",
        ),
    )

    private fun sampleState() = MobieUiState(
        selected = model,
        selectedArtifact = model.artifacts.first(),
        runtimeState = RuntimeState.READY,
        imageInputReady = true,
        chatting = true,
        installedModels = installed,
        activeInstalledId = model.id,
        messages = listOf(
            ChatMessage(
                fromUser = true,
                text = "Summarize these notes in three bullets.",
                attachments = listOf(
                    DocumentAttachment("meeting-notes.md", "text/markdown", 3_200, "# Notes", truncated = false),
                ),
            ),
            ChatMessage(
                fromUser = false,
                text = "- The launch moves to **Friday**.\n- Design review is on Wednesday.\n- Docs need one more pass.",
                stats = InferenceStats(tokensPerSecond = 18.4, ramBytes = 0, timeToFirstTokenMs = 900, decodeTokenCount = 42),
            ),
        ),
        pendingAttachments = listOf(
            DocumentAttachment("follow-up-questions.txt", "text/plain", 1_024, "Q1", truncated = false),
        ),
    )
}
