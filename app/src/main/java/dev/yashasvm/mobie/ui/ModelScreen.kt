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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.device.BackendAdmission
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.device.CpuThreadPolicy
import dev.yashasvm.mobie.core.device.DecodeMeasurement
import dev.yashasvm.mobie.core.device.DecodeRunSettings
import dev.yashasvm.mobie.core.device.DecodeSpeedEstimator
import dev.yashasvm.mobie.core.device.matchDecodeMeasurement
import dev.yashasvm.mobie.core.device.HardwareProfile
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.runtime.ComputeOptions
import dev.yashasvm.mobie.core.runtime.ComputeCapabilities
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.data.download.isCancellable
import dev.yashasvm.mobie.ui.theme.Mobie
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Pre-flight spec sheet for one model, styled as a terminal screen: a verdict box, a memory budget
 * window, storage and artifact key/value grids, and a pinned action dock for download / cancel / run.
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
    onComputeOptionsChange: (ComputeOptions) -> Unit = {},
    onSamplerChange: (SamplerSettings) -> String? = { null },
    onApplyCompute: () -> Unit = {},
    onResetCompute: () -> Unit = {},
    onMemoryRefresh: () -> Unit = {},
    onFreeMemory: (() -> Unit) -> Unit = { it() },
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact ?: return
    MemoryReliefResumeEffect(onMemoryRefresh)
    val resolver = remember { CompatibilityResolver() }
    val backendAdmissions = remember(state.device, state.selectedArtifact, state.computeOptions, state.computeCapabilities, state.selected) {
        AdvancedComputePolicy.backendAdmissions(state.device, state.selectedArtifact, state.selected?.supportsVision == true,
            state.computeOptions, state.computeCapabilities, resolver)
    }
    var confirmWarning by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }
    val downloading = state.download?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
    val gatedWithoutToken = model.gated && !state.tokenConfigured
    // Sections stagger in once; scrolling back up does not replay the entrance.
    val entrance = rememberEntranceTracker()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModelTopBar(path = model.id, runtime = artifact.runtimeLabel, onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "identity") { ModelIdentity(model, artifact, Modifier.entrance(entrance, "identity", 0)) }
            item(key = "verdict") { VerdictPanel(state.compatibility, Modifier.entrance(entrance, "verdict", 1)) }
            item(key = "memory") {
                MemoryPanel(state.compatibility, state.device, Modifier.entrance(entrance, "memory", 2),
                    state.memoryReliefInProgress, onFreeMemory)
            }
            item(key = "compute") {
                ComputeSettingsPanel(
                    backend = state.computeBackend,
                    cpuThreads = state.cpuThreads,
                    hardware = state.hardware,
                    artifactSizeBytes = artifact.sizeBytes,
                    measurements = state.decodeMeasurements,
                    backendAdmissions = backendAdmissions,
                    onBackendChange = onBackendChange,
                    onCpuThreadsChange = onCpuThreadsChange,
                    options = state.computeOptions,
                    sampler = state.sampler,
                    capabilities = state.computeCapabilities,
                    activeCompute = state.activeCompute,
                    needsApply = state.computeNeedsApply,
                    loaded = state.appliedComputeOptions != null,
                    runtimeState = state.runtimeState,
                    downloaded = state.downloadedPath != null,
                    onOptionsChange = onComputeOptionsChange,
                    onSamplerChange = onSamplerChange,
                    onApply = onApplyCompute,
                    onReset = onResetCompute,
                    modifier = Modifier.entrance(entrance, "compute", 3),
                )
            }
            item(key = "storage") {
                StoragePanel(artifact, state.compatibility, state.device, Modifier.entrance(entrance, "storage", 4))
            }
            item(key = "artifact") { ArtifactPanel(artifact, model, Modifier.entrance(entrance, "artifact", 5)) }
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
                if (MemoryReliefDecision.from(state.compatibility, state.device).requiresConfirmation) confirmRun = true
                else onRun(false)
            },
            onCancelDownload = onCancelDownload,
            runOn = runOnSummary(state.computeBackend, backendAdmissions[state.computeBackend]),
            onRunSettings = { scope.launch { listState.animateScrollToItem(COMPUTE_ITEM_INDEX) } },
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
    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("Free memory before loading") },
            text = { Text(state.compatibility?.reason.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { confirmRun = false; onRun(true) }) { Text("Load anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } },
        )
    }
}

