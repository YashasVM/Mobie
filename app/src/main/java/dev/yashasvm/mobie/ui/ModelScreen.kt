package dev.yashasvm.mobie.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.isCancellable
import dev.yashasvm.mobie.ui.theme.Mobie

private val ActionShape = RoundedCornerShape(12.dp)

/**
 * Pre-flight spec sheet for one model: verdict, memory budget, storage, and the exact artifact
 * Mobie will download, with a sticky action dock for download / cancel / run.
 */
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModelTopBar(runtime = artifact.runtimeLabel, onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "identity") { ModelIdentity(model) }
            item(key = "verdict") { VerdictPanel(state.compatibility) }
            item(key = "memory") { MemoryPanel(state.compatibility, state.device) }
            item(key = "storage") { StoragePanel(artifact, state.compatibility, state.device) }
            item(key = "artifact") { ArtifactPanel(artifact, model) }
        }
        ActionDock(
            state = state,
            artifact = artifact,
            downloading = downloading,
            gatedWithoutToken = gatedWithoutToken,
            canDownload = !downloading && !gatedWithoutToken &&
                state.compatibility?.status in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) &&
                (!model.gated || state.tokenConfigured),
            onDownloadClick = {
                if (state.compatibility?.status == Compatibility.WARNING) confirmWarning = true else onDownload(false)
            },
            onRun = onRun,
            onCancelDownload = onCancelDownload,
        )
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
private fun ModelTopBar(runtime: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(20.dp))
            }
            Text(
                "PRE-FLIGHT CHECK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Tag(runtime, icon = LucideR.drawable.lucide_ic_cpu, mono = true)
        }
        Hairline()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelIdentity(model: AiModel) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            model.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            model.id,
            style = Mobie.mono.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (model.description.isNotBlank()) {
            Text(
                model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tag("Type: ${model.type.displayLabel()}")
            Tag(
                if (model.gated) "Gated" else "Public",
                icon = if (model.gated) LucideR.drawable.lucide_ic_lock else LucideR.drawable.lucide_ic_globe,
            )
            Tag(
                if (model.supportsVision) "Vision" else "Text",
                icon = if (model.supportsVision) LucideR.drawable.lucide_ic_eye else LucideR.drawable.lucide_ic_type,
            )
        }
    }
}

