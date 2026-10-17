package dev.yashasvm.mobie.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.isCancellable
import dev.yashasvm.mobie.ui.theme.Mobie

/**
 * Model details, device fit, and download safeguards with a pinned download or run action.
 */
@Composable
internal fun ModelScreen(
    state: MobieUiState,
    onDownload: (Boolean) -> Unit,
    onRun: (Boolean) -> Unit,
    onCancelDownload: () -> Unit = {},
    onBack: () -> Unit = {},
    onBackendChange: (ComputeBackend) -> Unit = {},
    onCpuThreadsChange: (Int?) -> Unit = {},
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact ?: return
    var confirmWarning by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }
    val downloading = state.download?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
    val gatedWithoutToken = model.gated && !state.tokenConfigured
    // Sections stagger in once; scrolling back up does not replay the entrance.
    val entrance = rememberEntranceTracker()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModelTopBar(onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "identity") { ModelIdentity(model, artifact, Modifier.entrance(entrance, "identity", 0)) }
            item(key = "verdict") { VerdictPanel(state.compatibility, Modifier.entrance(entrance, "verdict", 1)) }
            item(key = "checklist") {
                ChecklistPanel(model, artifact, state, Modifier.entrance(entrance, "checklist", 2))
            }
            item(key = "memory") {
                MemoryPanel(state.compatibility, state.device, Modifier.entrance(entrance, "memory", 2))
            }
            item(key = "storage") {
                StoragePanel(artifact, state.compatibility, state.device, Modifier.entrance(entrance, "storage", 3))
            }
            item(key = "artifact") { ArtifactPanel(artifact, model, Modifier.entrance(entrance, "artifact", 4)) }
            item(key = "compute") {
                ComputeSettingsPanel(
                    backend = state.computeBackend,
                    cpuThreads = state.cpuThreads,
                    onBackendChange = onBackendChange,
                    onCpuThreadsChange = onCpuThreadsChange,
                    modifier = Modifier.entrance(entrance, "compute", 5),
                )
            }
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
            onRun = {
                if (state.compatibility?.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM) confirmRun = true
                else onRun(false)
            },
            onCancelDownload = onCancelDownload,
        )
    }

    if (confirmWarning) {
        ConfirmSheet(
            title = "Free memory before downloading",
            body = state.compatibility?.reason.orEmpty(),
            confirmLabel = "Download anyway",
            onConfirm = { confirmWarning = false; onDownload(true) },
            onDismiss = { confirmWarning = false },
        )
    }
    if (confirmRun) {
        ConfirmSheet(
            title = "Free memory before loading",
            body = state.compatibility?.reason.orEmpty(),
            confirmLabel = "Load anyway",
            onConfirm = { confirmRun = false; onRun(true) },
            onDismiss = { confirmRun = false },
        )
    }
}

/** Bottom sheet for a warning the user may override. Cancel is the default, easy way out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TermButton(confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth(), style = TermButtonStyle.Primary)
            TermButton("Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth(), style = TermButtonStyle.Ghost)
        }
    }
}

/**
 * The pre-download checklist. Every fact the user needs before committing storage and memory is
 * listed here: size, estimated RAM, free storage, runtime, quantization, license, access, and the
 * compatibility verdict.
 */