/** Position of the run settings window in the model page list; the dock's "change" link scrolls to it. */
private const val COMPUTE_ITEM_INDEX = 3

/** Top bar: back, the model as a path (`model/<id>`), and the runtime tag. */
@Composable
private fun ModelTopBar(path: String, runtime: String, onBack: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(22.dp))
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = dim)) { append("model/") }
                    withStyle(SpanStyle(color = ink)) { append(middleEllipsis(path, 36)) }
                },
                style = Mobie.numeric.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Tag(runtime, icon = LucideR.drawable.lucide_ic_cpu)
        }
        Hairline()
    }
}

/** Repo id as the large heading, author above it, and the facts that matter as tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelIdentity(model: AiModel, artifact: ModelArtifact, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (model.author.isNotBlank()) {
            Text("by ${model.author}", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            model.id,
            style = Mobie.numeric.large,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (model.title.isNotBlank() && model.title != model.id.substringAfter('/')) {
            Text(model.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
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
        Compatibility.INCOMPATIBLE, Compatibility.CONVERSION_REQUIRED -> "x"
        null -> "…"
    }
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, color)
            .background(color.copy(alpha = .06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("[$mark]", style = Mobie.numeric.large, color = color)
            AnimatedContent(
                targetState = when (status) {
                    Compatibility.COMPATIBLE -> "Expected to fit on this phone"
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
private fun MemoryPanel(
    result: CompatibilityResult?,
    device: DeviceProfile?,
    modifier: Modifier = Modifier,
    memoryReliefInProgress: Boolean = false,
    onFreeMemory: (() -> Unit) -> Unit = { it() },
) {
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

    TuiWindow("memory budget", modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                KvRow("Model needs", formatBytes(peak), valueColor = MaterialTheme.colorScheme.onSurface)
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
            MemoryReliefControls(MemoryReliefDecision.from(result, device), memoryReliefInProgress, onFreeMemory)
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
 * One terminal key/value row: dim key on the left, value on the right. Use [stacked] for values
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
            Text(key, style = Mobie.numeric.small, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = Mobie.numeric.small, color = ink)
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(.42f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The swatch column is always reserved so keys line up whether or not they have one.
                Box(Modifier.size(8.dp).background(swatch ?: Color.Transparent))
                Text(key, style = Mobie.numeric.small, color = dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    TuiWindow("storage", modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    TuiWindow("artifact", modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * Where and how the model runs. The primary choice is a short list of Auto / CPU / GPU rows, each
 * carrying its own speed and RAM so they can be compared at a glance; every expert knob lives
 * under [AdvancedComputePanel]. [framed] draws the terminal window; a host that already names the
 * section (the chat's run-settings sheet) passes false.
 */