@Composable
private fun VerdictPanel(result: CompatibilityResult?) {
    val status = result?.status
    val color = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    Panel(Modifier.fillMaxWidth(), borderColor = color.copy(alpha = .35f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Verdict")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(color, size = 10.dp, pulsing = status == null)
                Text(
                    when (status) {
                        Compatibility.COMPATIBLE -> "Runs on this phone"
                        Compatibility.WARNING -> "Free memory first"
                        Compatibility.INCOMPATIBLE -> "Won't fit on this phone"
                        Compatibility.CONVERSION_REQUIRED -> "Not directly runnable"
                        null -> "Checking this phone"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                )
            }
            result?.reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MemoryPanel(result: CompatibilityResult?, device: DeviceProfile?) {
    val weightsColor = MaterialTheme.colorScheme.primary
    val kvColor = Mobie.signals.thinking
    val overheadColor = MaterialTheme.colorScheme.outline
    val weights = result?.modelWeightsBytes ?: 0L
    val kv = result?.kvCacheBytes ?: 0L
    val overhead = result?.runtimeOverheadBytes ?: 0L
    val peak = result?.estimatedRamBytes?.takeIf { it > 0 } ?: (weights + kv + overhead)
    val hasBreakdown = weights + kv + overhead > 0
    val segments = if (hasBreakdown) {
        listOf(
            MemorySegment("Model weights", weights, weightsColor),
            MemorySegment("KV cache", kv, kvColor),
            MemorySegment("Runtime overhead", overhead, overheadColor),
        )
    } else {
        listOf(MemorySegment("Estimated peak", peak, weightsColor))
    }
    val total = device?.totalRamBytes ?: 0L
    val available = device?.availableRamBytes ?: 0L

    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Memory budget") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_memory_stick,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetricRow {
                val (peakValue, peakUnit) = splitBytes(peak)
                Metric("Est. peak", peakValue, Modifier.weight(1f), unit = peakUnit, valueStyle = Mobie.mono.large)
                VerticalHairline(36.dp)
                val (totalValue, totalUnit) = splitBytes(total)
                Metric("Total RAM", totalValue, Modifier.weight(1f), unit = totalUnit, valueStyle = Mobie.mono.large)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryBar(
                    segments = segments,
                    capacityBytes = total,
                    markerBytes = available.takeIf { it > 0 },
                    height = 12.dp,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("0", style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatBytes(total), style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { LegendRow(it.label, formatBytes(it.bytes), it.color) }
                LegendRow("Free now", formatBytes(available), MaterialTheme.colorScheme.onSurface, marker = true)
                SpecRow("Context window", result?.contextWindowTokens?.takeIf { it > 0 }?.let { "${formatTokens(it)} tokens" } ?: "Unknown")
            }
            Hairline()
            Text(
                "Estimated peak ≈ ${formatBytes(peak)} of ${formatBytes(total)} total · ${formatBytes(available)} available now",
                style = Mobie.mono.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val name = listOfNotNull(deviceName(device), chipName(device)).joinToString(" · ")
            Text(
                if (name.isBlank()) deviceLabel(device) else "$name · ${deviceLabel(device)}",
                style = Mobie.mono.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LegendRow(label: String, value: String, color: Color, marker: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (marker) 2.dp else 10.dp, height = 10.dp)
                .background(color, RoundedCornerShape(if (marker) 1.dp else 3.dp)),
        )
        Spacer(Modifier.size(if (marker) 18.dp else 10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = Mobie.mono.small, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StoragePanel(artifact: ModelArtifact, result: CompatibilityResult?, device: DeviceProfile?) {
    val needed = result?.requiredStorageBytes?.takeIf { it > 0 } ?: artifact.sizeBytes
    val free = device?.availableStorageBytes ?: 0L
    val fits = needed > 0 && free > 0 && needed <= free
    val barColor = when {
        needed <= 0 || free <= 0 -> MaterialTheme.colorScheme.outline
        fits -> MaterialTheme.colorScheme.primary
        else -> Mobie.signals.blocked
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Storage") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_hard_drive,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetricRow {
                val (neededValue, neededUnit) = splitBytes(needed)
                Metric("Needs", neededValue, Modifier.weight(1f), unit = neededUnit)
                VerticalHairline()
                val (freeValue, freeUnit) = splitBytes(free)
                Metric("Free", freeValue, Modifier.weight(1f), unit = freeUnit)
                VerticalHairline()
                val after = if (needed > 0 && free > 0) free - needed else 0L
                val (afterValue, afterUnit) = if (after < 0) "Short" to null else splitBytes(after)
                Metric(
                    "After",
                    afterValue,
                    Modifier.weight(1f),
                    unit = afterUnit,
                    valueColor = if (after < 0) Mobie.signals.blocked else MaterialTheme.colorScheme.onSurface,
                )
            }
            ProgressTrack(
                progress = if (needed > 0 && free > 0) needed.toFloat() / free else 0f,
                color = barColor,
            )
        }
    }
}

@Composable
private fun ArtifactPanel(artifact: ModelArtifact, model: AiModel) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Artifact") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_file_text,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SpecRow("Runtime", artifact.runtimeLabel)
            SpecRow("File", artifact.fileName.substringAfterLast('/'))
            SpecRow("Download size", formatBytes(artifact.sizeBytes))
            SpecRow("Quantization", artifact.quantization ?: "Publisher default")
            SpecRow(
                "Integrity",
                if (artifact.sha256.isNullOrBlank()) "Size check only" else "SHA-256 verified on download",
                valueColor = if (artifact.sha256.isNullOrBlank()) null else Mobie.signals.ready,
            )
            SpecRow("Input", if (model.supportsVision) "Vision" else "Text only")
            SpecRow("License", model.license ?: "Check model card")
            SpecRow("Access", if (model.gated) "Hugging Face approval required" else "Public")
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = Mobie.mono.small,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionDock(
    state: MobieUiState,
    artifact: ModelArtifact,
    downloading: Boolean,
    gatedWithoutToken: Boolean,
    canDownload: Boolean,
    onDownloadClick: () -> Unit,
    onRun: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Hairline()
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.download?.let { DownloadStatus(it) }
            if (state.downloadedPath != null) {
                if (!downloading) {
                    Button(
                        onClick = onRun,
                        shape = ActionShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                    ) {
                        LucideIcon(LucideR.drawable.lucide_ic_play, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Run locally")
                    }
                }
            } else {
                if (gatedWithoutToken) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LucideIcon(
                            LucideR.drawable.lucide_ic_lock,
                            null,
                            Modifier.padding(top = 2.dp).size(16.dp),
                            tint = Mobie.signals.caution,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Hugging Face access required", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Add a token in Settings to download this gated model.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Button(
                    onClick = onDownloadClick,
                    enabled = canDownload,
                    shape = ActionShape,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Mobie.signals.track,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                ) {
                    LucideIcon(
                        if (gatedWithoutToken) LucideR.drawable.lucide_ic_lock else LucideR.drawable.lucide_ic_download,
                        null,
                        Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when {
                            downloading -> "Downloading…"
                            gatedWithoutToken -> "Download requires token"
                            else -> if (artifact.sizeBytes > 0) "Download model · ${formatBytes(artifact.sizeBytes)}" else "Download model"
                        },
                    )
                }
            }
            if (state.download?.isCancellable == true) {
                OutlinedButton(
                    onClick = onCancelDownload,
                    shape = ActionShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cancel_download"),
                ) {
                    LucideIcon(LucideR.drawable.lucide_ic_x, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Cancel download")
                }
            }
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun DownloadStatus(download: DownloadProgress) {
    val progress = if (download.totalBytes > 0) {
        (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        progress,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "download progress",
    )
    val active = !download.state.isFinished
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (active && download.totalBytes > 0) {
            ProgressTrack(animatedProgress)
        } else if (active) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Mobie.signals.track,
                strokeCap = StrokeCap.Round,
            )
        }
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (download.error != null) {
                Text(
                    download.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                if (active) StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 6.dp)
                Text(
                    downloadProgressLabel(download),
                    style = Mobie.mono.tiny,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    downloadEta(download)?.let {
                        Text(it, style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
