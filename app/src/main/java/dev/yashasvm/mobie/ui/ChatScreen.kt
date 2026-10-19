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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onMenu: () -> Unit = {},
    onBack: () -> Unit = {},
    onHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onComputeSettings: () -> Unit = {},
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
    // Scroll in buckets of streamed characters instead of on every token.
    val streamedLengthBucket = state.messages.lastOrNull()?.let { (it.text.length + it.thinking.length) / 120 } ?: 0
    LaunchedEffect(state.messages.size, streamedLengthBucket) {
        // The list ends with a spacer item at index messages.size; scrolling to it reveals the bottom.
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
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
            onMenu = onMenu,
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
                runJson = { runJson(latestState) },
                onComputeSettings = onComputeSettings,
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
                state.messages.isEmpty() -> EmptyChat(model = model, imageInputReady = state.imageInputReady)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
 * Prompts type in from the left like a typed command; replies print in a beat later, one step
 * lower. Both are short so streaming never waits on them.
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
            translationX = (p - 1f) * 12.dp.toPx()
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
    onMenu: () -> Unit,
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
            trailing = "offline · on this phone",
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.testTag("open_menu")) {
                LucideIcon(LucideR.drawable.lucide_ic_menu, "Open menu", Modifier.size(22.dp))
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

/**
 * The strip under the chat header. Left: what the model is running on, as a chip that opens the
 * run settings for this model. Right: the latest speed, which expands the run details. Live
 * callbacks are SDK chunks; final token rates come from LiteRT benchmark data.
 */
@Composable
private fun RunDetails(
    activeCompute: ActiveCompute?,
    stats: InferenceStats?,
    live: RunTelemetry,
    runJson: () -> String,
    onComputeSettings: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val measured = stats?.tokensPerSecond?.takeIf { it > 0 }
    val decode = measured?.let { String.format(Locale.US, "%.1f tok/s", it) }
        ?: live.liveSdkChunksPerSecond?.let { String.format(Locale.US, "%.1f/s live", it) }
        ?: "run details"
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComputeChip(activeCompute, onComputeSettings)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide run details" else "Show run details") { expanded = !expanded }
                    .padding(horizontal = 8.dp)
                    .testTag("run_details_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    decode,
                    style = Mobie.numeric.small,
                    color = if (measured != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(if (expanded) "▾" else "▸", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        activeCompute?.fallbackReason?.let { reason ->
            Text(
                reason,
                style = Mobie.numeric.tiny,
                color = Mobie.signals.caution,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("chat_compute_fallback"),
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val first = live.firstChunkMs ?: stats?.timeToFirstTokenMs?.takeIf { it > 0 }
                Text("First text: ${first?.let(::formatMillis) ?: "—"}", style = Mobie.numeric.tiny)
                Text("Reading prompt: ${stats?.prefillTokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f tok/s (%d tokens)", it, stats.prefillTokenCount) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Writing reply: ${stats?.tokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f tok/s (%d tokens)", it, stats.decodeTokenCount) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("App memory: ${live.pssBytes?.let(::formatBytes) ?: "—"} · Thermal status: ${live.thermalStatus?.toString() ?: "—"}", style = Mobie.numeric.tiny)
                Text("Battery current: ${live.batteryCurrentMilliAmps?.let { String.format(Locale.US, "%.0f mA", it) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Energy per token (whole phone, estimate): ${live.estimatedEnergyPerTokenMilliWh?.let { String.format(Locale.US, "%.5f mWh", it) } ?: "—"}", style = Mobie.numeric.tiny)
                Text("Live speed counts text chunks as they arrive; final token counts come from LiteRT.", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TermButton(
                    "Copy run as JSON",
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Run JSON", runJson()))) } },
                    style = TermButtonStyle.Ghost,
                    enabled = stats != null || live.sdkChunks > 0,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Hairline()
    }
}

/**
 * `[≡ GPU ›]`: the active backend as a bordered chip. One tap opens the run settings for the loaded
 * model, so switching CPU / GPU or tuning Advanced never needs a trip back to the model page.
 */
@Composable
private fun ComputeChip(activeCompute: ActiveCompute?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (pressed) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val label = activeCompute?.label ?: "Run settings"
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .background(if (pressed) accent else Color.Transparent)
            .border(1.dp, if (pressed) accent else MaterialTheme.colorScheme.outline)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open run settings", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = activeCompute?.let { "Run settings. Running on ${it.label}" } ?: "Run settings"
            }
            .padding(horizontal = 10.dp)
            .testTag("open_compute_settings"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_sliders_horizontal, null, Modifier.size(14.dp), tint = if (pressed) ink else accent)
        Text(label, style = Mobie.numeric.small, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("›", style = Mobie.numeric.small, color = if (pressed) ink else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun runJson(state: MobieUiState): String {
    val stats = state.stats
    val live = state.telemetry
    val sampler = state.appliedSampler ?: state.sampler
    fun value(value: Any?): Any = value ?: JSONObject.NULL
    return JSONObject().apply {
        put("modelId", value(state.selected?.id))
        put("artifact", value(state.selectedArtifact?.fileName))
        put("compute", value(state.activeCompute?.label))
        put("sampler", JSONObject().put("temperature", sampler.temperature).put("topK", sampler.topK)
            .put("topP", sampler.topP).put("seed", sampler.seed))
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
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        TuiWindow(
            title = "loading weights",
            meta = "wait",
            borderColor = Mobie.signals.caution.copy(alpha = .45f),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).appear(),
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("[~] Loading $modelTitle into memory", style = Mobie.numeric.small, color = Mobie.signals.caution)
                IndeterminateTrack(Mobie.signals.caution)
                Text(
                    "The first load can take a while. Later loads are usually faster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A short block sliding across a thin track, like a cursor scanning a progress bar. */
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
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRect(track)
        val segment = size.width * .22f
        val start = (size.width + segment) * position - segment
        val left = start.coerceAtLeast(0f)
        val right = (start + segment).coerceAtMost(size.width)
        if (right > left) {
            drawRect(color, topLeft = Offset(left, 0f), size = Size(right - left, size.height))
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onBack: () -> Unit, onLoadAnyway: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        TuiWindow(
            title = "chat",
            meta = "not started",
            borderColor = Mobie.signals.blocked.copy(alpha = .5f),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).appear(),
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("[!] Chat couldn't start", style = Mobie.numeric.small, color = Mobie.signals.blocked)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onLoadAnyway != null) {
                    Text(
                        "Close a few other apps, then tap load anyway to try again.",
                        style = Mobie.numeric.tiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TermButton("back", onClick = onBack, style = TermButtonStyle.Ghost)
                // Only offered when the model fits once Android frees memory, matching the model
                // screen's "Load anyway"; models that can never fit get no override.
                if (onLoadAnyway != null) TermButton("load anyway", onClick = onLoadAnyway)
            }
        }
    }
}

@Composable
private fun EmptyChat(model: AiModel, imageInputReady: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        // A quiet greeting; the message box below is the only call to action.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PromptLine("Ready to chat", Modifier.appear())
            Text(
                model.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp).appear(delayMillis = 60),
            )
            Column(Modifier.padding(start = 20.dp).appear(delayMillis = 120), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckLine("Model loaded. Type a message below to begin.", "✓", Mobie.signals.ready)
                CheckLine(
                    if (model.supportsVision && imageInputReady) {
                        "Chat about text or an attached photo on this phone. Image generation isn't available."
                    } else if (model.supportsVision) {
                        "This phone loaded text chat only. Photo input is unavailable for this session."
                    } else {
                        "Chat about text on this phone. Nothing you type leaves it."
                    },
                    if (model.supportsVision && !imageInputReady) "!" else "i",
                    if (model.supportsVision && !imageInputReady) Mobie.signals.caution else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Messages

/** A prompt typed at the terminal: amber `>` gutter, then the text. No bubble. */
@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.imagePath?.let { AttachedImage(it) }
        if (message.text.isNotBlank()) {
            SelectionContainer {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        ">",
                        style = Mobie.numeric.medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        message.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

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
                .padding(start = 20.dp)
                .size(112.dp)
                .border(1.dp, Mobie.signals.hairline),
        )
    } else {
        Tag("Image attached", icon = LucideR.drawable.lucide_ic_image, modifier = Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, modelTitle: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val waitingForAnswer = streaming && message.text.isBlank()
    val gutter = MaterialTheme.colorScheme.outline.copy(alpha = .5f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "[$modelTitle]",
                style = Mobie.numeric.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (message.interrupted) Tag("Interrupted", color = Mobie.signals.caution)
        }
        // Output hangs off a dim left rule, the way terminal output sits under its command.
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(gutter, topLeft = Offset.Zero, size = Size(1.dp.toPx(), size.height))
                }
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (message.thinking.isNotBlank()) {
                ThinkingPanel(message.thinking, active = waitingForAnswer)
            }
            if (message.text.isNotBlank()) {
                // No animateContentSize here: it would fight the bucketed auto-scroll while tokens stream.
                SelectionContainer(Modifier.widthIn(max = 680.dp)) {
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
                        TypingIndicator(MaterialTheme.colorScheme.primary)
                        Text("Waiting for the first token", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (message.text.isNotBlank()) {
                    StreamingCaret()
                }
            } else if (message.text.isNotBlank()) {
                ReplyFooter(message)
            }
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
        Text(
            footer,
            style = Mobie.numeric.tiny,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // A light inline action: a full bordered button would crowd the stats it sits beside.
        Text(
            if (copied) "[copied]" else "[copy]",
            style = Mobie.numeric.tiny,
            color = if (copied) Mobie.signals.ready else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClickLabel = "Copy reply", role = Role.Button) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Reply", message.text)))
                        copied = true
                    }
                }
                .semantics { contentDescription = if (copied) "Copied" else "Copy reply" }
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(start = 12.dp, end = 4.dp),
        )
    }
}

/** "128 tok · 18.4 tok/s · ttft 0.9 s · 4.1 s", listing only fields that were measured. */
private fun replyStatsLine(stats: InferenceStats): String? = buildList {
    if (stats.decodeTokenCount > 0) add("${stats.decodeTokenCount} tok")
    if (stats.tokensPerSecond > 0) add(String.format(Locale.US, "%.1f tok/s", stats.tokensPerSecond))
    if (stats.timeToFirstTokenMs > 0) add("ttft ${formatMillis(stats.timeToFirstTokenMs)}")
    if (stats.totalGenerationMs > 0) add(formatMillis(stats.totalGenerationMs))
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** The block cursor shown after a streaming reply. Blinks in the draw phase, not in composition. */
@Composable
private fun StreamingCaret() {
    BlinkingCursor(
        Modifier.semantics { contentDescription = "Generating" },
        color = MaterialTheme.colorScheme.primary,
        width = 9.dp,
        height = 18.dp,
    )
}

/** Three square cells pulsing in sequence while the engine prepares its first token. */
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
                    .background(color, RectangleShape),
            )
        }
    }
}

@Composable
private fun ThinkingPanel(thinking: String, active: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val color = Mobie.signals.thinking
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, tween(MotionMedium, easing = EmphasizedEase), label = "chevron")
    // Reads like a folded block in a terminal transcript: `▸ thinking (1.2k chars)`.
    val label = if (active) "thinking…" else "thinking (${formatChars(thinking.length)} chars)"
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = .05f), RectangleShape)
            .border(1.dp, color.copy(alpha = .3f), RectangleShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide thinking" else "Show thinking",
                    role = Role.Button,
                ) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LucideIcon(
                LucideR.drawable.lucide_ic_chevron_right,
                null,
                Modifier.size(14.dp).graphicsLayer { rotationZ = chevron },
                tint = color,
            )
            if (active) StatusDot(color, pulsing = true, size = 6.dp)
            Text(
                label,
                style = Mobie.numeric.small,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { if (active) liveRegion = LiveRegionMode.Polite },
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
                    style = Mobie.numeric.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .drawBehind {
                            drawRect(color.copy(alpha = .6f), size = Size(1.dp.toPx(), size.height))
                        }
                        .padding(start = 12.dp),
                )
            }
        }
    }
}

private fun formatChars(count: Int): String =
    if (count >= 1000) String.format(Locale.US, "%.1fk", count / 1000.0) else count.toString()

// ---------------------------------------------------------------------------------------------
// Markdown. A deliberately small subset (bold, inline code, fenced code, lists, headings, rules)
// parsed per line so half-streamed input never throws: an unclosed ``` becomes an open code
// block and an unclosed ** or ` stays literal text until its closing marker arrives.

internal data class MdSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

internal sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class ListItem(val marker: String, val ordered: Boolean, val depth: Int, val spans: List<MdSpan>) : MdBlock
    data class Code(val language: String, val code: String, val closed: Boolean) : MdBlock
    data object Rule : MdBlock
}

private val HeadingLine = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val BulletLine = Regex("^(\\s*)[-*•]\\s+(.*)$")
private val OrderedLine = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val RuleLine = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

internal fun parseChatMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(parseInlineMarkdown(paragraph.toString()))
            paragraph.clear()
        }
    }
    val lines = text.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().takeWhile { !it.isWhitespace() && it != '`' }
            val code = mutableListOf<String>()
            var closed = false
            i++
            while (i < lines.size) {
                if (lines[i].trimStart().startsWith("```")) {
                    closed = true
                    break
                }
                code += lines[i]
                i++
            }
            blocks += MdBlock.Code(language, code.joinToString("\n").trimEnd('\n'), closed)
            i++ // Skip the closing fence.
            continue
        }
        val heading = HeadingLine.matchEntire(line)
        val bullet = BulletLine.matchEntire(line)
        val ordered = OrderedLine.matchEntire(line)
        when {
            line.isBlank() -> flushParagraph()
            RuleLine.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            heading != null -> {
                flushParagraph()
                val (hashes, content) = heading.destructured
                blocks += MdBlock.Heading(hashes.length, parseInlineMarkdown(content.trimEnd().trimEnd('#').trimEnd()))
            }
            bullet != null -> {
                flushParagraph()
                val (indent, content) = bullet.destructured
                blocks += MdBlock.ListItem("•", ordered = false, depth = listDepth(indent), spans = parseInlineMarkdown(content.trimEnd()))
            }
            ordered != null -> {
                flushParagraph()
                val (indent, number, content) = ordered.destructured
                blocks += MdBlock.ListItem("$number.", ordered = true, depth = listDepth(indent), spans = parseInlineMarkdown(content.trimEnd()))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trimEnd())
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun listDepth(indent: String): Int = (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

internal fun parseInlineMarkdown(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            spans += MdSpan(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flushPlain()
                spans += MdSpan(text.substring(i + 1, end), code = true)
                i = end + 1
                continue
            }
        } else if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flushPlain()
                spans += parseInlineMarkdown(text.substring(i + 2, end)).map { it.copy(bold = true) }
                i = end + 2
                continue
            }
        }
        plain.append(text[i])
        i++
    }
    flushPlain()
    // Merge neighbours with identical styling so the annotated string stays small.
    return spans.fold(mutableListOf()) { merged, span ->
        val last = merged.lastOrNull()
        if (last != null && last.bold == span.bold && last.code == span.code) {
            merged[merged.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            merged += span
        }
        merged
    }
}

