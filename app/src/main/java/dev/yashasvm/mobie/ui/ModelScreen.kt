package dev.yashasvm.mobie.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

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
    // Sections stagger in once; scrolling back up does not replay the entrance.
    val entrance = rememberEntranceTracker()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModelTopBar(runtime = artifact.runtimeLabel, onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "identity") { ModelIdentity(model, Modifier.entrance(entrance, "identity", 0)) }
            item(key = "verdict") { VerdictPanel(state.compatibility, Modifier.entrance(entrance, "verdict", 1)) }
            item(key = "memory") {
                MemoryPanel(state.compatibility, state.device, Modifier.entrance(entrance, "memory", 2))
            }
            item(key = "storage") {
                StoragePanel(artifact, state.compatibility, state.device, Modifier.entrance(entrance, "storage", 3))
            }
            item(key = "artifact") { ArtifactPanel(artifact, model, Modifier.entrance(entrance, "artifact", 4)) }
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
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(22.dp))
            }
            Text(
                "Pre-flight check",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Tag(runtime, icon = LucideR.drawable.lucide_ic_cpu)
        }
        Hairline()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelIdentity(model: AiModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            model.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            model.id,
            style = MaterialTheme.typography.bodyMedium,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
private fun VerdictPanel(result: CompatibilityResult?, modifier: Modifier = Modifier) {
    val status = result?.status
    val target = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    val color by animateColorAsState(target, tween(MotionMedium + 120, easing = EmphasizedEase), label = "verdict color")
    Panel(modifier.fillMaxWidth(), borderColor = color.copy(alpha = .35f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Verdict")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VerdictDot(color, status)
                AnimatedContent(
                    targetState = when (status) {
                        Compatibility.COMPATIBLE -> "Runs on this phone"
                        Compatibility.WARNING -> "Free memory first"
                        Compatibility.INCOMPATIBLE -> "Won't fit on this phone"
                        Compatibility.CONVERSION_REQUIRED -> "Not directly runnable"
                        null -> "Checking this phone"
                    },
                    transitionSpec = { fadeIn(tween(MotionMedium)) togetherWith fadeOut(tween(MotionShort)) },
                    label = "verdict text",
                ) { verdict ->
                    Text(verdict, style = MaterialTheme.typography.headlineSmall, color = color)
                }
            }
            result?.reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Status dot that sends out one soft ring when a verdict lands (and again if it changes).
 * While the check is still running it falls back to the shared breathing dot.
 */
@Composable
private fun VerdictDot(color: Color, status: Compatibility?) {
    val ring = remember { Animatable(1f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (status == null) return@LaunchedEffect
        ring.snapTo(0f)
        pop.snapTo(.6f)
        launch { pop.animateTo(1f, tween(MotionMedium, easing = EmphasizedEase)) }
        ring.animateTo(1f, tween(MotionLong - 80, easing = EmphasizedEase))
    }
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer {
                    val scale = 1f + ring.value * .9f
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - ring.value) * .45f
                }
                .background(color, CircleShape),
        )
        StatusDot(
            color,
            size = 12.dp,
            pulsing = status == null,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

@Composable
private fun MemoryPanel(result: CompatibilityResult?, device: DeviceProfile?, modifier: Modifier = Modifier) {
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

    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Memory budget") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_memory_stick,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetricRow {
                val (peakValue, peakUnit) = splitBytes(peak)
                Metric("Est. peak", peakValue, Modifier.weight(1f), unit = peakUnit, valueStyle = Mobie.numeric.large)
                VerticalHairline(40.dp)
                val (totalValue, totalUnit) = splitBytes(total)
                Metric("Total RAM", totalValue, Modifier.weight(1f), unit = totalUnit, valueStyle = Mobie.numeric.large)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryBar(
                    segments = segments,
                    capacityBytes = total,
                    markerBytes = available.takeIf { it > 0 },
                    height = 12.dp,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("0", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatBytes(total), style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Legend rows land just after the bar finishes sweeping in.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                segments.forEachIndexed { index, segment ->
                    LegendRow(
                        segment.label,
                        formatBytes(segment.bytes),
                        segment.color,
                        modifier = Modifier.appear(delayMillis = 320 + index * 50, offsetDp = 6f),
                    )
                }
                LegendRow(
                    "Free now",
                    formatBytes(available),
                    MaterialTheme.colorScheme.onSurface,
                    marker = true,
                    modifier = Modifier.appear(delayMillis = 320 + segments.size * 50, offsetDp = 6f),
                )
            }
            Hairline()
            SpecRow(
                "Context window",
                result?.contextWindowTokens?.takeIf { it > 0 }?.let { "${formatTokens(it)} tokens" } ?: "Unknown",
                numeric = true,
            )
            Text(
                "Needs about ${formatBytes(peak)} at peak. This phone has ${formatBytes(total)} in total, " +
                    "${formatBytes(available)} free right now.",
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

@Composable
private fun LegendRow(label: String, value: String, color: Color, modifier: Modifier = Modifier, marker: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Fixed-width swatch column keeps every label aligned.
        Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .size(width = if (marker) 3.dp else 12.dp, height = 12.dp)
                    .background(color, RoundedCornerShape(if (marker) 1.dp else 3.dp)),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        RollingText(value, style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurface)
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
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Storage") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_hard_drive,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetricRow {
                val (neededValue, neededUnit) = splitBytes(needed)
                Metric("Needs", neededValue, Modifier.weight(1f), unit = neededUnit)
                VerticalHairline(36.dp)
                val (freeValue, freeUnit) = splitBytes(free)
                Metric("Free", freeValue, Modifier.weight(1f), unit = freeUnit)
                VerticalHairline(36.dp)
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
private fun ArtifactPanel(artifact: ModelArtifact, model: AiModel, modifier: Modifier = Modifier) {
    val rows = buildList {
        add(SpecLine("Runtime", artifact.runtimeLabel))
        add(SpecLine("File", middleEllipsis(artifact.fileName.substringAfterLast('/'))))
        add(SpecLine("Download size", formatBytes(artifact.sizeBytes), numeric = true))
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
    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Artifact") {
                LucideIcon(
                    LucideR.drawable.lucide_ic_file_text,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.forEachIndexed { index, line ->
                if (index > 0) Hairline()
                SpecRow(
                    line.label,
                    line.value,
                    valueColor = if (line.highlight) Mobie.signals.ready else null,
                    numeric = line.numeric,
                )
            }
        }
    }
}

private data class SpecLine(
    val label: String,
    val value: String,
    val numeric: Boolean = false,
    val highlight: Boolean = false,
)

/** Label on the left, value right-aligned. Numbers use tabular figures; words use semibold body text. */
@Composable
private fun SpecRow(label: String, value: String, valueColor: Color? = null, numeric: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            style = if (numeric) Mobie.numeric.small else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shortens long file names in the middle so the distinctive suffix (quantization, extension) stays visible. */
private fun middleEllipsis(text: String, maxChars: Int = 44): String {
    if (text.length <= maxChars) return text
    val tail = maxChars / 2
    val head = maxChars - tail - 1
    return text.take(head) + "…" + text.takeLast(tail)
}

private data class PrimaryAction(@DrawableRes val icon: Int, val label: String)

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
    val showPrimary = !(downloaded && downloading)
    val primary = when {
        downloaded -> PrimaryAction(LucideR.drawable.lucide_ic_play, "Run locally")
        downloading -> PrimaryAction(LucideR.drawable.lucide_ic_download, "Downloading…")
        gatedWithoutToken -> PrimaryAction(LucideR.drawable.lucide_ic_lock, "Download requires token")
        else -> PrimaryAction(
            LucideR.drawable.lucide_ic_download,
            if (artifact.sizeBytes > 0) "Download model · ${formatBytes(artifact.sizeBytes)}" else "Download model",
        )
    }
    // Keep the last download visible while its block collapses away.
    var lastDownload by remember { mutableStateOf(state.download) }
    if (state.download != null) lastDownload = state.download

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Hairline()
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LucideIcon(
                        LucideR.drawable.lucide_ic_lock,
                        null,
                        Modifier.padding(top = 2.dp).size(18.dp),
                        tint = Mobie.signals.caution,
                    )
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
            AnimatedVisibility(
                visible = showPrimary,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                val interaction = remember { MutableInteractionSource() }
                Button(
                    onClick = if (downloaded) onRun else onDownloadClick,
                    enabled = downloaded || canDownload,
                    shape = ActionShape,
                    interactionSource = interaction,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Mobie.signals.track,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressScale(interaction)
                        .testTag("model_primary_action"),
                ) {
                    AnimatedContent(
                        targetState = primary,
                        transitionSpec = {
                            (slideInVertically(tween(MotionMedium, easing = EmphasizedEase)) { it / 2 } + fadeIn(tween(MotionMedium))) togetherWith
                                (slideOutVertically(tween(MotionShort)) { -it / 2 } + fadeOut(tween(MotionShort))) using
                                SizeTransform(clip = false)
                        },
                        contentAlignment = Alignment.Center,
                        label = "primary action",
                    ) { action ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon(action.icon, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.download?.isCancellable == true,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                val interaction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onCancelDownload,
                    shape = ActionShape,
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .pressScale(interaction)
                        .testTag("cancel_download"),
                ) {
                    LucideIcon(LucideR.drawable.lucide_ic_x, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Cancel download", style = MaterialTheme.typography.labelLarge)
                }
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

@Composable
private fun DownloadStatus(download: DownloadProgress) {
    val progress = if (download.totalBytes > 0) {
        (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
    } else 0f
    val active = !download.state.isFinished
    val label = downloadProgressLabel(download)
    // "42% · 1.2 GB of 3 GB · 8 MB/s" → the percentage rolls, the rest stays steady.
    val percent = label.substringBefore(" · ").takeIf { it.endsWith("%") }
    val detail = if (percent != null) label.substringAfter(" · ") else label
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active && download.totalBytes > 0) {
            ProgressTrack(progress)
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                if (active) StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 8.dp)
                if (percent != null) {
                    RollingText(percent, style = Mobie.numeric.medium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    detail,
                    style = Mobie.numeric.small,
                    color = if (percent != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    downloadEta(download)?.let {
                        Text(it, style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
