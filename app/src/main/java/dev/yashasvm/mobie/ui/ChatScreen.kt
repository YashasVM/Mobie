package dev.yashasvm.mobie.ui

import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.MemoryAdmissionTier
import dev.yashasvm.mobie.core.runtime.ActiveCompute
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.core.device.RunTelemetry
import org.json.JSONObject
import dev.yashasvm.mobie.ui.theme.Mobie
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local inference chat: header with engine status, live telemetry from the last measured reply,
 * the conversation, and a composer dock. Nothing here talks to the network; prompts go to [onSend],
 * which hands them to the on-device runtime.
 */
@Composable
internal fun ChatScreen(
    state: MobieUiState,
    onSend: (String, String?) -> Boolean,
    onStop: () -> Unit = {},
    onBack: () -> Unit = {},
    onHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onSamplerChange: (SamplerSettings) -> String? = { null },
    onLoadAnyway: () -> Unit = {},
) {
    val model = state.selected ?: return
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var imageCopying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    val entrances = remember { MessageEntrances() }
    entrances.sync(state.messages)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            imageError = null
            imageCopying = true
            try {
                val copiedPath = copyImageToCache(context, uri)
                if (copiedPath == null) {
                    imageError = IMAGE_IMPORT_ERROR
                } else {
                    imagePath?.let { deleteImageFile(it) }
                    imagePath = copiedPath
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                imageError = "Couldn't attach that image. Try another one."
            } finally {
                imageCopying = false
            }
        }
    }
    var followLatest by remember { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !automaticScroll) followLatest = !listState.canScrollForward
        }
    }
    // Follow replies only while the reader stays at the bottom. A new prompt resumes following.
    val streamedLengthBucket = state.messages.lastOrNull()?.let { (it.text.length + it.thinking.length) / 120 } ?: 0
    LaunchedEffect(state.messages.size, streamedLengthBucket) {
        if (state.messages.size > previousMessageCount && state.messages.lastOrNull()?.fromUser == true) {
            followLatest = true
        }
        previousMessageCount = state.messages.size
        if (state.messages.isNotEmpty() && followLatest) {
            automaticScroll = true
            try {
                listState.animateScrollToItem(state.messages.size)
            } finally {
                automaticScroll = false
            }
        }
    }
    val ready = state.runtimeState == RuntimeState.READY
    val generating = state.runtimeState == RuntimeState.GENERATING
    val runtimeLabel = state.selectedArtifact?.runtimeLabel ?: "LiteRT-LM"
    val canSubmit = ready && !imageCopying && prompt.isNotBlank()
    val imageAllowed = model.supportsVision && state.imageInputReady
    // Capability can be lost mid-draft (reload, or stop after uncertain cancellation). Drop the photo
    // here so submit never sends a stale attachment that the ViewModel would reject.
    LaunchedEffect(imageAllowed) {
        if (shouldDropPendingImage(imagePath, imageAllowed)) {
            imagePath?.let { deleteImageFile(it) }
            imagePath = null
            imageError = PHOTO_REMOVED_MESSAGE
        }
    }
    val submit = {
        // Keep the draft unless the ViewModel accepted the prompt.
        if (canSubmit && onSend(prompt, imagePath)) {
            prompt = ""
            imagePath = null
            imageError = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("chat_screen"),
    ) {
        ChatHeader(
            title = model.title,
            runtimeLabel = runtimeLabel,
            runtimeState = state.runtimeState,
            onBack = onBack,
            onHistory = onHistory,
            onNewChat = onNewChat,
        )
        AnimatedVisibility(
            visible = state.runtimeState != RuntimeState.LOADING && state.runtimeState != RuntimeState.ERROR,
            enter = fadeIn(tween(MotionMedium)) + expandVertically(tween(MotionMedium, easing = EmphasizedEase)),
            exit = fadeOut(tween(MotionShort)) + shrinkVertically(tween(MotionShort)),
        ) {
            // Only the run telemetry and sampler fields are passed, so token-only updates skip this panel.
            val latestState by rememberUpdatedState(state)
            RunDetails(
                activeCompute = state.activeCompute,
                stats = state.stats,
                live = state.telemetry,
                sampler = state.sampler,
                runJson = { runJson(latestState) },
                onSamplerChange = onSamplerChange,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.runtimeState == RuntimeState.LOADING -> LoadingPanel(model.title)
                state.runtimeState == RuntimeState.ERROR -> ErrorPanel(
                    state.error ?: "The model could not run on this phone.",
                    onBack = onBack,
                    onLoadAnyway = onLoadAnyway.takeIf {
                        state.compatibility?.memoryTier == MemoryAdmissionTier.FITS_AFTER_RECLAIM &&
                            state.compatibility.status != Compatibility.INCOMPATIBLE
                    },
                )
                state.messages.isEmpty() -> EmptyChat(
                    model = model,
                    imageInputReady = state.imageInputReady,
                    onStarter = { starter ->
                        prompt = starter
                        if (ready) inputFocus.requestFocus()
                    },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("chat_messages"),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(
                        state.messages,
                        key = { index, _ -> index },
                        contentType = { _, message -> if (message.fromUser) "user" else "assistant" },
                    ) { index, message ->
                        // Decided once per item: restored history appears at rest, live messages animate in.
                        val animate = remember { entrances.claim(index) }
                        val itemModifier = Modifier
                            .animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = tween(MotionShort))
                            .messageEntrance(animate, fromUser = message.fromUser)
                        if (message.fromUser) {
                            UserMessage(message, itemModifier)
                        } else {
                            AssistantMessage(
                                message = message,
                                modelTitle = model.title,
                                streaming = generating && index == state.messages.lastIndex,
                                modifier = itemModifier,
                            )
                        }
                    }
                    item(key = "end", contentType = "end") { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        ComposerDock(
            prompt = prompt,
            onPromptChange = { prompt = it },
            runtimeState = state.runtimeState,
            runtimeLabel = runtimeLabel,
            supportsVision = imageAllowed,
            imagePath = imagePath,
            imageCopying = imageCopying,
            canSubmit = canSubmit,
            error = state.error.takeIf { state.runtimeState != RuntimeState.ERROR },
            imageError = imageError,
            focusRequester = inputFocus,
            onAttach = { imagePicker.launch("image/*") },
            onRemoveImage = {
                val stale = imagePath
                imagePath = null
                scope.launch { stale?.let { deleteImageFile(it) } }
            },
            onSubmit = submit,
            onStop = onStop,
        )
    }
}

internal const val PHOTO_REMOVED_MESSAGE = "Photo removed: this model can't read images right now."

/** A pending photo must be dropped as soon as the loaded model can no longer read images. */
internal fun shouldDropPendingImage(imagePath: String?, imageAllowed: Boolean): Boolean =
    imagePath != null && !imageAllowed

/**
 * Decides which messages play an entrance animation. Messages that arrive live (a send adds the
 * prompt and an empty reply) animate; a restored or replaced conversation appears at rest so
 * opening history doesn't replay a long cascade.
 */
private class MessageEntrances {
    private var baseline = -1
    private var lastSize = 0
    private var head: ChatMessage? = null
    private val played = mutableSetOf<Int>()

    fun sync(messages: List<ChatMessage>) {
        val first = messages.firstOrNull()
        val replaced = baseline < 0 ||
            messages.size < lastSize ||
            messages.size - lastSize > 2 ||
            (lastSize > 0 && first !== head)
        if (replaced) {
            baseline = messages.size
            played.clear()
        }
        lastSize = messages.size
        head = first
    }

    fun claim(index: Int): Boolean = index >= baseline && played.add(index)
}

/**
 * Your messages slide in from the right, where their bubble sits; replies fade up a beat later. Both are short so streaming never waits on them.
 */
private fun Modifier.messageEntrance(animate: Boolean, fromUser: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            if (!fromUser) delay(90)
            progress.animateTo(1f, tween(MotionShort + 60, easing = EmphasizedEase))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        if (fromUser) {
            translationX = (1f - p) * 12.dp.toPx()
        } else {
            translationY = (1f - p) * 8.dp.toPx()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header and telemetry

private data class EngineStatus(val text: String, val pulsing: Boolean)

private fun engineStatus(runtimeState: RuntimeState, runtimeLabel: String): EngineStatus = when (runtimeState) {
    RuntimeState.IDLE -> EngineStatus("Not loaded · $runtimeLabel", false)
    RuntimeState.LOADING -> EngineStatus("Loading weights · $runtimeLabel", true)
    RuntimeState.READY -> EngineStatus("Ready · on-device · $runtimeLabel", false)
    RuntimeState.GENERATING -> EngineStatus("Generating · on-device · $runtimeLabel", true)
    RuntimeState.STOPPING -> EngineStatus("Stopping · $runtimeLabel", true)
    RuntimeState.ERROR -> EngineStatus("Error · $runtimeLabel", false)
}

@Composable
private fun RuntimeState.signal() = when (this) {
    RuntimeState.READY, RuntimeState.GENERATING -> Mobie.signals.ready
    RuntimeState.LOADING, RuntimeState.STOPPING -> Mobie.signals.caution
    RuntimeState.ERROR -> Mobie.signals.blocked
    RuntimeState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ChatHeader(
    title: String,
    runtimeLabel: String,
    runtimeState: RuntimeState,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
) {
    val status = engineStatus(runtimeState, runtimeLabel)
    val dotColor by animateColorAsState(runtimeState.signal(), tween(MotionMedium), label = "engine dot")
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        // tmux-style top bar: the app and the window in view, plus the runtime doing the work.
        StatusLine(
            segments = listOf(StatusSegment("mobie"), StatusSegment("chat")),
            selected = 1,
            trailing = runtimeLabel,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StatusDot(dotColor, pulsing = status.pulsing, size = 7.dp)
                    AnimatedContent(
                        targetState = status.text,
                        transitionSpec = {
                            fadeIn(tween(MotionMedium, delayMillis = 60)) togetherWith fadeOut(tween(MotionShort)) using
                                SizeTransform(clip = false)
                        },
                        label = "engine status",
                    ) { text ->
                        Text(
                            text,
                            style = Mobie.numeric.tiny,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(onClick = onHistory) {
                LucideIcon(LucideR.drawable.lucide_ic_history, "Chat history", Modifier.size(22.dp))
            }
            IconButton(onClick = onNewChat) {
                LucideIcon(LucideR.drawable.lucide_ic_plus, "Start a new chat", Modifier.size(22.dp))
            }
        }
        Hairline()
    }
}

/** Live callbacks are SDK chunks; final token rates come from LiteRT benchmark data. */
@Composable
private fun RunDetails(
    activeCompute: ActiveCompute?,
    stats: InferenceStats?,
    live: RunTelemetry,
    sampler: SamplerSettings,
    runJson: () -> String,
    onSamplerChange: (SamplerSettings) -> String?,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var temperature by remember(sampler) { mutableStateOf(sampler.temperature.toString()) }
    var topK by remember(sampler) { mutableStateOf(sampler.topK.toString()) }
    var topP by remember(sampler) { mutableStateOf(sampler.topP.toString()) }
    var seed by remember(sampler) { mutableStateOf(sampler.seed.toString()) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val decode = stats?.tokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f tok/s", it) }
        ?: live.liveSdkChunksPerSecond?.let { String.format(Locale.US, "%.1f SDK chunks/s", it) }
        ?: "Waiting for output"
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        activeCompute?.let { selected ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Running on ${selected.label}",
                    style = Mobie.numeric.tiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selected.fallbackReason?.let { reason ->
                    Text(reason, style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // One readout line, like a status bar cell: the section name on the left, live speed on the right.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "run details ${if (expanded) "▾" else "▸"}",
                style = Mobie.numeric.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                decode,
                style = Mobie.numeric.small,
                color = if (stats?.tokensPerSecond?.let { it > 0 } == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val first = live.firstChunkMs ?: stats?.timeToFirstTokenMs?.takeIf { it > 0 }
                Text("First SDK chunk: ${first?.let(::formatMillis) ?: "—"}", style = Mobie.numeric.tiny)
                Text("Prefill: ${stats?.prefillTokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f tok/s (%d tokens)", it, stats.prefillTokenCount) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Decode: ${stats?.tokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f tok/s (%d tokens)", it, stats.decodeTokenCount) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("App PSS: ${live.pssBytes?.let(::formatBytes) ?: "—"} · Thermal status: ${live.thermalStatus?.toString() ?: "—"}", style = Mobie.numeric.tiny)
                Text("Battery current: ${live.batteryCurrentMilliAmps?.let { String.format(Locale.US, "%.0f mA", it) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Estimated whole-device energy/token: ${live.estimatedEnergyPerTokenMilliWh?.let { String.format(Locale.US, "%.5f mWh", it) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Live speed uses SDK chunks; final token counts use LiteRT.", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TermButton(
                    "Copy run as JSON",
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Run JSON", runJson()))) } },
                    style = TermButtonStyle.Ghost,
                    enabled = stats != null || live.sdkChunks > 0,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                SectionLabel("Sampling", Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(temperature, { temperature = it }, label = { Text("Temperature") }, singleLine = true, modifier = Modifier.width(120.dp), shape = RectangleShape)
                    OutlinedTextField(topK, { topK = it }, label = { Text("Top-k") }, singleLine = true, modifier = Modifier.width(100.dp), shape = RectangleShape)
                    OutlinedTextField(topP, { topP = it }, label = { Text("Top-p") }, singleLine = true, modifier = Modifier.width(100.dp), shape = RectangleShape)
                    OutlinedTextField(seed, { seed = it }, label = { Text("Seed") }, singleLine = true, modifier = Modifier.width(120.dp), shape = RectangleShape)
                }
                TermButton(
                    "Apply sampling",
                    onClick = {
                        val parsed = listOfNotNull(temperature.toDoubleOrNull(), topK.toIntOrNull(), topP.toDoubleOrNull(), seed.toIntOrNull())
                        settingsError = if (parsed.size != 4) "Enter valid numbers for all sampling fields." else {
                            onSamplerChange(SamplerSettings(temperature.toDouble(), topK.toInt(), topP.toDouble(), seed.toInt()))
                        }
                    },
                    style = TermButtonStyle.Ghost,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                settingsError?.let { Text("[!] $it", style = Mobie.numeric.tiny, color = Mobie.signals.blocked) }
            }
        }
        Hairline()
    }
}

private fun runJson(state: MobieUiState): String {
    val stats = state.stats
    val live = state.telemetry
    fun value(value: Any?): Any = value ?: JSONObject.NULL
    return JSONObject().apply {
        put("modelId", value(state.selected?.id))
        put("artifact", value(state.selectedArtifact?.fileName))
        put("compute", value(state.activeCompute?.label))
        put("sampler", JSONObject().put("temperature", state.sampler.temperature).put("topK", state.sampler.topK)
            .put("topP", state.sampler.topP).put("seed", state.sampler.seed))
        put("firstSdkChunkMs", value(live.firstChunkMs))
        put("sdkChunks", live.sdkChunks)
        put("liveSdkChunksPerSecond", value(live.liveSdkChunksPerSecond))
        put("prefillTokensPerSecond", value(stats?.prefillTokensPerSecond))
        put("prefillTokenCount", value(stats?.prefillTokenCount))
        put("decodeTokensPerSecond", value(stats?.tokensPerSecond))
        put("decodeTokenCount", value(stats?.decodeTokenCount))
        put("appPssBytes", value(live.pssBytes))
        put("thermalStatus", value(live.thermalStatus))
        put("batteryCurrentMilliAmps", value(live.batteryCurrentMilliAmps))
        put("estimatedWholeDeviceEnergyMilliWh", value(live.estimatedEnergyMilliWh))
        put("estimatedWholeDeviceEnergyPerTokenMilliWh", value(live.estimatedEnergyPerTokenMilliWh))
    }.toString(2)
}

@Composable
private fun TelemetryValue(label: String?, value: String, unit: String? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
        RollingText(
            value,
            style = Mobie.numeric.small,
            color = if (value == "—") muted else MaterialTheme.colorScheme.onSurface,
        )
        if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Non-conversation states

@Composable
private fun LoadingPanel(modelTitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .appear()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MobieMark(size = 48.dp)
            Text(
                "Loading $modelTitle",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            IndeterminateTrack(MaterialTheme.colorScheme.primary)
            Text(
                "The first load can take a while. Later loads are usually faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A short rounded segment sweeping across a thin track while the model loads. */
@Composable
private fun IndeterminateTrack(color: Color) {
    val track = Mobie.signals.track
    val transition = rememberInfiniteTransition(label = "load track")
    // Read in the draw phase so the sweep never recomposes the panel.
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EmphasizedEase), RepeatMode.Restart),
        label = "load track position",
    )
    Canvas(Modifier.fillMaxWidth().height(6.dp).clip(PillShape)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val segment = size.width * .28f
        val start = (size.width + segment) * position - segment
        val left = start.coerceAtLeast(0f)
        val right = (start + segment).coerceAtMost(size.width)
        if (right > left) {
            drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(right - left, size.height), cornerRadius = radius)
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onBack: () -> Unit, onLoadAnyway: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.fillMaxWidth().widthIn(max = 420.dp).appear()) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Chat couldn't start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TermButton("Back", onClick = onBack, style = TermButtonStyle.Ghost)
                    // Only offered when the model fits once Android frees memory, matching the model
                    // screen's "Load anyway"; models that can never fit get no override.
                    if (onLoadAnyway != null) TermButton("Load anyway", onClick = onLoadAnyway)
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(model: AiModel, imageInputReady: Boolean, onStarter: (String) -> Unit) {
    val starters = remember(model.supportsVision, imageInputReady) {
        buildList {
            add("Explain how on-device AI works")
            add("Write a short poem about the sea")
            add("Summarize the pros and cons of electric cars")
            if (model.supportsVision && imageInputReady) add("Describe an image") else add("Help me plan a weekend trip")
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MobieMark(Modifier.appear(), size = 40.dp)
            Text(
                "What can I help with?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.appear(delayMillis = 60).semantics { heading() },
            )
            Text(
                model.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.appear(delayMillis = 90),
            )
            Column(Modifier.padding(top = 4.dp).appear(delayMillis = 120), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckLine(
                    if (model.supportsVision && imageInputReady) {
                        "Chat about text or an attached photo on this phone. Image generation isn't available."
                    } else if (model.supportsVision) {
                        "This phone loaded text chat only. Photo input is unavailable for this session."
                    } else {
                        "Chat about text on this phone. Nothing you type leaves it."
                    },
                    if (model.supportsVision && !imageInputReady) "!" else "✓",
                    if (model.supportsVision && !imageInputReady) Mobie.signals.caution else Mobie.signals.ready,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Try asking", Modifier.appear(delayMillis = 160))
            starters.forEachIndexed { index, starter ->
                val vision = starter == "Describe an image"
                Panel(
                    Modifier.fillMaxWidth().appear(delayMillis = 200 + index * 60),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = { onStarter(if (vision) "Describe this image in detail." else starter) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(starter, style = MaterialTheme.typography.bodyLarge)
                            if (vision) {
                                Text(
                                    "Attach a photo with the image button first",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LucideIcon(
                            if (vision) LucideR.drawable.lucide_ic_image else LucideR.drawable.lucide_ic_arrow_up_right,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Messages

/** Your message: a rounded bubble aligned to the right, like any chat app. */
@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(start = 48.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.imagePath?.let { AttachedImage(it) }
        if (message.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, UserBubbleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private val UserBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)

@Composable
private fun AttachedImage(path: String) {
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = decodeThumbnail(context, path) }
    val image = bitmap
    if (image != null) {
        Image(
            image,
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Mobie.signals.hairline, RoundedCornerShape(16.dp)),
        )
    } else {
        Tag("Image attached", icon = LucideR.drawable.lucide_ic_image)
    }
}

/** The model's reply: full-width plain text with no bubble, so long answers read like a page. */
@Composable
private fun AssistantMessage(message: ChatMessage, modelTitle: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val waitingForAnswer = streaming && message.text.isBlank()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MobieMark(size = 20.dp)
            Text(
                modelTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (message.interrupted) Tag("Interrupted", color = Mobie.signals.caution)
        }
        if (message.thinking.isNotBlank()) {
            ThinkingPanel(message.thinking, active = waitingForAnswer)
        }
        if (message.text.isNotBlank()) {
            // No animateContentSize here: it would fight the bucketed auto-scroll while tokens stream.
            SelectionContainer(Modifier.widthIn(max = 720.dp)) {
                MarkdownText(message.text)
            }
        }
        if (streaming) {
            if (message.text.isBlank() && message.thinking.isBlank()) {
                Row(
                    Modifier
                        .heightIn(min = 24.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TypingIndicator(MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Preparing a reply…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (message.text.isNotBlank()) {
                StreamingCaret()
            }
        } else if (message.text.isNotBlank()) {
            ReplyFooter(message)
        }
    }
}

@Composable
private fun ReplyFooter(message: ChatMessage) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val footer = message.stats?.let(::replyStatsLine).orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(offsetDp = 6f)) {
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Reply", message.text)))
                    copied = true
                }
            },
            modifier = Modifier.size(48.dp).offset(x = (-12).dp),
        ) {
            LucideIcon(
                if (copied) LucideR.drawable.lucide_ic_check else LucideR.drawable.lucide_ic_copy,
                if (copied) "Copied" else "Copy reply",
                Modifier.size(18.dp),
                tint = if (copied) Mobie.signals.ready else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            footer,
            style = Mobie.numeric.tiny,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).offset(x = (-8).dp),
        )
    }
}

/** "128 tokens · 18.4 tokens/s · first token 0.9 s · 4.1 s total", listing only measured fields. */
private fun replyStatsLine(stats: InferenceStats): String? = buildList {
    if (stats.decodeTokenCount > 0) add("${stats.decodeTokenCount} tokens")
    if (stats.tokensPerSecond > 0) add(String.format(Locale.US, "%.1f tokens/s", stats.tokensPerSecond))
    if (stats.timeToFirstTokenMs > 0) add("first token ${formatMillis(stats.timeToFirstTokenMs)}")
    if (stats.totalGenerationMs > 0) add("${formatMillis(stats.totalGenerationMs)} total")
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** The dot shown after a streaming reply. Pulses in the draw phase, not in composition. */
@Composable
private fun StreamingCaret() {
    BlinkingCursor(
        Modifier.semantics { contentDescription = "Generating" },
        color = MaterialTheme.colorScheme.onSurface,
        width = 10.dp,
        height = 20.dp,
    )
}

/** Three round dots rising in sequence while the engine prepares its first token. */
@Composable
private fun TypingIndicator(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "typing phase",
    )
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { dot ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        val local = ((phase - dot * .16f) % 1f + 1f) % 1f
                        val lift = if (local < .5f) sin(local * 2f * PI.toFloat()) else 0f
                        translationY = -lift * 4.dp.toPx()
                        alpha = .35f + .65f * lift
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun ThinkingPanel(thinking: String, active: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, tween(MotionMedium, easing = EmphasizedEase), label = "chevron")
    val label = if (active) "Thinking…" else "Show reasoning · ${formatChars(thinking.length)} characters"
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, Mobie.signals.hairline, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide thinking" else "Show thinking",
                    role = Role.Button,
                ) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active) {
                StatusDot(Mobie.signals.thinking, pulsing = true, size = 8.dp)
            } else {
                LucideIcon(LucideR.drawable.lucide_ic_lightbulb, null, Modifier.size(16.dp), tint = color)
            }
            Text(
                if (expanded && !active) "Hide reasoning" else label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { if (active) liveRegion = LiveRegionMode.Polite },
            )
            LucideIcon(
                LucideR.drawable.lucide_ic_chevron_right,
                null,
                Modifier.size(16.dp).graphicsLayer { rotationZ = chevron },
                tint = color,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(MotionMedium)) + expandVertically(tween(MotionMedium, easing = EmphasizedEase)),
            exit = fadeOut(tween(MotionShort)) + shrinkVertically(tween(MotionMedium, easing = EmphasizedEase)),
        ) {
            SelectionContainer {
                Text(
                    thinking,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                )
            }
        }
    }
}

private fun formatChars(count: Int): String =
    if (count >= 1000) String.format(Locale.US, "%.1fk", count / 1000.0) else count.toString()

@Composable
private fun MarkdownText(text: String) {
    val blocks = remember(text) { parseChatMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeFamily = Mobie.numeric.code.fontFamily
    val codeStyle = remember(codeBackground, codeFamily) {
        SpanStyle(fontFamily = codeFamily, fontSize = .88.em, background = codeBackground)
    }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val previous = blocks.getOrNull(index - 1)
            val gap = when {
                previous == null -> 0.dp
                previous is MdBlock.ListItem && block is MdBlock.ListItem -> 6.dp
                block is MdBlock.Heading -> 20.dp
                else -> 12.dp
            }
            Box(Modifier.padding(top = gap)) { MarkdownBlock(block, codeStyle) }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock, codeStyle: SpanStyle) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is MdBlock.Paragraph -> Text(
            remember(block, codeStyle) { block.spans.toAnnotated(codeStyle) },
            style = MaterialTheme.typography.bodyLarge,
            color = onSurface,
        )
        is MdBlock.Heading -> Text(
            remember(block, codeStyle) { block.spans.toAnnotated(codeStyle) },
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            color = onSurface,
            modifier = Modifier.semantics { heading() },
        )
        is MdBlock.ListItem -> Row(Modifier.padding(start = (block.depth * 18).dp)) {
            Text(
                block.marker,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (block.ordered) 28.dp else 18.dp).padding(end = 8.dp),
                maxLines = 1,
            )
            Text(
                remember(block, codeStyle) { block.spans.toAnnotated(codeStyle) },
                style = MaterialTheme.typography.bodyLarge,
                color = onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        is MdBlock.Code -> CodeBlock(block)
        MdBlock.Rule -> Hairline(Modifier.padding(vertical = 4.dp))
    }
}

/** A rounded code card: language and a copy action on top, then the source in monospace. */
@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, Mobie.signals.hairline, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                block.language.ifBlank { "text" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Copy code", role = Role.Button) {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Code", block.code)))
                            copied = true
                        }
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LucideIcon(
                    if (copied) LucideR.drawable.lucide_ic_check else LucideR.drawable.lucide_ic_copy,
                    null,
                    Modifier.size(14.dp),
                    tint = if (copied) Mobie.signals.ready else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (copied) Mobie.signals.ready else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            block.code.ifEmpty { " " },
            style = Mobie.numeric.code,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private fun List<MdSpan>.toAnnotated(codeStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        when {
            span.code && span.bold -> withStyle(codeStyle.copy(fontWeight = FontWeight.Bold)) { append(span.text) }
            span.code -> withStyle(codeStyle) { append(span.text) }
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            else -> append(span.text)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Composer

@Composable
private fun ComposerDock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    runtimeState: RuntimeState,
    runtimeLabel: String,
    supportsVision: Boolean,
    imagePath: String?,
    imageCopying: Boolean,
    canSubmit: Boolean,
    error: String?,
    imageError: String?,
    focusRequester: FocusRequester,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    val ready = runtimeState == RuntimeState.READY
    val generating = runtimeState == RuntimeState.GENERATING
    val placeholder = when (runtimeState) {
        RuntimeState.READY -> "Ask anything"
        RuntimeState.LOADING -> "Loading model…"
        RuntimeState.GENERATING -> "Generating…"
        RuntimeState.STOPPING -> "Stopping…"
        RuntimeState.ERROR -> "Model unavailable"
        RuntimeState.IDLE -> "Model not loaded"
    }
    val fieldShape = RoundedCornerShape(24.dp)
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val fieldBorder by animateColorAsState(
        when {
            ready && focused -> MaterialTheme.colorScheme.outline
            ready -> MaterialTheme.colorScheme.outlineVariant
            else -> Mobie.signals.hairline
        },
        tween(MotionMedium),
        label = "composer border",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = imageCopying,
                enter = fadeIn(tween(MotionShort)) + expandVertically(tween(MotionShort)),
                exit = fadeOut(tween(MotionShort)) + shrinkVertically(tween(MotionShort)),
            ) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 7.dp)
                    Text("Attaching image…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(
                visible = imagePath != null,
                enter = fadeIn(tween(MotionMedium)) + expandVertically(tween(MotionMedium, easing = EmphasizedEase)),
                exit = fadeOut(tween(MotionShort)) + shrinkVertically(tween(MotionShort)),
            ) {
                Row(
                    Modifier
                        .clip(PillShape)
                        .border(1.dp, Mobie.signals.hairline, PillShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LucideIcon(LucideR.drawable.lucide_ic_image, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Image attached", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onRemoveImage, modifier = Modifier.size(44.dp)) {
                        LucideIcon(LucideR.drawable.lucide_ic_x, "Remove image", Modifier.size(16.dp))
                    }
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.appear(offsetDp = 6f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            imageError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.appear(offsetDp = 6f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(fieldShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer, fieldShape)
                        .border(1.dp, fieldBorder, fieldShape)
                        .padding(start = if (supportsVision) 4.dp else 18.dp, end = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (supportsVision) {
                        IconButton(onClick = onAttach, enabled = ready && !imageCopying) {
                            LucideIcon(LucideR.drawable.lucide_ic_image, "Attach image", Modifier.size(20.dp))
                        }
                    }
                    BasicTextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        enabled = ready,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = if (ready) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                        interactionSource = fieldInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .focusRequester(focusRequester)
                            .testTag("chat_input"),
                        decorationBox = { inner ->
                            Box {
                                if (prompt.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                SendStopButton(generating = generating, canSubmit = canSubmit, onSubmit = onSubmit, onStop = onStop)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(LucideR.drawable.lucide_ic_lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Stays on this phone · $runtimeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One button that morphs between send (arrow) and stop (square). Its fill animates between the
 * disabled, ready, and generating states, and it springs up slightly once there is something to send.
 */
@Composable
private fun SendStopButton(generating: Boolean, canSubmit: Boolean, onSubmit: () -> Unit, onStop: () -> Unit) {
    val active = generating || canSubmit
    val container by animateColorAsState(
        when {
            generating -> MaterialTheme.colorScheme.onSurface
            canSubmit -> MaterialTheme.colorScheme.primary
            else -> Mobie.signals.track
        },
        tween(MotionMedium),
        label = "send container",
    )
    val content by animateColorAsState(
        when {
            generating -> MaterialTheme.colorScheme.surface
            canSubmit -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(MotionMedium),
        label = "send content",
    )
    val scale by animateFloatAsState(
        if (active) 1f else .9f,
        spring(dampingRatio = .55f, stiffness = Spring.StiffnessMediumLow),
        label = "send scale",
    )
    val interaction = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = { if (generating) onStop() else onSubmit() },
        enabled = active,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pressScale(interaction, pressedScale = .9f),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        interactionSource = interaction,
    ) {
        AnimatedContent(
            targetState = generating,
            transitionSpec = {
                (fadeIn(tween(MotionShort)) + scaleIn(tween(MotionMedium, easing = EmphasizedEase), initialScale = .4f)) togetherWith
                    (fadeOut(tween(MotionShort)) + scaleOut(tween(MotionShort), targetScale = .4f)) using
                    SizeTransform(clip = false)
            },
            label = "send stop",
        ) { stop ->
            val rotation by transition.animateFloat(
                transitionSpec = { tween(MotionMedium, easing = EmphasizedEase) },
                label = "send stop rotation",
            ) { phase -> if (phase == EnterExitState.Visible) 0f else if (stop) -90f else 90f }
            val iconModifier = Modifier.graphicsLayer { rotationZ = rotation }
            if (stop) {
                LucideIcon(LucideR.drawable.lucide_ic_square, "Stop generation", iconModifier.size(16.dp))
            } else {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_up, "Send message", iconModifier.size(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Files

/** Decodes a downsampled preview of a cached chat image off the main thread; null if unreadable. */
internal suspend fun decodeThumbnail(context: Context, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.isFile || !file.canonicalFile.toPath().startsWith(context.cacheDir.canonicalFile.toPath())) return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > 320L * sample) sample *= 2
    // BitmapFactory returns null (rather than throwing) for undecodable input; the caller then shows a tag.
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
private const val MAX_IMAGE_PIXELS = 25_000_000L
private const val MAX_IMAGE_DIMENSION = 16_384
private const val IMAGE_IMPORT_ERROR = "Choose a JPEG, PNG, or WebP image under 20 MB and 25 megapixels."

/** Deletes a cached chat image off the main thread; the delete finishes even if the caller leaves composition. */
internal suspend fun deleteImageFile(path: String) {
    withContext(NonCancellable + Dispatchers.IO) { File(path).delete() }
}

/** Copies recognized image bytes without resizing the image used for inference. */
internal suspend fun copyImageToCache(context: Context, uri: Uri): String? {
    var image: File? = null
    var copied = false
    try {
        return withContext(Dispatchers.IO) {
            val destination = File.createTempFile("chat-image-", ".bin", context.cacheDir)
            image = destination
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) return@withContext null
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, bounds)
            if (
                bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp") ||
                bounds.outWidth !in 1..MAX_IMAGE_DIMENSION ||
                bounds.outHeight !in 1..MAX_IMAGE_DIMENSION ||
                bounds.outWidth.toLong() * bounds.outHeight > MAX_IMAGE_PIXELS
            ) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > 320L * sample) sample *= 2
            val preview = BitmapFactory.decodeFile(
                destination.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@withContext null
            preview.recycle()
            currentCoroutineContext().ensureActive()
            destination.absolutePath
        }.also { copied = it != null }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return null
    } finally {
        if (!copied) withContext(NonCancellable + Dispatchers.IO) { image?.delete() }
    }
}