@Composable
private fun MarkdownText(text: String) {
    val blocks = remember(text) { parseChatMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeStyle = remember(codeBackground) {
        SpanStyle(fontFamily = FontFamily.Monospace, fontSize = .9.em, background = codeBackground)
    }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val previous = blocks.getOrNull(index - 1)
            val gap = when {
                previous == null -> 0.dp
                previous is MdBlock.ListItem && block is MdBlock.ListItem -> 6.dp
                block is MdBlock.Heading -> 18.dp
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
                else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            },
            // Headings take the amber accent, like a man page's bold section names.
            color = if (block.level <= 2) MaterialTheme.colorScheme.primary else onSurface,
            modifier = Modifier.semantics { heading() },
        )
        is MdBlock.ListItem -> Row(Modifier.padding(start = (block.depth * 18).dp)) {
            Text(
                block.marker,
                style = MaterialTheme.typography.bodyLarge,
                color = if (block.ordered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (block.ordered) 26.dp else 16.dp).padding(end = 8.dp),
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

/** A square code window: title bar with the language and a copy action, then the source. */
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RectangleShape)
            .border(1.dp, Mobie.signals.hairline, RectangleShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                block.language.ifBlank { "text" },
                style = Mobie.numeric.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TermButton(
                if (copied) "copied" else "copy",
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Code", block.code)))
                        copied = true
                    }
                },
                style = TermButtonStyle.Ghost,
            )
        }
        Hairline()
        Text(
            block.code.ifEmpty { " " },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
    val fieldShape = RectangleShape
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val fieldBorder by animateColorAsState(
        when {
            ready && focused -> MaterialTheme.colorScheme.primary
            ready -> MaterialTheme.colorScheme.outline.copy(alpha = .6f)
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
        Hairline()
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
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
                        .heightIn(min = 48.dp)
                        .clip(fieldShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, fieldShape)
                        .border(if (ready && focused) 1.5.dp else 1.dp, fieldBorder, fieldShape)
                        .padding(start = if (supportsVision) 0.dp else 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (supportsVision) {
                        IconButton(onClick = onAttach, enabled = ready && !imageCopying) {
                            LucideIcon(LucideR.drawable.lucide_ic_image, "Attach image", Modifier.size(20.dp))
                        }
                    }
                    // The terminal prompt glyph. Amber when the engine can take input.
                    Text(
                        ">",
                        style = Mobie.numeric.medium,
                        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = if (supportsVision) 4.dp else 0.dp, end = 10.dp, bottom = 14.dp),
                    )
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
                            .padding(vertical = 13.dp)
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
                    style = Mobie.numeric.tiny,
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
        shape = RectangleShape,
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
