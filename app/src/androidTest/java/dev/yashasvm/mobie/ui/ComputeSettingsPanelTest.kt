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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.core.device.BackendAdmission
import dev.yashasvm.mobie.core.device.CpuCluster
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.device.DecodeMeasurement
import dev.yashasvm.mobie.core.device.DecodeRunSettings
import dev.yashasvm.mobie.core.device.DecodeSpeed
import dev.yashasvm.mobie.core.device.DecodeSpeedSource
import dev.yashasvm.mobie.core.device.GpuHardware
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.RuntimeComputeSettings
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.ui.theme.MobieTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.json.JSONObject
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
                        measurements = listOf(DecodeMeasurement(ComputeBackend.GPU, DecodeRunSettings(null, null),
                            DecodeSpeed(18.0, 22.0, DecodeSpeedSource.MEASURED, 2))),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Snapdragon 8 Gen 3 · Adreno 750 GPU · 8 CPU cores").assertIsDisplayed()
        composeRule.onNodeWithTag("backend_CPU").assertIsSelected()
        composeRule.onNodeWithText("estimate").assertIsDisplayed()
        composeRule.onNodeWithText("18–22 tokens/s").assertIsDisplayed()
        composeRule.onNodeWithText("measured on this phone · 2 recent runs").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(ComputeBackend.CPU, selected) }
        composeRule.onNodeWithTag("backend_GPU").performClick().assertIsSelected()
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
        composeRule.onNodeWithTag("decode_speed_GPU", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Speed unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("OpenCL driver was not found. Run GPU to measure support and speed.").assertIsDisplayed()
        composeRule.onNodeWithTag("backend_GPU").performClick()
        composeRule.runOnIdle { assertEquals(ComputeBackend.GPU, selected) }
        composeRule.onNodeWithText("Saved. Used next time you load a model.").performScrollTo().assertIsDisplayed()
    }

    @Test fun savedNonDefaultChoicesWithNothingLoadedNeverOfferAReload() {
        val saved = ComputeOptions(backend = ComputeBackend.GPU, maxOutputTokens = 128, prefillPrefaceOnInit = true)
        var applyCount = 0
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(saved.backend, saved.cpuThreads, {}, {}, hardware = hardware,
                        artifactSizeBytes = 344_671_744, options = saved,
                        needsApply = AdvancedComputePolicy.needsApply(saved, SamplerSettings(), null, null),
                        loaded = false, runtimeState = RuntimeState.IDLE, downloaded = true, onApply = { applyCount++ })
                }
            }
        }
        composeRule.onNodeWithTag("saved_settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Saved. Used next time you load a model.").assertIsDisplayed()
        composeRule.onNodeWithTag("reload_prompt").assertDoesNotExist()
        composeRule.onNodeWithTag("apply_compute").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, applyCount) }
    }

    @Test fun speedRowsNameMeasurementsFromOtherSettingsInsteadOfClaimingThisPhone() {
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(
                        backend = ComputeBackend.CPU, cpuThreads = 6,
                        onBackendChange = {}, onCpuThreadsChange = {},
                        hardware = hardware, artifactSizeBytes = 344_671_744,
                        measurements = listOf(DecodeMeasurement(ComputeBackend.CPU, DecodeRunSettings(2, null),
                            DecodeSpeed(7.0, 7.0, DecodeSpeedSource.MEASURED, 1))),
                        options = ComputeOptions(backend = ComputeBackend.CPU, cpuThreads = 6),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("decode_speed_CPU", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("measured at different settings · last run").assertIsDisplayed()
        composeRule.onNodeWithText("measured on this phone · last run").assertDoesNotExist()
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
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
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
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
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
                        measurements = listOf(DecodeMeasurement(ComputeBackend.GPU, DecodeRunSettings(null, null),
                            DecodeSpeed(30.0, 30.0, DecodeSpeedSource.MEASURED, 1))),
                    )
                }
            }
        }
        composeRule.onNodeWithText("estimate · lower confidence").assertIsDisplayed()
        composeRule.onNodeWithText("≈30 tokens/s").assertIsDisplayed()
        composeRule.onNodeWithText("measured on this phone · last run").assertIsDisplayed()
        captureUiFixture("compute-speed-lower-confidence-fixture.png")
    }

    @Test fun advancedStartsCollapsedAndUnsupportedControlsStayHidden() {
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(ComputeBackend.AUTO, null, {}, {}, hardware = hardware,
                        artifactSizeBytes = 344_671_744, capabilities = ComputeCapabilities(2_048, true, false, false))
                }
            }
        }
        composeRule.onNodeWithTag("advanced_controls").assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("advanced_threads").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_thinking").assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_speculative").assertDoesNotExist()
        composeRule.onNode(hasText("4096") and hasAnyAncestor(hasTestTag("advanced_context"))).assertDoesNotExist()
        composeRule.onNodeWithTag("advanced_context").performScrollTo().assertIsDisplayed()
        captureUiFixture("advanced-context-fixture.png")
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("advanced_controls").assertDoesNotExist()
        captureUiFixture("advanced-collapsed-fixture.png")
    }

    @Test fun supportedThinkingAndDrafterControlsAreVisibleWithoutUnmeasuredClaims() {
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(ComputeBackend.AUTO, null, {}, {}, hardware = hardware,
                        artifactSizeBytes = 344_671_744, capabilities = ComputeCapabilities(1_280, false, true, true))
                }
            }
        }
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("advanced_thinking").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_speculative").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("Experimental — not yet tested on this phone") and
            hasAnyAncestor(hasTestTag("advanced_speculative"))).assertIsDisplayed()
        composeRule.onNode(hasText("Experimental — not yet tested on this phone") and
            hasAnyAncestor(hasTestTag("advanced_preface"))).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activation_hint").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("FP16 was 7% slower than Auto in one S24 Ultra test.")
            .assertIsDisplayed()
        composeRule.onNode(hasText("1024") and hasAnyAncestor(hasTestTag("advanced_context"))).assertDoesNotExist()
        composeRule.onNode(hasText("1280") and hasAnyAncestor(hasTestTag("advanced_context"))).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("advanced_activation").performScrollTo().assertIsDisplayed()
        captureUiFixture("advanced-experiments-fixture.png")
    }

    @Test fun backendChangesPromptForExplicitReloadAndGenerationBlocksApply() {
        val original = ComputeOptions(backend = ComputeBackend.CPU)
        var options by mutableStateOf(original)
        var runtime by mutableStateOf(RuntimeState.READY)
        var applyCount = 0
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(options.backend, options.cpuThreads, { options = options.copy(backend = it) }, {},
                        hardware = hardware, artifactSizeBytes = 344_671_744, options = options,
                        activeCompute = ActiveCompute(ComputeBackend.CPU, 4, "GPU could not load this model. Using CPU."),
                        needsApply = options != original, loaded = true, runtimeState = runtime, downloaded = true,
                        onOptionsChange = { options = it }, onApply = { applyCount++ })
                }
            }
        }
        composeRule.onNodeWithTag("compute_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("reload_prompt").assertDoesNotExist()
        composeRule.onNodeWithTag("backend_GPU").performClick()
        composeRule.onNodeWithTag("reload_prompt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("apply_compute").performScrollTo().assertIsEnabled()
        composeRule.runOnIdle { assertEquals(0, applyCount); runtime = RuntimeState.GENERATING }
        composeRule.onNodeWithTag("apply_compute").assertIsNotEnabled()
        composeRule.onNodeWithText("Wait for the reply", substring = true).assertIsDisplayed()
        captureUiFixture("advanced-reload-generating-fixture.png")
        composeRule.runOnIdle { runtime = RuntimeState.READY }
        composeRule.onNodeWithTag("apply_compute").performClick()
        composeRule.runOnIdle { assertEquals(1, applyCount) }
    }

    @Test fun invalidSamplerFieldsBlockApplyAndResetRestoresRecommendedValues() {
        var options by mutableStateOf(ComputeOptions(ComputeBackend.GPU, 4, maxOutputTokens = 128,
            activationType = ActivationType.FP16, prefillPrefaceOnInit = true))
        var sampler by mutableStateOf(SamplerSettings(0.0, 1, 1.0, 77))
        var applyCount = 0
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(options.backend, options.cpuThreads, { options = options.copy(backend = it) }, {},
                        hardware = hardware, artifactSizeBytes = 344_671_744, options = options, sampler = sampler,
                        needsApply = AdvancedComputePolicy.needsApply(options, sampler, ComputeOptions(), SamplerSettings()),
                        loaded = true,
                        runtimeState = RuntimeState.READY, downloaded = true, onOptionsChange = { options = it },
                        onSamplerChange = { sampler = it; null }, onApply = { applyCount++ },
                        onReset = { options = ComputeOptions(); sampler = SamplerSettings() })
                }
            }
        }
        composeRule.onNodeWithTag("advanced_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("sampler_top_k").performScrollTo().performTextReplacement("65")
        composeRule.onNodeWithTag("apply_compute").performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, sampler.topK); assertEquals(0, applyCount) }
        composeRule.onNodeWithTag("reset_compute").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(ComputeOptions(), options); assertEquals(SamplerSettings(), sampler) }
        composeRule.onNodeWithTag("apply_compute").assertDoesNotExist()
        composeRule.onNodeWithTag("sampler_top_k").performScrollTo().assertIsDisplayed()
        captureUiFixture("advanced-sampler-reset-fixture.png")
    }

    @Test fun advancedPreferencesSurviveRecreationAndSanitizeOnRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RuntimeComputeSettings(context)
        val keys = AdvancedComputeSettingsCodec.encode(ComputeOptions()).keys + setOf("selected_backend", "cpu_threads")
        val preferences = settings.preferences
        val original = preferences.all.filterKeys { it in keys }
        try {
            val chosen = ComputeOptions(ComputeBackend.GPU, 8, contextTokens = 2_048, maxOutputTokens = 128,
                thinkingEnabled = false, thinkingTokenBudget = 64, activationType = ActivationType.FP32,
                speculativeDecoding = true, prefillPrefaceOnInit = true)
            settings.writeAdvancedOptions(chosen, 8)
            assertEquals(chosen.copy(cpuThreads = 4), RuntimeComputeSettings(context).readAdvancedOptions(4))
            preferences.edit().putInt("cpu_threads", 99).putString("max_output_tokens", "9999")
                .putString("activation_type", "INT8").commit()
            val restored = RuntimeComputeSettings(context).readAdvancedOptions(1)
            assertEquals(1, restored.cpuThreads)
            assertEquals(1_024, restored.maxOutputTokens)
            assertEquals(ActivationType.AUTO, restored.activationType)
            assertEquals(1, preferences.getInt("cpu_threads", 0))
            assertEquals("1024", preferences.getString("max_output_tokens", null))
        } finally {
            preferences.edit().apply {
                keys.forEach { remove(it) }
                original.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
        }
    }

    @Test fun eachBackendRowCarriesItsOwnRamAndAutoNamesItsPick() {
        val gib = 1024L * 1024L * 1024L
        var selected by mutableStateOf(ComputeBackend.AUTO)
        val admissions = mapOf(
            ComputeBackend.AUTO to BackendAdmission(ComputeBackend.GPU, CompatibilityResult(Compatibility.COMPATIBLE, "Fits in free memory.", 3 * gib)),
            ComputeBackend.CPU to BackendAdmission(ComputeBackend.CPU, CompatibilityResult(Compatibility.COMPATIBLE, "", gib)),
            ComputeBackend.GPU to BackendAdmission(ComputeBackend.GPU, CompatibilityResult(Compatibility.WARNING, "", 3 * gib)),
        )
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(selected, null, { selected = it }, {}, hardware = hardware,
                        artifactSizeBytes = 344_671_744, backendAdmissions = admissions)
                }
            }
        }
        composeRule.onNodeWithTag("backend_AUTO").assertIsSelected()
        composeRule.onNode(hasText("Uses GPU · needs ~3.0 GB RAM") and hasAnyAncestor(hasTestTag("auto_admission")), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("ram_CPU", useUnmergedTree = true).assertTextEquals("needs ~1.0 GB RAM · Runs here")
        composeRule.onNodeWithTag("ram_GPU", useUnmergedTree = true).assertTextEquals("needs ~3.0 GB RAM · Free memory first")
        // The whole row is the touch target, not just the option name.
        composeRule.onNodeWithTag("ram_CPU", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("backend_CPU").assertIsSelected()
        composeRule.onNodeWithTag("backend_AUTO").assertIsNotSelected()
        composeRule.runOnIdle { assertEquals(ComputeBackend.CPU, selected) }
        captureUiFixture("compute-rows-fixture.png")
    }

    @Test fun loadedSettingsWithoutChangesSayTheyAreInUse() {
        composeRule.setContent {
            MobieTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ComputeSettingsPanel(ComputeBackend.GPU, null, {}, {}, hardware = hardware, artifactSizeBytes = 344_671_744,
                        activeCompute = ActiveCompute(ComputeBackend.GPU), needsApply = false, loaded = true,
                        runtimeState = RuntimeState.READY, downloaded = true)
                }
            }
        }
        composeRule.onNodeWithText("These settings are in use.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("running · GPU").assertIsDisplayed()
        composeRule.onNodeWithTag("apply_compute").assertDoesNotExist()
    }

    @Test fun exportedRunUsesAppliedSamplerRatherThanPendingEdits() {
        val applied = SamplerSettings(0.0, 1, 1.0, 77)
        val state = MobieUiState(sampler = SamplerSettings(2.0, 64, 0.5, 99), appliedSampler = applied)
        val exported = JSONObject(runJson(state)).getJSONObject("sampler")
        assertEquals(0.0, exported.getDouble("temperature"), 0.0)
        assertEquals(1, exported.getInt("topK"))
        assertEquals(1.0, exported.getDouble("topP"), 0.0)
        assertEquals(77, exported.getInt("seed"))
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
