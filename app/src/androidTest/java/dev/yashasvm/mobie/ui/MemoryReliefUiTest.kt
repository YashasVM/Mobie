package dev.yashasvm.mobie.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MemoryReliefUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val artifact = ModelArtifact("small.litertlm", "https://example.invalid/small.litertlm", GIB,
        format = ModelFormat.LITERT_LM)
    private val model = AiModel("litert-community/memory-test", "Memory test model", "litert-community",
        "Memory pre-flight verification", artifacts = listOf(artifact))
    private val resolver = CompatibilityResolver()
    private val shortMemory = DeviceProfile(8 * GIB, 512 * MIB, 20 * GIB, listOf("x86_64"), 35,
        lowMemoryThresholdBytes = 256 * MIB, availableAfterReclaimBytes = 2 * GIB)

    @Test
    fun explainsUserActionBeforeReleasingMemory() {
        var cleanupRequested = false
        composeRule.setContent {
            MobieTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ModelScreen(
                        state = state(shortMemory), onDownload = {}, onRun = {},
                        onFreeMemory = { cleanupRequested = true },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("free_up_memory").performScrollTo().assertIsDisplayed()
        capture("memory-warning.png")
        composeRule.onNodeWithTag("free_up_memory").performClick()
        composeRule.onNodeWithText("Android doesn't let Mobie close other apps, so close apps yourself in Recents or system settings, then return here to check memory.")
            .assertIsDisplayed()
        capture("memory-explanation.png")
        composeRule.runOnIdle { assertTrue(!cleanupRequested) }
        composeRule.onNodeWithText("Open system screen").performClick()
        composeRule.runOnIdle { assertTrue(cleanupRequested) }
    }

    @Test
    fun resumeRefreshMakesLoadProceedWithoutTheMemoryWarning() {
        val liveState = mutableStateOf(state(shortMemory))
        var resumeCount = 0
        var loadedWithoutOverride = false
        composeRule.setContent {
            MobieTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ModelScreen(
                        state = liveState.value,
                        onDownload = {},
                        onRun = { loadedWithoutOverride = !it },
                        onMemoryRefresh = {
                            resumeCount++
                            if (resumeCount > 1) {
                                liveState.value = state(shortMemory.copy(availableRamBytes = 3 * GIB,
                                    availableAfterReclaimBytes = 3 * GIB))
                            }
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("free_up_memory").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, resumeCount) }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("free_up_memory").assertDoesNotExist()
        composeRule.onNodeWithTag("memory_reclaim_estimate")
            .assertExists()
        composeRule.onNodeWithText("Android may reclaim about 0 MB more RAM; this is an estimate.").assertExists()
        composeRule.onNodeWithText("Model needs").performScrollTo().assertIsDisplayed()
        capture("memory-after-return.png")
        composeRule.onNodeWithTag("model_primary_action").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, resumeCount)
            assertTrue(loadedWithoutOverride)
        }
    }

    @Test
    fun systemNavigationOpensTheEmulatorsAvailableSettingsFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        composeRule.setContent {
            MobieTheme { MemoryReliefControls(MemoryReliefDecision.from(resolver.resolve(artifact, shortMemory), shortMemory),
                busy = false, onFreeMemory = { it() }) }
        }
        composeRule.onNodeWithTag("free_up_memory").performClick()
        composeRule.onNodeWithText("Open system screen").performClick()
        instrumentation.waitForIdleSync()
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        var resumedActivity = ""
        var settingsVisible = false
        var accessibilityRoot = "not queried"
        while (!settingsVisible && android.os.SystemClock.elapsedRealtime() < deadline) {
            resumedActivity = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities"),
            ).bufferedReader().use { reader ->
                // Drain the command before closing its pipe; early close floods system_server with EPIPE.
                reader.readText().lineSequence().firstOrNull {
                    it.contains("topResumedActivity=") || it.contains("mResumedActivity:") ||
                        it.contains("mResumedActivity=")
                }.orEmpty()
            }
            // Window transitions can leave a cached framework root after Settings has rendered.
            if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
            val root = automation.rootInActiveWindow
            accessibilityRoot = "package=${root?.packageName}, children=${root?.childCount}, visible=${root?.isVisibleToUser}"
            settingsVisible = resumedActivity.contains(" com.android.settings/") &&
                root?.let(::hasVisibleSettingsText) == true
            if (!settingsVisible) android.os.SystemClock.sleep(100)
        }
        assertTrue("A Settings activity must be resumed on this emulator: $resumedActivity",
            resumedActivity.contains(" com.android.settings/"))
        assertTrue("The resumed Settings activity must display content: $accessibilityRoot", settingsVisible)
        capture("memory-system-apps.png")
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun hasVisibleSettingsText(node: AccessibilityNodeInfo): Boolean {
        if (node.packageName?.toString() == "com.android.settings" &&
            node.isVisibleToUser && !node.text.isNullOrBlank()) return true
        return (0 until node.childCount).any { index ->
            node.getChild(index)?.let(::hasVisibleSettingsText) == true
        }
    }

    private fun state(device: DeviceProfile) = MobieUiState(
        selected = model, selectedArtifact = artifact, device = device,
        compatibility = resolver.resolve(artifact, device), downloadedPath = "/private/test.litertlm",
    )

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val output = File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name)
        output.outputStream().use {
            assertTrue(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("cp ${output.absolutePath} /sdcard/$name"),
        ).use { it.readBytes() }
    }

    private companion object {
        const val MIB = 1024L * 1024
        const val GIB = 1024L * MIB
    }
}
