package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.device.CpuCluster
import dev.yashasvm.mobie.core.device.DecodeSpeed
import dev.yashasvm.mobie.core.device.DecodeSpeedSource
import dev.yashasvm.mobie.core.device.GpuHardware
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComputeSettingsPanelTest {
    @get:Rule val composeRule = createComposeRule()

    private val hardware = HardwareProfile("Qualcomm", "SM8650", 8,
        listOf(CpuCluster(listOf(0, 1), 2_200_000), CpuCluster((2..6).toList(), 3_200_000), CpuCluster(listOf(7), 3_390_000)),
        GpuHardware("Adreno (TM) 750", "Qualcomm"), true, 8L * 1024 * 1024 * 1024)

    @Test fun pickerShowsEstimatedAndMeasuredSpeedsAndKeepsChoiceUserControlled() {
        var selected by mutableStateOf(ComputeBackend.CPU)
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = selected, cpuThreads = null,
                        onBackendChange = { selected = it }, onCpuThreadsChange = {},
                        hardware = hardware, artifactSizeBytes = 2_000_000_000L,
                        measuredSpeeds = mapOf(ComputeBackend.GPU to DecodeSpeed(18.0, 22.0, DecodeSpeedSource.MEASURED, 2)),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Snapdragon 8 Gen 3 · Adreno 750 GPU · 8 CPU cores").assertIsDisplayed()
        composeRule.onNodeWithText("cpu").assertIsSelected()
        composeRule.onNodeWithText("estimate").assertIsDisplayed()
        composeRule.onNodeWithText("18–22 tokens/s").assertIsDisplayed()
        composeRule.onNodeWithText("measured on this phone · 2 recent runs").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(ComputeBackend.CPU, selected) }
        composeRule.onNodeWithText("gpu").performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(ComputeBackend.GPU, selected) }
        captureUiFixture("compute-speed-panel-fixture.png")
    }

    @Test fun missingDriverShowsNoFakeGpuSpeedAndLeavesPickerAvailable() {
        var selected = ComputeBackend.CPU
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = selected, cpuThreads = null,
                        onBackendChange = { selected = it }, onCpuThreadsChange = {},
                        hardware = hardware.copy(openClLibraryPresent = false), artifactSizeBytes = 2_000_000_000L,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("decode_speed_GPU").assertIsDisplayed()
        composeRule.onNodeWithText("Speed unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("OpenCL driver was not found. Run GPU to measure support and speed.").assertIsDisplayed()
        composeRule.onNodeWithText("gpu").performClick()
        composeRule.runOnIdle { assertEquals(ComputeBackend.GPU, selected) }
        composeRule.onNodeWithText("Changes apply", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun fourCorePickerClampsRestoredSelectionAndNeverOffersMoreWorkers() {
        var chosen: Int? = null
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = ComputeBackend.CPU, cpuThreads = 8,
                        onBackendChange = {}, onCpuThreadsChange = { chosen = it },
                        hardware = hardware.copy(cpuCoreCount = 4), artifactSizeBytes = 344_671_744,
                    )
                }
            }
        }
        composeRule.onNodeWithText("4").performScrollTo().assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals(4, chosen) }
        for (count in 5..8) composeRule.onNodeWithText(count.toString()).assertDoesNotExist()
    }

    @Test fun oneCorePickerOnlyOffersOneWorkerAndAutomaticSelection() {
        var chosen: Int? = 8
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = ComputeBackend.CPU, cpuThreads = 8,
                        onBackendChange = {}, onCpuThreadsChange = { chosen = it },
                        hardware = hardware.copy(cpuCoreCount = 1), artifactSizeBytes = 344_671_744,
                    )
                }
            }
        }
        composeRule.onNodeWithText("1").performScrollTo().assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals(1, chosen) }
        for (count in 2..8) composeRule.onNodeWithText(count.toString()).assertDoesNotExist()
    }

    @Test fun deniedClocksShowLowerConfidenceEstimateAndMeasuredStatsOverrideIt() {
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = ComputeBackend.CPU, cpuThreads = null,
                        onBackendChange = {}, onCpuThreadsChange = {},
                        hardware = hardware.copy(cpuClusters = emptyList()), artifactSizeBytes = 344_671_744,
                        measuredSpeeds = mapOf(ComputeBackend.GPU to DecodeSpeed(30.0, 30.0, DecodeSpeedSource.MEASURED, 1)),
                    )
                }
            }
        }
        composeRule.onNodeWithText("estimate · lower confidence").assertIsDisplayed()
        composeRule.onNodeWithText("≈30 tokens/s").assertIsDisplayed()
        composeRule.onNodeWithText("measured on this phone · last run").assertIsDisplayed()
        captureUiFixture("compute-speed-lower-confidence-fixture.png")
    }

    private fun captureUiFixture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(checkNotNull(context.getExternalFilesDir(null)), name)
        file.outputStream().use { output ->
            assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
