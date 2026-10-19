package dev.yashasvm.mobie.ui

import android.graphics.Bitmap
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
        composeRule.setContent {
            MobieTheme { MemoryReliefControls(MemoryReliefDecision.from(resolver.resolve(artifact, shortMemory), shortMemory),
                busy = false, onFreeMemory = { it() }) }
        }
        composeRule.onNodeWithTag("free_up_memory").performClick()
        composeRule.onNodeWithText("Open system screen").performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        var resumedActivity = ""
        var settingsVisible = false
        while (!settingsVisible && android.os.SystemClock.elapsedRealtime() < deadline) {
            resumedActivity = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities"),
            ).bufferedReader().use { reader ->
                reader.readLines().firstOrNull {
                    it.contains("topResumedActivity=") || it.contains("mResumedActivity:") ||
                        it.contains("mResumedActivity=")
                }.orEmpty()
            }
            val root = instrumentation.uiAutomation.rootInActiveWindow
            settingsVisible = resumedActivity.contains(" com.android.settings/") &&
                root?.packageName?.toString() == "com.android.settings" && hasVisibleText(root)
            if (!settingsVisible) android.os.SystemClock.sleep(100)
        }
        try {
            recordSettingsDiagnostics(resumedActivity)
            assertTrue("A Settings activity must be resumed on this emulator: $resumedActivity",
                resumedActivity.contains(" com.android.settings/"))
            assertTrue("The resumed Settings activity must display content", settingsVisible)
        } finally {
            runCatching {
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }.onFailure { android.util.Log.w("MobieSettingsDiag", "Could not leave Settings", it) }
        }
    }

    private fun recordSettingsDiagnostics(resumedActivity: String) {
        runCatching { capture("memory-system-apps.png") }
            .onFailure { android.util.Log.w("MobieSettingsDiag", "Could not capture Settings screenshot", it) }
        runCatching {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            var nodes = 0
            val hierarchy = buildString {
                appendLine(resumedActivity)
                fun describe(node: AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 24 || nodes++ >= 500) return
                    val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                    appendLine("${"  ".repeat(depth)}package=${node.packageName} class=${node.className} " +
                        "visible=${node.isVisibleToUser} bounds=$bounds text=${node.text?.take(160)} " +
                        "description=${node.contentDescription?.take(160)}")
                    for (index in 0 until node.childCount) describe(node.getChild(index), depth + 1)
                }
                appendLine("ACTIVE WINDOW")
                describe(automation.rootInActiveWindow, 0)
                automation.windows.forEach { window ->
                    appendLine("WINDOW id=${window.id} type=${window.type} " +
                        "active=${window.isActive} focused=${window.isFocused}")
                    describe(window.root, 0)
                }
            }
            android.util.Log.i("MobieSettingsDiag", hierarchy)
            val output = File(checkNotNull(InstrumentationRegistry.getInstrumentation()
                .targetContext.getExternalFilesDir(null)), "memory-system-apps-hierarchy.txt")
            output.writeText(hierarchy)
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "cp ${output.absolutePath} /sdcard/${output.name}",
            )).use { it.readBytes() }
        }.onFailure { android.util.Log.w("MobieSettingsDiag", "Could not capture Settings hierarchy", it) }
    }

    private fun hasVisibleText(node: AccessibilityNodeInfo): Boolean {
        if (node.isVisibleToUser && !node.text.isNullOrBlank()) return true
        return (0 until node.childCount).any { index ->
            node.getChild(index)?.let(::hasVisibleText) == true
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
