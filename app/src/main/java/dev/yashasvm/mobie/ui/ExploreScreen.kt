package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.device.DecodeMeasurement
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import java.util.Locale

internal enum class ExploreTask(val title: String, val description: String, val icon: Int) {
    CHAT("AI chat", "Think through an idea with a model on your phone.", LucideR.drawable.lucide_ic_message_square),
    IMAGE("Ask a photo", "Choose a photo or take one, then ask a vision model about it.", LucideR.drawable.lucide_ic_image),
    LAB("Prompt lab", "Summarize, rewrite, translate or write code in a fresh conversation.", LucideR.drawable.lucide_ic_flask_conical),
    LANGUAGE("Language practice", "Practice a language with a patient tutor. Start with your level and a topic.", LucideR.drawable.lucide_ic_languages),
    PERFORMANCE("Model performance", "Review measured speed and run the same prompt on your installed models.", LucideR.drawable.lucide_ic_gauge),
}

internal enum class PromptTask(val title: String, val example: String, val optionLabel: String? = null, val defaultOption: String = "") {
    FREEFORM("Freeform", "Explain why the sky changes color at sunset."),
    SUMMARIZE("Summarize", "Paste an article, notes or a long message."),
    REWRITE("Rewrite", "Paste a draft you want to improve.", "Tone", "Clear and friendly"),
    CODE("Code", "Describe what the code should do.", "Language", "Kotlin"),
    TRANSLATE("Translate", "Enter the text to translate.", "Translate into", "Spanish"),
}

/** Each task starts a new session; user text stays separate from the task instruction. */
internal fun taskPrompt(task: PromptTask, input: String, option: String): String {
    require(input.isNotBlank())
    require(task.optionLabel == null || option.isNotBlank())
    val instruction = when (task) {
        PromptTask.FREEFORM -> return input.trim()
        PromptTask.SUMMARIZE -> "Summarize the following text. Preserve key facts and avoid inventing details."
        PromptTask.REWRITE -> "Rewrite the following text in this tone: ${option.trim()}. Preserve its meaning."
        PromptTask.CODE -> "Write code in ${option.trim()} for the following request. Explain how to use it."
        PromptTask.TRANSLATE -> "Translate the following text into ${option.trim()}. Return only the translation."
    }
    return "$instruction\n\n${input.trim()}"
}

internal fun localTaskModels(models: List<InstalledModelEntry>, task: ExploreTask): List<InstalledModelEntry> =
    models.filter { entry ->
        entry.model.cloudModel == null && entry.model.bestArtifact?.format == ModelFormat.LITERT_LM &&
            (task != ExploreTask.IMAGE || entry.model.supportsVision)
    }

internal const val COMPARISON_PROMPT = "Explain how a rainbow forms in five short sentences."

