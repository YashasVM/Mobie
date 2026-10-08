package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import dev.yashasvm.mobie.R
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.isCancellable
import dev.yashasvm.mobie.data.history.ChatHistorySession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ModelScreen(
    state: MobieUiState,
    onDownload: (Boolean) -> Unit,
    onRun: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact ?: return
    var confirmWarning by remember { mutableStateOf(false) }
    val downloading = state.download?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
    val gatedWithoutToken = model.gated && !state.tokenConfigured
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TonalPanel(shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.downloadedPath != null) {
                        if (!downloading) Button(
                            onClick = onRun,
                            shape = RoundedCornerShape(100),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Run locally")
                        }
                    } else {
                        if (gatedWithoutToken) {
                            Text("Hugging Face access required", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add a token in Settings to download this gated model.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = {
                                if (state.compatibility?.status == Compatibility.WARNING) confirmWarning = true else onDownload(false)
                            },
                            enabled = !downloading && !gatedWithoutToken &&
                                state.compatibility?.status in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) &&
                                (!model.gated || state.tokenConfigured),
                            shape = RoundedCornerShape(100),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null)
                            Spacer(Modifier.size(8.dp))
                            Text(when {
                                downloading -> "Downloading…"
                                gatedWithoutToken -> "Download requires token"
                                else -> "Download model"
                            })
                        }
                    }
                    state.download?.let { download ->
                        val progress = if (download.totalBytes > 0) {
                            (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
                        } else 0f
                        val animatedProgress by animateFloatAsState(
                            progress,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                            label = "download progress",
                        )
                        if (!download.state.isFinished) {
                            if (download.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            download.error ?: downloadProgressLabel(download),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (download.isCancellable) {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cancel_download"),
                                shape = RoundedCornerShape(100),
                            ) {
                                LucideIcon(LucideR.drawable.lucide_ic_x, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Cancel download")
                            }
                        }
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 180.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ready check", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(100)) {
                            Text(artifact.runtimeLabel, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(model.title, style = MaterialTheme.typography.headlineMedium)
                    Text("${model.author} · ${model.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(model.description.ifBlank { "Ready-to-run Hugging Face LiteRT-LM model." }, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Badge("Type: ${model.type.displayLabel()}")
                        Badge(if (model.gated) "Gated" else "Public")
                        Badge(if (model.supportsVision) "Vision" else "Text")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Compatibility", "Can this model run comfortably here?")
                    TonalPanel {
                        CompatibilityCard(state.compatibility, state.device)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Model package", "The exact file Mobie will download")
                    TonalPanel {
                        ArtifactCard(artifact, model)
                    }
                }
            }
        }
    }
    if (confirmWarning) {
        AlertDialog(
            onDismissRequest = { confirmWarning = false },
            title = { Text("Free memory before running") },
            text = { Text(state.compatibility?.reason.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { confirmWarning = false; onDownload(true) }) { Text("Download anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmWarning = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompatibilityCard(result: CompatibilityResult?, device: DeviceProfile?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val status = result?.status
                Icon(
                    when (status) {
                        Compatibility.COMPATIBLE -> Icons.Filled.CheckCircle
                        Compatibility.WARNING -> Icons.Filled.WarningAmber
                        else -> Icons.Filled.ErrorOutline
                    },
                    null,
                    tint = when (status) {
                        Compatibility.COMPATIBLE -> Color(0xFF55D68B)
                        Compatibility.WARNING -> Color(0xFFFFC857)
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    when (status) {
                        Compatibility.COMPATIBLE -> "Ready for this phone"
                        Compatibility.WARNING -> "Needs memory first"
                        Compatibility.INCOMPATIBLE -> "Cannot run on this phone"
                        else -> "Not directly runnable"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(result?.reason.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            InfoRow("Model weights", formatBytes(result?.modelWeightsBytes ?: 0))
            InfoRow("Runtime overhead", formatBytes(result?.runtimeOverheadBytes ?: 0))
            InfoRow("KV cache", formatBytes(result?.kvCacheBytes ?: 0))
            InfoRow("Prompt context", "${result?.contextWindowTokens ?: 0} tokens")
            InfoRow("Estimated RAM", formatBytes(result?.estimatedRamBytes ?: 0))
            InfoRow("Total RAM", formatBytes(device?.totalRamBytes ?: 0))
            InfoRow("Available RAM", formatBytes(device?.availableRamBytes ?: 0))
            InfoRow("Storage needed", formatBytes(result?.requiredStorageBytes ?: 0))
            InfoRow("Free storage", formatBytes(device?.availableStorageBytes ?: 0))
            InfoRow("Device", deviceLabel(device))
    }
}

@Composable
private fun ArtifactCard(artifact: ModelArtifact, model: AiModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("Runtime", "LiteRT-LM")
            InfoRow("Artifact", artifact.fileName.substringAfterLast('/'))
            InfoRow("Download", formatBytes(artifact.sizeBytes))
            InfoRow("Quantization", artifact.quantization ?: "Publisher default")
            InfoRow("Checksum", if (artifact.sha256.isNullOrBlank()) "Size validation only" else "SHA-256 available")
            InfoRow("Vision", if (model.supportsVision) "Supported" else "Text only")
            InfoRow("License", model.license ?: "Check model card")
            InfoRow("Access", if (model.gated) "Hugging Face approval required" else "Public")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.46f))
        Text(
            value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.54f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
