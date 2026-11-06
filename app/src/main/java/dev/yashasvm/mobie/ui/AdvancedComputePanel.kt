package dev.yashasvm.mobie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.ui.theme.Mobie

private const val EXPERIMENT_WARNING = "Experimental. Not yet tested on this phone."

/** The one phone and model where activation was measured. Do not extend this claim to other phones or models. */
private const val FP16_HINT = "FP16 was 7% slower than Auto in one S24 Ultra test."

/**
 * Settings are grouped by memory and processing, reply generation, and experiments. A control is
 * shown only when this artifact's metadata says it applies.
 */
@Composable
internal fun AdvancedComputePanel(
    options: ComputeOptions,
    sampler: SamplerSettings,
    coreCount: Int,
    capabilities: ComputeCapabilities?,
    onOptionsChange: (ComputeOptions) -> Unit,
    onSamplerChange: (SamplerSettings) -> String?,
    onReset: () -> Unit,
    onValidityChange: (Boolean) -> Unit = {},
) {
    var resetVersion by remember { mutableIntStateOf(0) }
    val invalidFields = remember { mutableStateMapOf<String, Boolean>() }
    val validate: (String, Boolean) -> Unit = { tag, valid ->
        invalidFields[tag] = !valid
        onValidityChange(invalidFields.values.none { it })
    }
    // Edits change the saved choice; selected values show what this artifact would actually run.
    val effective = AdvancedComputePolicy.forArtifact(options, coreCount, capabilities)
    key(resetVersion) {
    Column(Modifier.testTag("advanced_controls"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionLabel("Memory and processing")
        AdvancedField("CPU threads", "CPU only. Auto is recommended.", "advanced_threads") {
            val selected = CpuThreadPolicy.clamp(options.cpuThreads, coreCount)
            AdvancedChoices(listOf<Int?>(null) + (1..CpuThreadPolicy.limit(coreCount)), selected, { it?.toString() ?: "Auto" }) {
                onOptionsChange(options.copy(cpuThreads = it))
            }
        }
        AdvancedField("Context size", "Conversation memory. Smaller uses less RAM.", "advanced_context") {
            if (capabilities == null) {
                Hint("Run this model once to read its context limits.")
            } else {
                AdvancedChoices(listOf<Int?>(null) + capabilities.contextChoices(), effective.contextTokens, { it?.toString() ?: "Auto" }) {
                    onOptionsChange(options.copy(contextTokens = it))
                }
                Hint("This model allows up to ${capabilities.maxContextTokens} tokens${if (!capabilities.dynamicContext) " · fixed size" else ""}")
            }
        }
        AdvancedField("Activation type", "Changes working precision, not the model's weight quantization.", "advanced_activation",
            experimental = true) {
            AdvancedChoices(ActivationType.entries, options.activationType, { if (it == ActivationType.AUTO) "Auto" else it.name }) {
                onOptionsChange(options.copy(activationType = it))
            }
            Hint(FP16_HINT, Modifier.testTag("activation_hint"))
        }
        SectionLabel("Reply generation")
        AdvancedField("Reply length", "Maximum tokens for reasoning and the visible reply together. A token is roughly part of a word.", "advanced_output") {
            NumberOption(options.maxOutputTokens.toString(), "1–1024", "output_tokens", validate) { text ->
                text.toIntOrNull()?.takeIf { it in 1..1_024 }?.let { onOptionsChange(options.copy(maxOutputTokens = it)) }
                    ?: return@NumberOption "Enter a whole number from 1 to 1024."
                null
            }
        }
        if (capabilities?.supportsThinking == true) {
            AdvancedField("Thinking", "Reasoning uses the same output budget as the final answer.", "advanced_thinking") {
                AdvancedChoices(listOf<Boolean?>(null, false, true), effective.thinkingEnabled,
                    { when (it) { null -> "Model default"; true -> "On"; false -> "Off" } }) {
                    onOptionsChange(options.copy(thinkingEnabled = it))
                }
                if (effective.thinkingEnabled == true) {
                    Hint("Thinking budget: limits reasoning tokens. No limit keeps only the output cap above.")
                    AdvancedChoices(listOf<Int?>(null, 0, 64, 128, 256, 512, 1_024), effective.thinkingTokenBudget, { it?.toString() ?: "No limit" }) {
                        onOptionsChange(options.copy(thinkingTokenBudget = it))
                    }
                } else {
                    Hint("Choose On to set a thinking budget.")
                }
            }
        }
        AdvancedField("Sampler", "Controls how varied replies are. Keep it the same when comparing speeds.", "advanced_sampler") {
            NumberOption(sampler.temperature.toString(), "Temperature · 0–2", "sampler_temperature", validate,
                hint = "0 picks the most likely token; higher values add variation.") { text ->
                val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2.0 }
                    ?: return@NumberOption "Enter a number from 0 to 2."
                onSamplerChange(sampler.copy(temperature = value))
            }
            NumberOption(sampler.topK.toString(), "Top-k · 1–64", "sampler_top_k", validate,
                hint = "How many candidate tokens are considered.") { text ->
                val value = text.toIntOrNull()?.takeIf { it in 1..64 } ?: return@NumberOption "Enter a whole number from 1 to 64."
                onSamplerChange(sampler.copy(topK = value))
            }
            NumberOption(sampler.topP.toString(), "Top-p · above 0, up to 1", "sampler_top_p", validate,
                hint = "Keeps candidates within this share of total probability.") { text ->
                val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 1 }
                    ?: return@NumberOption "Enter a number above 0 and no greater than 1."
                onSamplerChange(sampler.copy(topP = value))
            }
            NumberOption(sampler.seed.toString(), "Seed · whole number", "sampler_seed", validate,
                hint = "Starting value for random choices; repeats also depend on the runtime.") { text ->
                val value = text.toIntOrNull() ?: return@NumberOption "Enter a whole number within the 32-bit range."
                onSamplerChange(sampler.copy(seed = value))
            }
        }
        SectionLabel("Experimental")
        if (capabilities?.supportsSpeculativeDecoding == true) {
            AdvancedField("Speculative decoding", "Uses the model's built-in draft model to guess tokens ahead; may need extra memory.",
                "advanced_speculative", experimental = true) {
                AdvancedChoices(listOf<Boolean?>(null, false, true), effective.speculativeDecoding,
                    { when (it) { null -> "Model default"; true -> "On"; false -> "Off" } }) {
                    onOptionsChange(options.copy(speculativeDecoding = it))
                }
            }
        }
        AdvancedField("Prepare past messages early", "When a saved chat reopens, read its history while loading instead of on your first message. " +
            "This moves the wait; it does not remove it.", "advanced_preface", experimental = true) {
            AdvancedChoices(listOf(false, true), options.prefillPrefaceOnInit, { if (it) "On" else "Off" }) {
                onOptionsChange(options.copy(prefillPrefaceOnInit = it))
            }
        }
        if (capabilities == null) Hint("Thinking and draft-model controls appear after this model has been loaded once.")
        Hairline()
        Hint("Speed estimates use model size and assumed memory bandwidth. Heat, context and CPU threads affect speed. Send a message to measure it.")
        TermButton("Reset to recommended", onClick = { invalidFields.clear(); onValidityChange(true); resetVersion++; onReset() }, style = TermButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("reset_compute"))
    }
    }
}

