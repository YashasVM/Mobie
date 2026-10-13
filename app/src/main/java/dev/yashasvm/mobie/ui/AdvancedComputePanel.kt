package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import dev.yashasvm.mobie.core.runtime.ActivationType
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.SamplerSettings

private const val EXPERIMENT_WARNING = "Experimental — not yet tested on this phone"

@Composable
internal fun AdvancedComputePanel(
    options: ComputeOptions,
    sampler: SamplerSettings,
    coreCount: Int,
    capabilities: ComputeCapabilities?,
    onOptionsChange: (ComputeOptions) -> Unit,
    onSamplerChange: (SamplerSettings) -> String?,
    onReset: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TermButton(
        "Advanced ${if (expanded) "▾" else "▸"}",
        onClick = { expanded = !expanded }, style = TermButtonStyle.Ghost,
        modifier = Modifier.fillMaxWidth().testTag("advanced_toggle"),
    )
    if (!expanded) return
    Column(Modifier.testTag("advanced_controls"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AdvancedField("CPU threads", "Auto chooses workers for your CPU layout; extra threads can slow it down.", "advanced_threads") {
            val selected = CpuThreadPolicy.clamp(options.cpuThreads, coreCount)
            AdvancedChoices(listOf<Int?>(null) + (1..CpuThreadPolicy.limit(coreCount)), selected, { it?.toString() ?: "Auto" }) {
                onOptionsChange(options.copy(cpuThreads = it))
            }
        }
        AdvancedField("Context size", "Limits remembered input and reply tokens; smaller sizes use less memory.", "advanced_context") {
            if (capabilities == null) {
                Text("Run this model once to read its context limits.", style = MaterialTheme.typography.bodySmall)
            } else {
                AdvancedChoices(listOf<Int?>(null) + capabilities.contextChoices(), options.contextTokens, { it?.toString() ?: "Auto" }) {
                    onOptionsChange(options.copy(contextTokens = it))
                }
                Text("Artifact maximum: ${capabilities.maxContextTokens} tokens${if (!capabilities.dynamicContext) " · fixed size" else ""}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        AdvancedField("Max output tokens", "Caps reasoning and the visible reply together, within the context limit.", "advanced_output") {
            NumberOption(options.maxOutputTokens.toString(), "1–1024", "output_tokens") { text ->
                text.toIntOrNull()?.takeIf { it in 1..1_024 }?.let { onOptionsChange(options.copy(maxOutputTokens = it)) }
                    ?: return@NumberOption "Enter a whole number from 1 to 1024."
                null
            }
        }
        if (capabilities?.supportsThinking == true) {
            AdvancedField("Thinking", "Reasoning uses the same output budget as the final answer.", "advanced_thinking") {
                AdvancedChoices(listOf<Boolean?>(null, false, true), options.thinkingEnabled,
                    { when (it) { null -> "Model default"; true -> "On"; false -> "Off" } }) {
                    onOptionsChange(options.copy(thinkingEnabled = it))
                }
                Text("Thinking budget: limits reasoning tokens; Model default keeps the artifact setting.", style = MaterialTheme.typography.bodySmall)
                AdvancedChoices(listOf<Int?>(null, 0, 64, 128, 256, 512, 1_024), options.thinkingTokenBudget, { it?.toString() ?: "Model default" }) {
                    onOptionsChange(options.copy(thinkingTokenBudget = it))
                }
            }
        }
        AdvancedField("Activation type", "Changes working precision, not the model's weight quantization.", "advanced_activation") {
            AdvancedChoices(ActivationType.entries, options.activationType, { if (it == ActivationType.AUTO) "Auto" else it.name }) {
                onOptionsChange(options.copy(activationType = it))
            }
            Text(EXPERIMENT_WARNING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (capabilities?.supportsSpeculativeDecoding == true) {
            AdvancedField("Speculative decoding", "Uses the artifact's drafter to propose tokens; may need extra memory.", "advanced_speculative") {
                AdvancedChoices(listOf<Boolean?>(null, false, true), options.speculativeDecoding,
                    { when (it) { null -> "Model default"; true -> "On"; false -> "Off" } }) {
                    onOptionsChange(options.copy(speculativeDecoding = it))
                }
                Text(EXPERIMENT_WARNING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AdvancedField("Prefill preface", "Processes restored conversation history during creation; moves work, rather than removing it.", "advanced_preface") {
            AdvancedChoices(listOf(false, true), options.prefillPrefaceOnInit, { if (it) "On" else "Off" }) {
                onOptionsChange(options.copy(prefillPrefaceOnInit = it))
            }
        }
        AdvancedField("Sampler", "Controls the reply's variation; it also changes fair speed comparisons.", "advanced_sampler") {
            NumberOption(sampler.temperature.toString(), "Temperature · 0–2", "sampler_temperature") { text ->
                val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2.0 }
                    ?: return@NumberOption "Enter a number from 0 to 2."
                onSamplerChange(sampler.copy(temperature = value))
            }
            Text("Temperature: 0 picks the most likely token; higher values add variation.", style = MaterialTheme.typography.bodySmall)
            NumberOption(sampler.topK.toString(), "Top-k · 1–64", "sampler_top_k") { text ->
                val value = text.toIntOrNull()?.takeIf { it in 1..64 } ?: return@NumberOption "Enter a whole number from 1 to 64."
                onSamplerChange(sampler.copy(topK = value))
            }
            Text("Top-k: limits the number of candidate tokens considered.", style = MaterialTheme.typography.bodySmall)
            NumberOption(sampler.topP.toString(), "Top-p · above 0, up to 1", "sampler_top_p") { text ->
                val value = text.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 1 }
                    ?: return@NumberOption "Enter a number above 0 and no greater than 1."
                onSamplerChange(sampler.copy(topP = value))
            }
            Text("Top-p: keeps candidates within a cumulative probability threshold.", style = MaterialTheme.typography.bodySmall)
            NumberOption(sampler.seed.toString(), "Seed · whole number", "sampler_seed") { text ->
                val value = text.toIntOrNull() ?: return@NumberOption "Enter a whole number within the 32-bit range."
                onSamplerChange(sampler.copy(seed = value))
            }
            Text("Seed: starting value for random selection; repeatability also depends on the runtime.", style = MaterialTheme.typography.bodySmall)
        }
        if (capabilities == null) Text("Thinking and drafter controls appear after the runtime reads this artifact's metadata.", style = MaterialTheme.typography.bodySmall)
        Text("Estimates use model size and assumed memory bandwidth. Heat, context and CPU threads affect speed. Run a reply to measure it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TermButton("Reset to recommended", onClick = onReset, style = TermButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth().testTag("reset_compute"))
    }
}

@Composable
private fun AdvancedField(title: String, explanation: String, tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun <T> AdvancedChoices(choices: List<T>, selected: T, label: (T) -> String, onChange: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { value -> FlagChoice(label(value), selected == value, { onChange(value) }) }
    }
}

@Composable
private fun NumberOption(value: String, label: String, tag: String, onChange: (String) -> String?) {
    var text by remember(value) { mutableStateOf(value) }
    var error by remember(value) { mutableStateOf<String?>(null) }
    OutlinedTextField(text, { newValue -> text = newValue; error = onChange(newValue) },
        label = { Text(label) }, singleLine = true, shape = RectangleShape, isError = error != null,
        supportingText = error?.let { message -> { Text(message) } }, modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
internal fun ComputeSettingsDialog(
    state: MobieUiState,
    onOptionsChange: (ComputeOptions) -> Unit,
    onSamplerChange: (SamplerSettings) -> String?,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("Close", onClick = onClose, style = TermButtonStyle.Ghost)
            ComputeSettingsPanel(
                backend = state.computeBackend, cpuThreads = state.cpuThreads,
                onBackendChange = { onOptionsChange(state.computeOptions.copy(backend = it)) },
                onCpuThreadsChange = { onOptionsChange(state.computeOptions.copy(cpuThreads = it)) },
                hardware = state.hardware, artifactSizeBytes = state.selectedArtifact?.sizeBytes ?: 0,
                measuredSpeeds = state.measuredDecodeSpeeds, options = state.computeOptions, sampler = state.sampler,
                capabilities = state.computeCapabilities, activeCompute = state.activeCompute,
                needsApply = state.computeNeedsApply, runtimeState = state.runtimeState, downloaded = state.downloadedPath != null,
                onOptionsChange = onOptionsChange, onSamplerChange = onSamplerChange, onApply = onApply, onReset = onReset,
            )
        }
    }
}
