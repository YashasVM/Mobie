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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
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
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.ui.theme.Mobie
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onSend: (String, String?) -> Unit,
    onStop: () -> Unit = {},
    onBack: () -> Unit = {},
    onHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
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
                    imageError = "Couldn't attach that image. Try another one."
                } else {
                    imagePath?.let { File(it).delete() }
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
    val submit = {
        if (canSubmit) {
            onSend(prompt, imagePath)
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
            TelemetryStrip(state.stats)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.runtimeState == RuntimeState.LOADING -> LoadingPanel(model.title)
                state.runtimeState == RuntimeState.ERROR -> ErrorPanel(state.error ?: "The model could not run on this phone.")
                state.messages.isEmpty() -> EmptyChat(
                    model = model,
                    onStarter = { starter ->
                        prompt = starter
                        if (ready) inputFocus.requestFocus()
                    },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
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
            supportsVision = model.supportsVision,
            imagePath = imagePath,
            imageCopying = imageCopying,
            canSubmit = canSubmit,
            error = state.error.takeIf { state.runtimeState != RuntimeState.ERROR },
            imageError = imageError,
            focusRequester = inputFocus,
            onAttach = { imagePicker.launch("image/*") },
            onRemoveImage = {
                imagePath?.let { File(it).delete() }
                imagePath = null
            },
            onSubmit = submit,
            onStop = onStop,
        )
    }
}

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
 * User messages slide in from the right and grow from their bottom-end corner; replies fade and
 * lift in a beat later.
 */
private fun Modifier.messageEntrance(animate: Boolean, fromUser: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            if (!fromUser) delay(90)
            progress.animateTo(1f, tween(MotionMedium + 80, easing = EmphasizedEase))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        if (fromUser) {
            transformOrigin = TransformOrigin(1f, 1f)
            translationX = (1f - p) * 28.dp.toPx()
            val scale = .96f + .04f * p
            scaleX = scale
            scaleY = scale
        } else {
            translationY = (1f - p) * 14.dp.toPx()
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
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            style = MaterialTheme.typography.bodySmall,
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

/** Last measured numbers. Unmeasured values stay as a dash; nothing is estimated here. */
@Composable
private fun TelemetryStrip(stats: InferenceStats?) {
    val decode = stats?.tokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
    val prefill = stats?.prefillTokensPerSecond?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.0f", it) }
    val ttft = formatMillis(stats?.timeToFirstTokenMs ?: 0)
    val ram = stats?.ramBytes?.takeIf { it > 0 }?.let(::formatBytes) ?: "—"
    val description = if (stats == null) {
        "No reply measured yet"
    } else {
        buildString {
            append("Last reply: decode $decode tokens per second")
            prefill?.let { append(", prefill $it tokens per second") }
            append(", time to first token $ttft, memory $ram")
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 9.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelemetryValue(label = null, value = decode, unit = "tok/s")
            if (prefill != null) {
                VerticalHairline(16.dp)
                TelemetryValue(label = "Prefill", value = prefill, unit = "tok/s")
            }
            VerticalHairline(16.dp)
            TelemetryValue(label = "First token", value = ttft)
            VerticalHairline(16.dp)
            TelemetryValue(label = "RAM", value = ram)
        }
        Hairline()
    }
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
        Panel(Modifier.fillMaxWidth().widthIn(max = 420.dp).appear()) {
            Column(
                Modifier
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LucideIcon(LucideR.drawable.lucide_ic_cpu, null, Modifier.size(18.dp), tint = Mobie.signals.caution)
                    Text("Loading weights", style = MaterialTheme.typography.titleSmall, color = Mobie.signals.caution)
                }
                Text("Loading $modelTitle into memory", style = MaterialTheme.typography.titleLarge)
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

/** A short segment sliding across a thin track. Signals work without implying a percentage. */
@Composable
private fun IndeterminateTrack(color: Color) {
    val track = Mobie.signals.track
    val transition = rememberInfiniteTransition(label = "load track")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EmphasizedEase), RepeatMode.Restart),
        label = "load track position",
    )
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
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
private fun ErrorPanel(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Panel(
            Modifier.fillMaxWidth().widthIn(max = 420.dp).appear(),
            borderColor = Mobie.signals.blocked.copy(alpha = .4f),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LucideIcon(LucideR.drawable.lucide_ic_circle_alert, null, Modifier.size(18.dp), tint = Mobie.signals.blocked)
                    Text("Engine error", style = MaterialTheme.typography.titleSmall, color = Mobie.signals.blocked)
                }
                Text("Chat couldn't start", style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyChat(model: AiModel, onStarter: (String) -> Unit) {
    val starters = remember(model.supportsVision) {
        buildList {
            add("Explain how on-device AI works")
            add("Write a short poem about the sea")
            add("Summarize the pros and cons of electric cars")
            if (model.supportsVision) add("Describe an image") else add("Help me plan a weekend trip")
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.appear(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(Mobie.signals.ready, size = 7.dp)
                Text("Model in memory", style = MaterialTheme.typography.titleSmall, color = Mobie.signals.ready)
            }
            Text(
                model.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.appear(delayMillis = 60),
            )
            Text(
                "Runs entirely on this phone. Nothing you type leaves it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.appear(delayMillis = 120),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Try", Modifier.appear(delayMillis = 160))
            starters.forEachIndexed { index, starter ->
                val vision = starter == "Describe an image"
                Panel(
                    Modifier.fillMaxWidth().appear(delayMillis = 200 + index * 60),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { onStarter(if (vision) "Describe this image in detail." else starter) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 13.dp),
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

@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.imagePath?.let { AttachedImage(it) }
        if (message.text.isNotBlank()) {
            Box(Modifier.fillMaxWidth(.86f), contentAlignment = Alignment.TopEnd) {
                SelectionContainer {
                    Text(
                        message.text,
                        modifier = Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                            .border(1.dp, Mobie.signals.hairline, shape)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
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
    val shape = RoundedCornerShape(12.dp)
    if (image != null) {
        Image(
            image,
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(112.dp).clip(shape).border(1.dp, Mobie.signals.hairline, shape),
        )
    } else {
        Tag("Image attached", icon = LucideR.drawable.lucide_ic_image)
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, modelTitle: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val waitingForAnswer = streaming && message.text.isBlank()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(LucideR.drawable.lucide_ic_cpu, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                modelTitle,
                style = MaterialTheme.typography.labelLarge,
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
                    Text("Waiting for the first token", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val footer = message.stats?.let(::replyStatsLine)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(offsetDp = 6f)) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (footer != null) {
                LucideIcon(LucideR.drawable.lucide_ic_gauge, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    footer,
                    style = Mobie.numeric.tiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Reply", message.text)))
                    copied = true
                }
            },
            modifier = Modifier.size(44.dp).semantics { if (copied) stateDescription = "Copied" },
        ) {
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (fadeIn(tween(MotionShort)) + scaleIn(tween(MotionMedium, easing = EmphasizedEase), initialScale = .6f)) togetherWith
                        (fadeOut(tween(MotionShort)) + scaleOut(tween(MotionShort), targetScale = .6f))
                },
                label = "copy state",
            ) { done ->
                LucideIcon(
                    if (done) LucideR.drawable.lucide_ic_check else LucideR.drawable.lucide_ic_copy,
                    "Copy reply",
                    Modifier.size(18.dp),
                    tint = if (done) Mobie.signals.ready else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "14.2 tok/s · TTFT 380 ms · 212 tok · 4.1 s", listing only fields that were measured. */
private fun replyStatsLine(stats: InferenceStats): String? = buildList {
    if (stats.tokensPerSecond > 0) add(String.format(Locale.US, "%.1f tok/s", stats.tokensPerSecond))
    if (stats.timeToFirstTokenMs > 0) add("first token ${formatMillis(stats.timeToFirstTokenMs)}")
    if (stats.decodeTokenCount > 0) add("${stats.decodeTokenCount} tokens")
    if (stats.totalGenerationMs > 0) add(formatMillis(stats.totalGenerationMs))
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = .15f,
        animationSpec = infiniteRepeatable(tween(560, easing = EmphasizedEase), RepeatMode.Reverse),
        label = "caret alpha",
    )
    Box(
        Modifier
            .semantics { contentDescription = "Generating" }
            .width(8.dp)
            .height(18.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
    )
}

/** Three dots bouncing in sequence while the engine prepares its first token. */
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
    val color = Mobie.signals.thinking
    val shape = RoundedCornerShape(14.dp)
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, tween(MotionMedium, easing = EmphasizedEase), label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = .06f), shape)
            .border(1.dp, color.copy(alpha = .22f), shape),
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
                StatusDot(color, pulsing = true, size = 7.dp)
            } else {
                LucideIcon(LucideR.drawable.lucide_ic_brain, null, Modifier.size(16.dp), tint = color)
            }
            Text(
                if (active) "Thinking…" else "Thought process",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier.weight(1f).semantics { if (active) liveRegion = LiveRegionMode.Polite },
            )
            LucideIcon(
                LucideR.drawable.lucide_ic_chevron_right,
                null,
                Modifier.size(18.dp).graphicsLayer { rotationZ = chevron },
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
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        .drawBehind {
                            drawRoundRect(
                                color.copy(alpha = .6f),
                                size = Size(2.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(1.dp.toPx()),
                            )
                        }
                        .padding(start = 12.dp),
                )
            }
        }
    }
}

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
            color = onSurface,
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

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, Mobie.signals.hairline, shape),
    ) {
        if (block.language.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LucideIcon(LucideR.drawable.lucide_ic_code, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(block.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
                        .heightIn(min = 50.dp)
                        .clip(fieldShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, fieldShape)
                        .border(if (ready && focused) 1.5.dp else 1.dp, fieldBorder, fieldShape)
                        .padding(start = if (supportsVision) 0.dp else 18.dp, end = 16.dp),
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
                            .padding(vertical = 12.5.dp)
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
                    style = MaterialTheme.typography.bodySmall,
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
            .size(50.dp)
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
private suspend fun decodeThumbnail(context: Context, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.isFile || !file.absolutePath.startsWith(context.cacheDir.absolutePath)) return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) sample *= 2
    // BitmapFactory returns null (rather than throwing) for undecodable input; the caller then shows a tag.
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

internal suspend fun copyImageToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val image = File.createTempFile("chat-image-", ".bin", context.cacheDir)
    var copied = false
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            image.outputStream().use(input::copyTo)
        } ?: return@withContext null
        copied = true
        image.absolutePath
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    } finally {
        if (!copied) image.delete()
    }
}