@Composable
private fun AdvancedField(
    title: String,
    explanation: String,
    tag: String,
    experimental: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (experimental) Text(EXPERIMENT_WARNING, style = MaterialTheme.typography.bodySmall, color = Mobie.signals.caution)
            Hint(explanation)
        }
        content()
    }
}

/** Secondary copy under a control. Always dim so it never competes with the choice itself. */
@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> AdvancedChoices(choices: List<T>, selected: T, label: (T) -> String, onChange: (T) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { value -> FlagChoice(label(value), selected == value, { onChange(value) }) }
    }
}

@Composable
private fun NumberOption(
    value: String,
    label: String,
    tag: String,
    onValidityChanged: (String, Boolean) -> Unit,
    hint: String? = null,
    onChange: (String) -> String?,
) {
    var text by remember(value) { mutableStateOf(value) }
    var error by remember(value) { mutableStateOf<String?>(null) }
    val supporting = error ?: hint
    OutlinedTextField(text, { newValue -> text = newValue; error = onChange(newValue); onValidityChanged(tag, error == null) },
        label = { Text(label) }, singleLine = true, shape = RectangleShape, isError = error != null,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        supportingText = supporting?.let { message -> { Text(message) } }, modifier = Modifier.fillMaxWidth().testTag(tag))
}

/**
 * The chat's route to the loaded model's run settings: the same [ComputeSettingsPanel] as the model
 * page, in a bottom sheet titled with the model so it is clear which engine the choices apply to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComputeSettingsSheet(
    state: MobieUiState,
    onOptionsChange: (ComputeOptions) -> Unit,
    onSamplerChange: (SamplerSettings) -> String?,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onReplySpeedChange: (Int?) -> Unit = {},
) {
    val resolver = remember { CompatibilityResolver() }
    val backendAdmissions = remember(state.device, state.selectedArtifact, state.computeOptions, state.computeCapabilities, state.selected) {
        AdvancedComputePolicy.backendAdmissions(state.device, state.selectedArtifact, state.selected?.supportsVision == true,
            state.computeOptions, state.computeCapabilities, resolver)
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = .6f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)),
            )
        },
        modifier = Modifier.testTag("compute_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Chat settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() })
                    state.selected?.let { model ->
                        val running = state.activeCompute?.let { " · running on ${it.label}" }.orEmpty()
                        Text(model.title + running, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_compute_settings")) {
                    LucideIcon(LucideR.drawable.lucide_ic_x, "Close chat settings", Modifier.size(22.dp))
                }
            }
            Hairline()
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReplySpeedControl(state.replyTokensPerSecond, onReplySpeedChange)
                state.error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings_error"))
                }
                Hairline()
                ComputeSettingsPanel(
                    backend = state.computeBackend, cpuThreads = state.cpuThreads,
                    onBackendChange = { onOptionsChange(state.computeOptions.copy(backend = it)) },
                    onCpuThreadsChange = { onOptionsChange(state.computeOptions.copy(cpuThreads = it)) },
                    hardware = state.hardware, artifactSizeBytes = state.selectedArtifact?.sizeBytes ?: 0,
                    measurements = state.decodeMeasurements, backendAdmissions = backendAdmissions,
                    options = state.computeOptions, sampler = state.sampler,
                    capabilities = state.computeCapabilities, activeCompute = state.activeCompute,
                    needsApply = state.computeNeedsApply, loaded = state.appliedComputeOptions != null,
                    runtimeState = state.runtimeState, downloaded = state.downloadedPath != null,
                    onOptionsChange = onOptionsChange, onSamplerChange = onSamplerChange, onApply = onApply, onReset = onReset,
                    framed = false,
                )
            }
        }
    }
}
