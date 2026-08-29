package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MobieApp(container: AppContainer) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    var splashFinished by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (!splashFinished) {
        MobieSplash { splashFinished = true }
        return
    }
    if (!state.onboardingComplete) {
        OnboardingScreen(viewModel::finishOnboarding)
        return
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.download() }
    fun requestDownload() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(container.appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.download()
        }
    }

    BackHandler(enabled = state.selected != null) {
        if (state.chatting) viewModel.leaveChat() else viewModel.select(null)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.chatting -> state.selected?.title.orEmpty()
                            state.selected != null -> state.selected?.title.orEmpty()
                            else -> "Mobie"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (state.selected != null) {
                        TextButton(onClick = {
                            if (state.chatting) viewModel.leaveChat() else viewModel.select(null)
                        }) { Text("Back") }
                    }
                },
                actions = {
                    if (state.selected == null) {
                        TextButton(onClick = { showSettings = true }) { Text("HF token") }
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.chatting -> ChatScreen(state, viewModel::sendMessage)
                state.selected != null -> ModelScreen(state, ::requestDownload, viewModel::runInstalled) {
                    showSettings = true
                }
                else -> CatalogScreen(
                    state,
                    viewModel::setQuery,
                    viewModel::search,
                    viewModel::select,
                    viewModel::loadFeatured,
                )
            }
        }
    }

    if (showSettings) {
        TokenDialog(state.tokenConfigured, onDismiss = { showSettings = false }) {
            viewModel.saveToken(it)
            showSettings = false
        }
    }
}

@Composable
private fun MobieSplash(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(420))
        scale.animateTo(1f, tween(420))
        delay(520)
        alpha.animateTo(0f, tween(260))
        onFinished()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.graphicsLayer(alpha = alpha.value, scaleX = scale.value, scaleY = scale.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("M", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.displayMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Mobie", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingScreen(onContinue: (String?) -> Unit) {
    var token by remember { mutableStateOf("") }
    val tokenLooksValid = token.startsWith("hf_") && token.length > 10
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Connect Hugging Face", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your token unlocks gated models and avoids anonymous rate limits. It is encrypted on this device and sent only to Hugging Face.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_token_input"),
            label = { Text("Hugging Face access token") },
            placeholder = { Text("hf_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onContinue(token) },
            enabled = tokenLooksValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue securely") }
        TextButton(onClick = { onContinue(null) }, modifier = Modifier.fillMaxWidth()) {
            Text("Use public models without a token")
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
        Text("Best models for this phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${formatBytes(state.device?.totalRamBytes ?: 0)} RAM · Only ready-to-run LiteRT-LM models",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search compatible Hugging Face models") },
            trailingIcon = { TextButton(onClick = onSearch) { Text("Search") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.query.isBlank()) "Recommended" else "Compatible results", style = MaterialTheme.typography.titleLarge)
            if (state.query.isNotBlank()) TextButton(onClick = onFeatured) { Text("Recommended") }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> ErrorState(state.error, onFeatured)
            state.models.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No verified LiteRT-LM model fits this device.")
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("LiteRT-LM")
                model.bestArtifact?.let { Badge(formatBytes(it.sizeBytes)) }
                if (model.supportsVision) Badge("Vision")
            }
        }
    }
}

@Composable
internal fun ModelScreen(
    state: MobieUiState,
    onDownload: () -> Unit,
    onRun: () -> Unit,
    onConfigureToken: () -> Unit,
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(model.id, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(model.description.ifBlank { "Ready-to-run Hugging Face LiteRT-LM model." })
            Text("Type: ${model.type.displayLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { CompatibilityCard(state.compatibility, state.device) }
        item { ArtifactCard(artifact, model) }
        item {
            val downloading = state.download?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
            if (state.downloadedPath != null) {
                Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) { Text("Run locally") }
            } else {
                Button(
                    onClick = onDownload,
                    enabled = !downloading && (!model.gated || state.tokenConfigured),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (downloading) "Downloading…" else "Download & Run")
                }
            }
            if (model.gated && !state.tokenConfigured) {
                Text("Accept the model terms on Hugging Face and add your token before downloading.")
                OutlinedButton(onClick = onConfigureToken, modifier = Modifier.fillMaxWidth()) { Text("Add token") }
            }
            state.download?.let { download ->
                if (download.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    download.error ?: "${formatBytes(download.downloadedBytes)} · ${formatBytes(download.bytesPerSecond)}/s",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ChatScreen(state: MobieUiState, onSend: (String, String?) -> Unit) {
    val model = state.selected ?: return
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imagePath = uri?.let { copyImageToCache(context, it) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        when (state.runtimeState) {
            RuntimeState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading model into memory…")
                }
            }
            RuntimeState.ERROR -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "The model could not run.", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.messages.isEmpty()) item {
                        Text("Running completely on this device", color = MaterialTheme.colorScheme.primary)
                    }
                    items(state.messages) { message ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.88f),
                            ) {
                                Text(message.text.ifEmpty { "…" }, Modifier.padding(12.dp))
                            }
                        }
                    }
                }
                state.stats?.let {
                    Text(
                        String.format(Locale.US, "%.1f tokens/s · %s RAM", it.tokensPerSecond, formatBytes(it.ramBytes)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                imagePath?.let { Text("Image attached", color = MaterialTheme.colorScheme.primary) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.supportsVision) {
                        TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Image") }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f).testTag("chat_input"),
                        placeholder = { Text("Message") },
                        enabled = state.runtimeState == RuntimeState.READY,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            onSend(prompt, imagePath)
                            prompt = ""
                            imagePath = null
                        }),
                    )
                    TextButton(
                        onClick = {
                            onSend(prompt, imagePath)
                            prompt = ""
                            imagePath = null
                        },
                        enabled = prompt.isNotBlank() && state.runtimeState == RuntimeState.READY,
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun CompatibilityCard(result: CompatibilityResult?, device: DeviceProfile?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verified for this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(result?.reason.orEmpty())
            HorizontalDivider()
            InfoRow("Estimated RAM", formatBytes(result?.estimatedRamBytes ?: 0))
            InfoRow("Available RAM", formatBytes(device?.availableRamBytes ?: 0))
            InfoRow("Free storage", formatBytes(device?.availableStorageBytes ?: 0))
        }
    }
}

@Composable
private fun ArtifactCard(artifact: ModelArtifact, model: AiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Model package", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoRow("Runtime", "LiteRT-LM")
        InfoRow("Download", formatBytes(artifact.sizeBytes))
        InfoRow("Vision", if (model.supportsVision) "Supported" else "Text only")
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
                Text(if (configured) "A token is securely stored. Enter a replacement or leave blank to remove it." else "Used only for Hugging Face catalog and model downloads.")
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

private fun copyImageToCache(context: Context, uri: Uri): String? = runCatching {
    val file = File(context.cacheDir, "chat-image-${System.currentTimeMillis()}.bin")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use(input::copyTo)
    } ?: return null
    file.absolutePath
}.getOrNull()

private fun formatBytes(value: Long): String {
    if (value <= 0) return "Unknown"
    val gib = value / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1) String.format(Locale.US, "%.1f GB", gib) else String.format(Locale.US, "%.0f MB", value / (1024.0 * 1024.0))
}

private fun ModelType.displayLabel(): String = when (this) {
    ModelType.TEXT_GENERATION -> "Text generation"
    ModelType.VISION -> "Vision chat"
    ModelType.EMBEDDING -> "Embedding"
    ModelType.AUDIO -> "Audio"
    ModelType.UNKNOWN -> "Unknown"
}
