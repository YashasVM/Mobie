package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelType
import androidx.work.WorkInfo
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MobieApp(container: AppContainer) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var confirmWarning by remember { mutableStateOf(false) }

    fun continueDownload() {
        if (state.compatibility?.status == Compatibility.WARNING) {
            confirmWarning = true
        } else {
            viewModel.download()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continueDownload() }

    fun requestDownload() {
        val context = container.appContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueDownload()
        }
    }

    BackHandler(enabled = state.selected != null) { viewModel.select(null) }
    LaunchedEffect(state.selected?.id) { confirmWarning = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selected == null) "Mobie" else state.selected?.title.orEmpty(),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (state.selected != null) TextButton(onClick = { viewModel.select(null) }) { Text("Back") }
                },
                actions = {
                    if (state.selected == null) TextButton(onClick = { showSettings = true }) { Text("HF token") }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            if (state.selected == null) {
                CatalogScreen(state, viewModel::setQuery, viewModel::search, viewModel::select, viewModel::loadFeatured)
            } else {
                ModelScreen(
                    state = state,
                    onDownload = ::requestDownload,
                    onRequest = viewModel::requestConversion,
                    onConfigureToken = { showSettings = true },
                )
            }
        }
    }

    if (confirmWarning) {
        AlertDialog(
            onDismissRequest = { confirmWarning = false },
            title = { Text("This model may not load") },
            text = { Text(state.compatibility?.reason.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWarning = false
                        viewModel.download(allowWarning = true)
                    },
                ) { Text("Download anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmWarning = false }) { Text("Cancel") } },
        )
    }

    if (showSettings) {
        TokenDialog(state.tokenConfigured, onDismiss = { showSettings = false }) {
            viewModel.saveToken(it)
            showSettings = false
        }
    }
}

@Composable
internal fun CatalogScreen(
    state: MobieUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Run open models on your phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Inference stays local after download.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search Hugging Face") },
            trailingIcon = { TextButton(onClick = onSearch) { Text("Search") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Spacer(Modifier.height(20.dp))
        if (state.query.isBlank() && state.recentModels.isNotEmpty()) {
            Text("Recently used", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.recentModels.take(2).forEach { model ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onSelect(model) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(model.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            Text(model.bestArtifact?.runtimeLabel ?: "Conversion", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.query.isBlank()) "Popular models" else "Search results", style = MaterialTheme.typography.titleLarge)
            if (state.query.isNotBlank()) TextButton(onClick = onFeatured) { Text("Featured") }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> ErrorState(state.error, onFeatured)
            state.models.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No runnable or convertible models found.")
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.models, key = { it.id }) { model -> ModelCard(model, onSelect) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ModelCard(model: AiModel, onSelect: (AiModel) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(model) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(model.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(model.description, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(model.bestArtifact?.runtimeLabel ?: "Conversion")
                model.bestArtifact?.let { Badge(formatBytes(it.sizeBytes)) }
                if (model.gated) Badge("Gated")
            }
        }
    }
}

@Composable
internal fun ModelScreen(
    state: MobieUiState,
    onDownload: () -> Unit,
    onRequest: () -> Unit,
    onConfigureToken: () -> Unit,
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(model.id, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(model.description.ifBlank { "No model description is available." })
            Spacer(Modifier.height(8.dp))
            Text("Type: ${model.type.displayLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { CompatibilityCard(state.compatibility, state.device) }
        if (artifact != null) item { ArtifactCard(artifact, model) }
        item {
            if (artifact == null) {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Request Conversion") }
                state.conversionStatus?.let { Text("Status: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}") }
            } else {
                val needsToken = model.gated && !state.tokenConfigured
                val downloading = state.download?.state in setOf(
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED,
                    WorkInfo.State.RUNNING,
                )
                val downloaded = state.download?.state == WorkInfo.State.SUCCEEDED
                Button(
                    onClick = onDownload,
                    enabled = state.compatibility?.status != Compatibility.INCOMPATIBLE &&
                        !needsToken && !downloading && !downloaded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            downloading -> "Downloading…"
                            downloaded -> "Downloaded"
                            model.gated -> "Authenticate, Download & Run"
                            else -> "Download & Run"
                        },
                    )
                }
                if (needsToken) {
                    Text("Accept this model's terms on Hugging Face, then add an access token.")
                    OutlinedButton(onClick = onConfigureToken, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Hugging Face token")
                    }
                }
                state.download?.let { download ->
                    val fraction = if (download.totalBytes > 0) {
                        (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    if (download.totalBytes > 0) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        when (download.state) {
                            WorkInfo.State.SUCCEEDED -> "Downloaded. Runtime integration is the next milestone."
                            WorkInfo.State.FAILED -> download.error ?: "Download failed"
                            WorkInfo.State.CANCELLED -> "Download cancelled"
                            else -> if (download.bytesPerSecond > 0) {
                                "${formatBytes(download.downloadedBytes)} · ${formatBytes(download.bytesPerSecond)}/s"
                            } else {
                                "${formatBytes(download.downloadedBytes)} · Starting…"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Download runs in the background, resumes partial files, and validates SHA-256 when Hugging Face provides it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CompatibilityCard(result: CompatibilityResult?, device: DeviceProfile?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device compatibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(result?.status?.name?.replace('_', ' ') ?: "Checking")
            Text(result?.reason.orEmpty())
            HorizontalDivider()
            InfoRow("Estimated model RAM", formatBytes(result?.estimatedRamBytes ?: 0))
            InfoRow("Available RAM now", formatBytes(device?.availableRamBytes ?: 0))
            InfoRow("Free storage", formatBytes(device?.availableStorageBytes ?: 0))
        }
    }
}

@Composable
private fun ArtifactCard(artifact: ModelArtifact, model: AiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recommended artifact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoRow("Runtime", artifact.runtimeLabel)
        InfoRow("Quantization", artifact.quantization ?: "As published")
        InfoRow("Download size", formatBytes(artifact.sizeBytes))
        InfoRow("License", model.license ?: "Check model card")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(100), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            OutlinedButton(onClick = retry) { Text("Retry") }
        }
    }
}

@Composable
private fun TokenDialog(configured: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hugging Face token") },
        text = {
            Column {
                Text(if (configured) "A token is securely stored. Enter a replacement or leave blank to remove it." else "Needed only for private or gated models.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.testTag("hf_token_input"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("hf_…") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(token) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatBytes(value: Long): String {
    if (value <= 0) return "Unknown"
    val gib = value / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1) String.format(Locale.US, "%.1f GB", gib) else String.format(Locale.US, "%.0f MB", value / (1024.0 * 1024.0))
}

private fun ModelType.displayLabel(): String = when (this) {
    ModelType.TEXT_GENERATION -> "Text generation"
    ModelType.EMBEDDING -> "Embedding"
    ModelType.VISION -> "Vision"
    ModelType.AUDIO -> "Audio"
    ModelType.UNKNOWN -> "Unknown"
}