@Composable
private fun ChecklistPanel(model: AiModel, artifact: ModelArtifact, state: MobieUiState, modifier: Modifier = Modifier) {
    val result = state.compatibility
    val peak = result?.estimatedRamBytes?.takeIf { it > 0 }
    val needed = result?.requiredStorageBytes?.takeIf { it > 0 } ?: artifact.sizeBytes
    val free = state.device?.availableStorageBytes?.takeIf { it > 0 }
    val license = model.license ?: "Check model card"
    val storageShort = needed > 0 && free != null && needed > free
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Before you download")
            Spacer(Modifier.height(6.dp))
            StatRow("Download size", if (artifact.sizeBytes > 0) formatBytes(artifact.sizeBytes) else "Unknown")
            Hairline()
            StatRow("Estimated RAM", peak?.let { formatBytes(it) } ?: "Unknown")
            Hairline()
            StatRow(
                "Free storage",
                free?.let { formatBytes(it) } ?: "Unknown",
                valueColor = if (storageShort) Mobie.signals.blocked else null,
            )
            Hairline()
            StatRow("Runtime", artifact.runtimeLabel)
            Hairline()
            StatRow("Quantization", artifact.quantization ?: "Publisher default")
            Hairline()
            StatRow("License", license, stacked = license.length > 24)
            Hairline()
            StatRow("Access", if (model.gated) "Needs Hugging Face approval" else "Public")
            Hairline()
            StatRow(
                "Compatibility",
                statusLabel(result?.status),
                valueColor = result?.status.signalColor(),
            )
        }
    }
}