@Composable
internal fun ExploreScreen(
    state: MobieUiState,
    onMenu: () -> Unit,
    onTask: (ExploreTask) -> Unit,
    onModels: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("explore_screen"),
        contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu, modifier = Modifier.testTag("open_menu")) {
                    LucideIcon(LucideR.drawable.lucide_ic_menu, "Open menu", Modifier.size(24.dp))
                }
                Text("Explore", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What will you make today?", style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
                Text("Use the models on your phone for everyday tasks. Local prompts and photos stay on this device.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.installedModels.size} installed · No cloud model is selected by these tasks",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(ExploreTask.entries, key = { it.name }) { task ->
            Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("explore_${task.name}"), onClick = { onTask(task) }) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LucideIcon(task.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleLarge)
                        Text(task.description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TermButton("Browse models", onModels, Modifier.fillMaxWidth(), style = TermButtonStyle.Ghost)
                Text("Audio transcription, media search and device actions need additional tested model support.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun TaskWorkspace(
    task: ExploreTask,
    state: MobieUiState,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onRun: (InstalledModelEntry, String?) -> Unit,
    onSettings: () -> Unit,
) {
    var template by rememberSaveable { mutableStateOf(PromptTask.FREEFORM) }
    var input by rememberSaveable(task) { mutableStateOf("") }
    var option by rememberSaveable(template) { mutableStateOf(template.defaultOption) }
    var language by rememberSaveable { mutableStateOf("Spanish") }
    val models = remember(state.installedModels, task) { localTaskModels(state.installedModels, task) }
    var selectedPath by rememberSaveable(task) { mutableStateOf<String?>(null) }
    val selected = models.firstOrNull { it.localPath == selectedPath } ?: models.firstOrNull()
    val busy = state.runtimeState in setOf(RuntimeState.LOADING, RuntimeState.GENERATING, RuntimeState.STOPPING)
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().testTag("task_workspace"),
        contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back to Explore", Modifier.size(24.dp)) }
                Text(task.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.description, style = MaterialTheme.typography.bodyLarge)
                Text("Choose an installed local model", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                if (models.isEmpty()) {
                    Text(if (task == ExploreTask.IMAGE) "Install a vision model to ask about photos." else "Download a model to get started.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TermButton("Find a model", onModels, Modifier.fillMaxWidth())
                }
            }
        }
        items(models, key = { it.localPath }) { entry ->
            FilterChip(selected = selected?.localPath == entry.localPath,
                onClick = { selectedPath = entry.localPath },
                label = { Column(Modifier.padding(vertical = 6.dp)) {
                    Text(entry.model.title)
                    Text(entry.model.bestArtifact?.fileName.orEmpty(), style = MaterialTheme.typography.labelSmall)
                } }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        }
        if (task == ExploreTask.LAB) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptTask.entries.forEach { choice ->
                        FilterChip(selected = template == choice, onClick = { template = choice }, label = { Text(choice.title) })
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (task == ExploreTask.LAB) {
                    template.optionLabel?.let { label ->
                        OutlinedTextField(option, { option = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(input, { input = it }, label = { Text("Your input") },
                        placeholder = { Text(template.example) }, minLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("lab_input"))
                } else if (task == ExploreTask.LANGUAGE) {
                    OutlinedTextField(language, { language = it }, label = { Text("Language to practice") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(input, { input = it }, label = { Text("Your level and topic") },
                        placeholder = { Text("I'm a beginner. Help me order lunch at a cafe.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                if (task == ExploreTask.PERFORMANCE) {
                    Text("Same prompt, fresh conversation", style = MaterialTheme.typography.titleMedium)
                    Text(COMPARISON_PROMPT, style = MaterialTheme.typography.bodyMedium)
                    Text("Use the same compute and sampling settings for each run. Speed also depends on temperature, battery and other apps. This is a single chat workload, not a controlled benchmark.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val validInput = when (task) {
                    ExploreTask.LAB -> input.isNotBlank() && (template.optionLabel == null || option.isNotBlank())
                    ExploreTask.LANGUAGE -> language.isNotBlank() && input.isNotBlank()
                    else -> true
                }
                TermButton(if (task == ExploreTask.PERFORMANCE) "Run comparison prompt" else if (task in setOf(ExploreTask.LAB, ExploreTask.LANGUAGE)) "Generate locally" else "Open local chat",
                    onClick = {
                        selected?.let { entry ->
                            val prompt = when (task) {
                                ExploreTask.LAB -> taskPrompt(template, input, option)
                                ExploreTask.LANGUAGE -> "Act as a patient ${language.trim()} tutor. Adapt to my level, correct mistakes gently and ask one question at a time.\n\n${input.trim()}"
                                ExploreTask.PERFORMANCE -> COMPARISON_PROMPT
                                else -> null
                            }
                            onRun(entry, prompt)
                        }
                    }, enabled = selected != null && validInput && !busy,
                    modifier = Modifier.fillMaxWidth().testTag("run_task"))
                if (busy) Text("Finish or stop the current run before starting another task.", style = MaterialTheme.typography.bodySmall)
                if (task in setOf(ExploreTask.LAB, ExploreTask.PERFORMANCE)) {
                    TermButton("Generation settings", onSettings, Modifier.fillMaxWidth(), style = TermButtonStyle.Ghost)
                }
                Text("Each run starts a new saved conversation. Your existing chats stay in history.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task == ExploreTask.IMAGE) Text("Photo input becomes available after the selected vision model loads. One photo per message.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (task == ExploreTask.PERFORMANCE) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Measurements for the active model", style = MaterialTheme.typography.titleMedium)
                    Text(state.selected?.title ?: "No model loaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.decodeMeasurements.isEmpty()) Text("No saved speed samples for this model yet. Run it to collect measured results.")
                }
            }
            items(state.decodeMeasurements) { measurement ->
                MeasurementRow(measurement)
            }
        }
    }
}

@Composable
private fun MeasurementRow(measurement: DecodeMeasurement) {
    Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${measurement.backend} · ${measurement.settings.cpuThreads?.toString() ?: "Auto"} threads · ${measurement.settings.contextTokens?.toString() ?: "Auto"} context",
                style = MaterialTheme.typography.labelMedium)
            Text(String.format(Locale.getDefault(), "%.1f to %.1f tokens/s", measurement.speed.lowTokensPerSecond, measurement.speed.highTokensPerSecond), style = MaterialTheme.typography.headlineSmall)
            Text("${measurement.speed.sampleCount} measured runs", style = MaterialTheme.typography.bodySmall)
        }
    }
}