@Composable
internal fun ComputeSettingsPanel(
    backend: ComputeBackend,
    cpuThreads: Int?,
    onBackendChange: (ComputeBackend) -> Unit,
    onCpuThreadsChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    hardware: HardwareProfile? = null,
    artifactSizeBytes: Long = 0,
    /** Past runs on this phone. Each speed row shows the closest run for the current thread and context settings. */
    measurements: List<DecodeMeasurement> = emptyList(),
    /** Per requested backend: what it would run and its RAM. Empty hides the RAM lines. */
    backendAdmissions: Map<ComputeBackend, BackendAdmission> = emptyMap(),
    options: ComputeOptions = ComputeOptions(backend = backend, cpuThreads = cpuThreads),
    sampler: SamplerSettings = SamplerSettings(),
    capabilities: ComputeCapabilities? = null,
    activeCompute: ActiveCompute? = null,
    needsApply: Boolean = false,
    /** Whether an engine is loaded with saved settings. Pending changes only ask for a reload then. */
    loaded: Boolean = false,
    runtimeState: RuntimeState = RuntimeState.IDLE,
    downloaded: Boolean = false,
    onOptionsChange: (ComputeOptions) -> Unit = { onBackendChange(it.backend); onCpuThreadsChange(it.cpuThreads) },
    onSamplerChange: (SamplerSettings) -> String? = { null },
    onApply: () -> Unit = {},
    onReset: () -> Unit = { onOptionsChange(ComputeOptions()); onSamplerChange(SamplerSettings()) },
    framed: Boolean = true,
) {
    val coreCount = hardware?.cpuCoreCount?.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
    // Speeds match the settings the next load would use, after this artifact's limits.
    val effective = AdvancedComputePolicy.forArtifact(options, coreCount, capabilities)
    val runSettings = DecodeRunSettings(cpuThreads = effective.cpuThreads, contextTokens = effective.contextTokens)
    var inputsValid by remember { mutableStateOf(true) }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(if (framed) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlagHeader("Run on", "speed and memory for this model")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackendOption(
                        label = "Auto",
                        selected = backend == ComputeBackend.AUTO,
                        onClick = { onBackendChange(ComputeBackend.AUTO) },
                        modifier = Modifier.testTag("backend_AUTO"),
                        badge = "recommended",
                    ) {
                        val auto = backendAdmissions[ComputeBackend.AUTO]
                        if (auto != null) {
                            Column(Modifier.testTag("auto_admission"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(autoPickLabel(auto), style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurface)
                                Text(auto.result.reason, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("Tries GPU when it fits, then CPU.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    listOf(ComputeBackend.CPU, ComputeBackend.GPU).forEach { choice ->
                        val match = remember(measurements, choice, runSettings) { matchDecodeMeasurement(measurements, choice, runSettings) }
                        val prediction = remember(hardware, artifactSizeBytes, choice, match?.speed) {
                            DecodeSpeedEstimator.predict(hardware, artifactSizeBytes, choice, match?.speed)
                        }
                        BackendOption(
                            label = choice.name,
                            selected = backend == choice,
                            onClick = { onBackendChange(choice) },
                            modifier = Modifier.testTag("backend_${choice.name}"),
                            trailing = prediction.speed?.let(::decodeSpeedLabel) ?: "Speed unavailable",
                            trailingMuted = prediction.speed == null,
                        ) {
                            Column(Modifier.testTag("decode_speed_${choice.name}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(prediction.speed?.let { decodeSpeedSourceLabel(it, exactSettings = match?.exactSettings ?: true) }
                                    ?: prediction.unavailableReason.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                backendAdmissions[choice]?.result?.let { result ->
                                    backendRamLabel(result)?.let { ram ->
                                        Text(ram, modifier = Modifier.testTag("ram_${choice.name}"), style = Mobie.numeric.tiny,
                                            color = if (result.status == Compatibility.COMPATIBLE) MaterialTheme.colorScheme.onSurfaceVariant
                                            else result.status.signalColor())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            activeCompute?.fallbackReason?.let { reason ->
                Text(reason, modifier = Modifier.testTag("compute_fallback"), style = MaterialTheme.typography.bodySmall,
                    color = Mobie.signals.caution)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hardwareSummary(hardware), style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val details = hardware?.let(::hardwareDetails).orEmpty()
                Text(listOf(details, "GPU support is checked when loading.").filter { it.isNotEmpty() }.joinToString(" · "),
                    style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AdvancedComputePanel(options, sampler, coreCount, capabilities, onOptionsChange, onSamplerChange, onReset) { inputsValid = it }
            ComputeApplyStatus(needsApply, loaded, inputsValid, runtimeState, downloaded, onApply)
        }
    }
    if (framed) {
        TuiWindow(
            "run settings",
            modifier.fillMaxWidth().testTag("compute_settings"),
            meta = activeCompute?.let { "running · ${it.label}" },
            metaColor = Mobie.signals.ready,
        ) { body() }
    } else {
        Box(modifier.fillMaxWidth().testTag("compute_settings")) { body() }
    }
}

/** What happens to the choices above: saved for the next load, in use now, or waiting for a reload. */
@Composable
private fun ComputeApplyStatus(
    needsApply: Boolean,
    loaded: Boolean,
    inputsValid: Boolean,
    runtimeState: RuntimeState,
    downloaded: Boolean,
    onApply: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    if (!loaded) {
        // Nothing is loaded, so there is no reload to ask for; the choices simply wait for the next load.
        Text("Saved. Used next time you load a model.", modifier = Modifier.testTag("saved_settings"),
            style = MaterialTheme.typography.bodySmall, color = dim)
    } else if (needsApply) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .6f))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .06f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Changes need a reload. Your saved conversation will be kept.", modifier = Modifier.testTag("reload_prompt"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            when {
                !inputsValid -> Text("Fix the highlighted fields before applying.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                runtimeState == RuntimeState.GENERATING || runtimeState == RuntimeState.STOPPING ->
                    Text("Wait for the reply to finish, or stop it before applying changes.", style = MaterialTheme.typography.bodySmall, color = dim)
                runtimeState == RuntimeState.LOADING -> Text("Wait for the model to finish loading.", style = MaterialTheme.typography.bodySmall, color = dim)
                !downloaded -> Text("Download this model before applying. These settings are saved for its next load.",
                    style = MaterialTheme.typography.bodySmall, color = dim)
            }
            TermButton("Apply & reload", onClick = onApply, enabled = inputsValid && AdvancedComputePolicy.canApply(runtimeState, downloaded),
                modifier = Modifier.fillMaxWidth().testTag("apply_compute"))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(Mobie.signals.ready, size = 6.dp)
            Text("These settings are in use.", style = MaterialTheme.typography.bodySmall, color = dim)
        }
    }
}

/**
 * One row of the Auto / CPU / GPU choice: a `(•)` radio mark, the option name, its headline figure
 * on the right, and [details] underneath. The whole row is the touch target.
 */
@Composable
private fun BackendOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingMuted: Boolean = false,
    badge: String? = null,
    details: @Composable () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val border by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = .55f),
        tween(MotionShort), label = "backend border")
    val fill by animateColorAsState(if (selected) accent.copy(alpha = .08f) else Color.Transparent,
        tween(MotionShort), label = "backend fill")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(fill)
            .border(1.dp, border)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (selected) "(•)" else "( )", style = Mobie.numeric.small,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = Mobie.numeric.small, color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                if (badge != null) Tag(badge)
                if (trailing != null) {
                    Text(trailing, style = Mobie.numeric.small, maxLines = 1,
                        color = if (trailingMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            details()
        }
    }
}

/** A terminal flag name followed by a plain-language hint, e.g. `--backend  where the model runs`. */
@Composable
private fun FlagHeader(flag: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(flag, style = Mobie.numeric.small, color = MaterialTheme.colorScheme.primary)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A square option button. The selected option is filled, like an inverted tmux cursor. */
@Composable
internal fun FlagChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .background(if (selected) accent else Color.Transparent)
            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Mobie.numeric.small,
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        )
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
    runOn: String,
    onRunSettings: () -> Unit,
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
    val primaryEnabled = !state.memoryReliefInProgress &&
        ((downloaded && MemoryReliefDecision.from(state.compatibility, state.device).canLoad) || canDownload)
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
                visible = downloaded && showPrimary,
                enter = expandVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeIn(tween(MotionMedium)),
                exit = shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)) + fadeOut(tween(MotionShort)),
            ) {
                RunOnLine(runOn, onRunSettings)
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

/** `run on  Auto · GPU · ~3.0 GB RAM   change ›`: what Run will use, one tap from changing it. */
@Composable
private fun RunOnLine(summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClickLabel = "Change run settings", onClick = onClick)
            .testTag("dock_run_on"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_cpu, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("run on", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(summary, style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("change ›", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.primary)
    }
}

/** Shown in the dock when a gated model has no token. Explains the next step without a dead end. */
@Composable
private fun AccessNotice() {
    val caution = Mobie.signals.caution
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, caution.copy(alpha = .5f))
            .padding(12.dp),
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
                    if (active) BlinkingCursor(width = 7.dp, height = 13.dp)
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