/** Keeps the model detail screen connected to the model library. */
@Composable
private fun ModelTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(22.dp))
        }
        Text("Model details", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Human-readable name first, with the publisher and supported inputs beneath it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelIdentity(model: AiModel, artifact: ModelArtifact, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (model.author.isNotBlank()) {
            Text(model.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            model.title.ifBlank { model.id.substringAfterLast('/') },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tag(model.type.displayLabel())
            Tag(
                if (model.gated) "Gated" else "Public",
                icon = if (model.gated) LucideR.drawable.lucide_ic_lock else LucideR.drawable.lucide_ic_globe,
                color = if (model.gated) Mobie.signals.caution else null,
            )
            Tag(
                if (model.supportsVision) "Vision" else "Text",
                icon = if (model.supportsVision) LucideR.drawable.lucide_ic_eye else LucideR.drawable.lucide_ic_type,
            )
            artifact.quantization?.let { Tag(it) }
            model.license?.let { Tag("License: $it") }
        }
    }
}

/** A bordered verdict box in the signal color, with a `[✓]` / `[!]` / `[x]` mark and the reason. */
@Composable
private fun VerdictPanel(result: CompatibilityResult?, modifier: Modifier = Modifier) {
    val status = result?.status
    val target = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    val color by animateColorAsState(target, tween(MotionMedium + 120, easing = EmphasizedEase), label = "verdict color")
    val mark = when (status) {
        Compatibility.COMPATIBLE -> "✓"
        Compatibility.WARNING -> "!"
        Compatibility.INCOMPATIBLE, Compatibility.CONVERSION_REQUIRED -> "✕"
        null -> "…"
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = .08f), shape)
            .border(1.dp, color.copy(alpha = .45f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = .18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, style = Mobie.numeric.medium, color = color)
            }
            AnimatedContent(
                targetState = when (status) {
                    Compatibility.COMPATIBLE -> "Fits this phone’s memory"
                    Compatibility.WARNING ->
                        if (result?.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM) "Fits after freeing memory" else "Check before running"
                    Compatibility.INCOMPATIBLE -> "Won't fit on this phone"
                    Compatibility.CONVERSION_REQUIRED -> "Not directly runnable"
                    null -> "Checking this phone"
                },
                transitionSpec = { fadeIn(tween(MotionMedium)) togetherWith fadeOut(tween(MotionShort)) },
                label = "verdict text",
            ) { verdict ->
                Text(verdict, style = MaterialTheme.typography.titleMedium, color = color)
            }
        }
        result?.reason?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryPanel(result: CompatibilityResult?, device: DeviceProfile?, modifier: Modifier = Modifier) {
    val weightsColor = MaterialTheme.colorScheme.primary
    val kvColor = Mobie.signals.thinking
    val overheadColor = MaterialTheme.colorScheme.outline
    val weights = result?.residentWeightBudgetBytes ?: 0L
    val kv = result?.kvCacheBytes ?: 0L
    val overhead = result?.runtimeOverheadBytes ?: 0L
    val peak = result?.estimatedRamBytes?.takeIf { it > 0 } ?: (weights + kv + overhead)
    val hasBreakdown = weights + kv + overhead > 0
    val segments = if (hasBreakdown) {
        listOf(
            MemorySegment("Model weights", weights, weightsColor),
            MemorySegment("KV cache", kv, kvColor),
            MemorySegment("Runtime", overhead, overheadColor),
        )
    } else {
        listOf(MemorySegment("Estimated peak", peak, weightsColor))
    }
    val total = device?.totalRamBytes ?: 0L
    val available = device?.availableRamBytes ?: 0L

    TuiWindow("Memory", modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MemoryBar(
                segments = segments,
                capacityBytes = total,
                markerBytes = available.takeIf { it > 0 },
                height = 14.dp,
            )
            Row(Modifier.fillMaxWidth()) {
                Text("0", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(formatBytes(total), style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Legend rows land just after the bar finishes sweeping in.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvRow("Est. peak", formatBytes(peak), valueColor = MaterialTheme.colorScheme.onSurface)
                segments.forEachIndexed { index, segment ->
                    KvRow(
                        segment.label,
                        formatBytes(segment.bytes),
                        swatch = segment.color,
                        modifier = Modifier.appear(delayMillis = 320 + index * 50, offsetDp = 6f),
                    )
                }
                KvRow(
                    "Free now",
                    formatBytes(available),
                    swatch = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.appear(delayMillis = 320 + segments.size * 50, offsetDp = 6f),
                )
            }
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvRow("Total RAM", formatBytes(total))
                KvRow(
                    "Context window",
                    result?.contextWindowTokens?.takeIf { it > 0 }?.let { "${formatTokens(it)} tokens" } ?: "Unknown",
                )
                KvRow("File-backed weights", formatBytes(result?.modelWeightsBytes ?: 0L))
            }
            Text(
                "Needs about ${formatBytes(peak)} at peak. This phone has ${formatBytes(total)} in total, " +
                    "${formatBytes(available)} free right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "App RAM ${formatBytes(device?.appPssBytes ?: 0L)} · Now ${formatBytes(available)} · " +
                    "After reclaim ${formatBytes(device?.availableAfterReclaimBytes ?: available)} · " +
                    "Swap ${formatBytes(device?.swapFreeBytes ?: 0L)} free / ${formatBytes(device?.swapTotalBytes ?: 0L)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val name = listOfNotNull(deviceName(device), chipName(device)).joinToString(" · ")
            Text(
                if (name.isBlank()) deviceLabel(device) else "$name · ${deviceLabel(device)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One key/value row: muted key on the left, value on the right. Use [stacked] for values
 * too long to share a line (such as file names), which puts the value under the key.
 */
@Composable
private fun KvRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
    valueColor: Color? = null,
    stacked: Boolean = false,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = valueColor ?: MaterialTheme.colorScheme.onSurface
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(key, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = Mobie.numeric.small, color = ink)
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(.42f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The swatch column is always reserved so keys line up whether or not they have one.
                Box(Modifier.size(8.dp).clip(CircleShape).background(swatch ?: Color.Transparent))
                Text(key, style = MaterialTheme.typography.bodyMedium, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                value,
                style = Mobie.numeric.small,
                color = ink,
                modifier = Modifier.weight(.58f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoragePanel(
    artifact: ModelArtifact,
    result: CompatibilityResult?,
    device: DeviceProfile?,
    modifier: Modifier = Modifier,
) {
    val needed = result?.requiredStorageBytes?.takeIf { it > 0 } ?: artifact.sizeBytes
    val free = device?.availableStorageBytes ?: 0L
    val fits = needed > 0 && free > 0 && needed <= free
    val barColor = when {
        needed <= 0 || free <= 0 -> MaterialTheme.colorScheme.outline
        fits -> MaterialTheme.colorScheme.primary
        else -> Mobie.signals.blocked
    }
    val after = if (needed > 0 && free > 0) free - needed else 0L
    TuiWindow("Storage", modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvRow("Needs", formatBytes(needed))
                KvRow("Free", formatBytes(free))
                KvRow(
                    "After",
                    if (after < 0) "Short by ${formatBytes(-after)}" else formatBytes(after),
                    valueColor = if (after < 0) Mobie.signals.blocked else null,
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
private fun ArtifactPanel(artifact: ModelArtifact, model: AiModel, modifier: Modifier = Modifier) {
    val rows = buildList {
        add(SpecLine("Runtime", artifact.runtimeLabel))
        add(SpecLine("File", middleEllipsis(artifact.fileName.substringAfterLast('/'))))
        add(SpecLine("Download size", formatBytes(artifact.sizeBytes)))
        add(SpecLine("Quantization", artifact.quantization ?: "Publisher default"))
        add(
            if (artifact.sha256.isNullOrBlank()) {
                SpecLine("Integrity", "Size check only")
            } else {
                SpecLine("Integrity", "SHA-256 verified on download", highlight = true)
            },
        )
        add(SpecLine("Input", if (model.supportsVision) "Vision" else "Text only"))
        add(SpecLine("License", model.license ?: "Check model card"))
        add(SpecLine("Access", if (model.gated) "Hugging Face approval required" else "Public"))
    }
    TuiWindow("Files and details", modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { line ->
                KvRow(
                    line.label,
                    line.value,
                    valueColor = if (line.highlight) Mobie.signals.ready else null,
                    stacked = line.value.length > 24,
                )
            }
        }
    }
}

private data class SpecLine(
    val label: String,
    val value: String,
    val highlight: Boolean = false,
)

/** Shortens long file names in the middle so the distinctive suffix (quantization, extension) stays visible. */
private fun middleEllipsis(text: String, maxChars: Int = 44): String {
    if (text.length <= maxChars) return text
    val tail = maxChars / 2
    val head = maxChars - tail - 1
    return text.take(head) + "…" + text.takeLast(tail)
}

@Composable
private fun ComputeSettingsPanel(
    backend: ComputeBackend,
    cpuThreads: Int?,
    onBackendChange: (ComputeBackend) -> Unit,
    onCpuThreadsChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    TuiWindow("Performance", modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlagHeader("Processing", "Where the model runs")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ComputeBackend.AUTO, ComputeBackend.CPU, ComputeBackend.GPU).forEach { choice ->
                        ChoiceChip(
                            label = when (choice) { ComputeBackend.AUTO -> "Automatic"; ComputeBackend.CPU -> "CPU"; ComputeBackend.GPU -> "GPU"; ComputeBackend.NPU -> "NPU" },
                            selected = backend == choice,
                            onClick = { onBackendChange(choice) },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlagHeader("CPU threads", "Changes apply on next load")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Auto", selected = cpuThreads == null, onClick = { onCpuThreadsChange(null) })
                    (1..8).forEach { count ->
                        ChoiceChip(
                            label = count.toString(),
                            selected = cpuThreads == count,
                            onClick = { onCpuThreadsChange(count) },
                        )
                    }
                }
            }
            Text(
                "First GPU load can be slow. Auto tries GPU on supported phones and uses CPU if loading fails. Changes apply on the next model load.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A settings label followed by a short explanation. */
@Composable
private fun FlagHeader(flag: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(flag, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PrimaryAction(@DrawableRes val icon: Int, val label: String)

/** Pinned bottom bar: download status, the primary action, and cancel while a download runs. */
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
    val downloaded = state.downloadedPath != null
    // While a download runs, its progress line and Cancel say everything; a disabled button would not.
    val showPrimary = !downloading
    val primary = when {
        downloaded -> PrimaryAction(LucideR.drawable.lucide_ic_play, "Run locally")
        downloading -> PrimaryAction(LucideR.drawable.lucide_ic_download, "Downloading…")
        gatedWithoutToken -> PrimaryAction(LucideR.drawable.lucide_ic_lock, "Download requires token")
        else -> PrimaryAction(
            LucideR.drawable.lucide_ic_download,
            if (artifact.sizeBytes > 0) "Download model · ${formatBytes(artifact.sizeBytes)}" else "Download model",
        )
    }
    val primaryEnabled = (downloaded && state.compatibility?.status != Compatibility.INCOMPATIBLE) || canDownload
    // Keep the last download visible while its block collapses away.
    var lastDownload by remember { mutableStateOf(state.download) }
    if (state.download != null) lastDownload = state.download

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Hairline()
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(
                visible = state.download != null,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                lastDownload?.let { DownloadStatus(it) }
            }
            AnimatedVisibility(
                visible = !downloaded && gatedWithoutToken,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                AccessNotice()
            }
            AnimatedVisibility(
                visible = showPrimary,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                AnimatedContent(
                    targetState = primary,
                    transitionSpec = {
                        (slideInVertically(tween(MotionMedium, easing = EmphasizedEase)) { it / 2 } + fadeIn(tween(MotionMedium))) togetherWith
                            (slideOutVertically(tween(MotionShort)) { -it / 2 } + fadeOut(tween(MotionShort))) using
                            SizeTransform(clip = false)
                    },
                    label = "primary action",
                ) { action ->
                    TermButton(
                        text = action.label,
                        onClick = if (downloaded) onRun else onDownloadClick,
                        modifier = Modifier.fillMaxWidth().testTag("model_primary_action"),
                        style = TermButtonStyle.Primary,
                        enabled = primaryEnabled,
                        icon = action.icon,
                    )
                }
            }
            AnimatedVisibility(
                visible = state.download?.isCancellable == true,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                TermButton(
                    text = "Cancel download",
                    onClick = onCancelDownload,
                    modifier = Modifier.fillMaxWidth().testTag("cancel_download"),
                    style = TermButtonStyle.Ghost,
                    icon = LucideR.drawable.lucide_ic_x,
                )
            }
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.appear(offsetDp = 6f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** Shown in the dock when a gated model has no token. Explains the next step without a dead end. */
@Composable
private fun AccessNotice() {
    val caution = Mobie.signals.caution
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(caution.copy(alpha = .08f), shape)
            .border(1.dp, caution.copy(alpha = .45f), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_lock, null, Modifier.padding(top = 2.dp).size(18.dp), tint = caution)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Hugging Face access required", style = MaterialTheme.typography.titleSmall)
            Text(
                "Add a token in Settings to download this gated model.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Download readout in one mono line, `42% · 512 MB / 1.2 GB · 8.1 MB/s · 1m 4s left`, above a
 * block-cell progress meter. The percentage rolls; the rest stays steady.
 */
@Composable
private fun DownloadStatus(download: DownloadProgress) {
    val known = download.totalBytes > 0
    val active = !download.state.isFinished
    val progress = if (known) (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f) else 0f
    val percent = if (known) (download.downloadedBytes.toDouble() / download.totalBytes * 100).toInt().coerceIn(0, 100) else 0
    val error = download.error
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active && known) ProgressTrack(progress)
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                download.state == WorkInfo.State.CANCELLED -> Text(
                    "Download cancelled",
                    style = Mobie.numeric.small,
                    color = dim,
                    modifier = Modifier.weight(1f),
                )
                !known -> {
                    if (active) StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 8.dp)
                    Text("Starting download…", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurface)
                }
                else -> {
                    if (active) StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 8.dp)
                    RollingText("$percent%", style = Mobie.numeric.medium, color = MaterialTheme.colorScheme.onSurface)
                    val rest = buildList {
                        add("${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}")
                        if (active && download.bytesPerSecond > 0) add("${formatBytes(download.bytesPerSecond)}/s")
                        if (active) downloadEta(download)?.let { add(it) }
                    }.joinToString(" · ")
                    Text(
                        " · $rest",
                        style = Mobie.numeric.small,
                        color = dim,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
