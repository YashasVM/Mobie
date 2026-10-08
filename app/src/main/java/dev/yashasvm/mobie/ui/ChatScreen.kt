package dev.yashasvm.mobie.ui

import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.runtime.InferenceStats
import dev.yashasvm.mobie.ui.theme.Mobie
import java.io.File
import java.util.Locale
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
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(
                        state.messages,
                        key = { index, _ -> index },
                        contentType = { _, message -> if (message.fromUser) "user" else "assistant" },
                    ) { index, message ->
                        val itemModifier = Modifier.animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = null,
                            fadeOutSpec = tween(120),
                        )
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
                    item(key = "end", contentType = "end") { Spacer(Modifier.height(8.dp)) }
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

// ---------------------------------------------------------------------------------------------
// Header and telemetry

private data class EngineStatus(val text: String, val pulsing: Boolean)

private fun engineStatus(runtimeState: RuntimeState, runtimeLabel: String): EngineStatus = when (runtimeState) {
    RuntimeState.IDLE -> EngineStatus("$runtimeLabel · on-device · not loaded", false)
    RuntimeState.LOADING -> EngineStatus("$runtimeLabel · loading weights", true)
    RuntimeState.READY -> EngineStatus("$runtimeLabel · on-device · ready", false)
    RuntimeState.GENERATING -> EngineStatus("$runtimeLabel · generating", true)
    RuntimeState.STOPPING -> EngineStatus("$runtimeLabel · stopping", true)
    RuntimeState.ERROR -> EngineStatus("$runtimeLabel · error", false)
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
    val color = runtimeState.signal()
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(20.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusDot(color, pulsing = status.pulsing, size = 6.dp)
                    Text(
                        status.text,
                        style = Mobie.mono.tiny,
                        color = if (runtimeState == RuntimeState.IDLE) MaterialTheme.colorScheme.onSurfaceVariant else color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onHistory) {
                LucideIcon(LucideR.drawable.lucide_ic_history, "Chat history", Modifier.size(20.dp))
            }
            IconButton(onClick = onNewChat) {
                LucideIcon(LucideR.drawable.lucide_ic_plus, "Start a new chat", Modifier.size(20.dp))
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TelemetryValue("tok/s", decode)
            if (prefill != null) {
                VerticalHairline(14.dp)
                TelemetryValue("prefill", prefill)
            }
            VerticalHairline(14.dp)
            TelemetryValue("ttft", ttft)
            VerticalHairline(14.dp)
            TelemetryValue("ram", ram)
        }
        Hairline()
    }
}

@Composable
private fun TelemetryValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label.uppercase(Locale.US), style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            style = Mobie.mono.small,
            color = if (value == "—") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.animateContentSize(tween(160)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Non-conversation states

@Composable
private fun LoadingPanel(modelTitle: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Panel(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
            Column(
                Modifier
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LucideIcon(LucideR.drawable.lucide_ic_cpu, null, Modifier.size(16.dp), tint = Mobie.signals.caution)
                    Text("LOADING WEIGHTS", style = Mobie.mono.tiny, color = Mobie.signals.caution)
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
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "load track position",
    )
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
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
            Modifier.fillMaxWidth().widthIn(max = 420.dp),
            borderColor = Mobie.signals.blocked.copy(alpha = .4f),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LucideIcon(LucideR.drawable.lucide_ic_circle_alert, null, Modifier.size(16.dp), tint = Mobie.signals.blocked)
                    Text("ENGINE ERROR", style = Mobie.mono.tiny, color = Mobie.signals.blocked)
                }
                Text("Chat couldn't start", style = MaterialTheme.typography.titleLarge)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(Mobie.signals.ready, size = 6.dp)
                Text("MODEL IN MEMORY", style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(model.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Runs entirely on this phone. Nothing you type leaves it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Try")
            starters.forEach { starter ->
                val vision = starter == "Describe an image"
                Panel(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = { onStarter(if (vision) "Describe this image in detail." else starter) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(starter, style = MaterialTheme.typography.bodyMedium)
                            if (vision) {
                                Text(
                                    "Attach a photo with the image button first",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LucideIcon(
                            if (vision) LucideR.drawable.lucide_ic_image else LucideR.drawable.lucide_ic_arrow_up,
                            null,
                            Modifier.size(14.dp),
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
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.imagePath?.let { AttachedImage(it) }
        if (message.text.isNotBlank()) {
            Box(Modifier.fillMaxWidth(.84f), contentAlignment = Alignment.TopEnd) {
                SelectionContainer {
                    Text(
                        message.text,
                        modifier = Modifier
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                            .border(1.dp, Mobie.signals.hairline, shape)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val waitingForAnswer = streaming && message.text.isBlank()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(LucideR.drawable.lucide_ic_cpu, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                modelTitle,
                style = Mobie.mono.tiny,
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
            SelectionContainer {
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (streaming) {
            if (message.text.isBlank() && message.thinking.isBlank()) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(Mobie.signals.ready, pulsing = true, size = 6.dp)
                    Text("waiting for first token", style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (message.text.isNotBlank()) {
                StreamingCaret()
            }
        } else if (message.text.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val footer = message.stats?.let(::replyStatsLine)
                Text(
                    footer.orEmpty(),
                    style = Mobie.mono.tiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Reply", message.text)))
                            copied = true
                        }
                    },
                    modifier = Modifier.semantics { if (copied) stateDescription = "Copied" },
                ) {
                    LucideIcon(
                        if (copied) LucideR.drawable.lucide_ic_check else LucideR.drawable.lucide_ic_copy,
                        "Copy reply",
                        Modifier.size(15.dp),
                        tint = if (copied) Mobie.signals.ready else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "14.2 tok/s · TTFT 380 ms · 212 tok · 4.1 s", listing only fields that were measured. */
private fun replyStatsLine(stats: InferenceStats): String? = buildList {
    if (stats.tokensPerSecond > 0) add(String.format(Locale.US, "%.1f tok/s", stats.tokensPerSecond))
    if (stats.timeToFirstTokenMs > 0) add("TTFT ${formatMillis(stats.timeToFirstTokenMs)}")
    if (stats.decodeTokenCount > 0) add("${stats.decodeTokenCount} tok")
    if (stats.totalGenerationMs > 0) add(formatMillis(stats.totalGenerationMs))
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun StreamingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
        label = "caret alpha",
    )
    Box(
        Modifier
            .semantics { contentDescription = "Generating" }
            .width(8.dp)
            .height(16.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
    )
}

@Composable
private fun ThinkingPanel(thinking: String, active: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val color = Mobie.signals.thinking
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = .06f), shape)
            .border(1.dp, color.copy(alpha = .22f), shape)
            .animateContentSize(tween(200)),
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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (active) {
                StatusDot(color, pulsing = true, size = 6.dp)
            } else {
                LucideIcon(LucideR.drawable.lucide_ic_brain, null, Modifier.size(14.dp), tint = color)
            }
            Text(
                if (active) "Thinking…" else "Thought process",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier.weight(1f).semantics { if (active) liveRegion = LiveRegionMode.Polite },
            )
            LucideIcon(
                if (expanded) LucideR.drawable.lucide_ic_chevron_down else LucideR.drawable.lucide_ic_chevron_right,
                null,
                Modifier.size(14.dp),
                tint = color,
            )
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
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
        RuntimeState.READY -> "Ask anything — stays on this phone"
        RuntimeState.LOADING -> "Loading model…"
        RuntimeState.GENERATING -> "Generating…"
        RuntimeState.STOPPING -> "Stopping…"
        RuntimeState.ERROR -> "Model unavailable"
        RuntimeState.IDLE -> "Model not loaded"
    }
    val fieldShape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        Hairline()
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (imageCopying) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusDot(MaterialTheme.colorScheme.primary, pulsing = true, size = 6.dp)
                    Text("Attaching image…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (imagePath != null) {
                Row(
                    Modifier
                        .clip(PillShape)
                        .border(1.dp, Mobie.signals.hairline, PillShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LucideIcon(LucideR.drawable.lucide_ic_image, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Image attached", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = onRemoveImage, modifier = Modifier.size(40.dp)) {
                        LucideIcon(LucideR.drawable.lucide_ic_x, "Remove image", Modifier.size(14.dp))
                    }
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            imageError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(fieldShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, fieldShape)
                        .border(
                            1.dp,
                            if (ready) MaterialTheme.colorScheme.outline.copy(alpha = .6f) else Mobie.signals.hairline,
                            fieldShape,
                        )
                        .padding(start = if (supportsVision) 0.dp else 16.dp, end = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (supportsVision) {
                        IconButton(onClick = onAttach, enabled = ready && !imageCopying) {
                            LucideIcon(LucideR.drawable.lucide_ic_image, "Attach image", Modifier.size(19.dp))
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
                FilledIconButton(
                    onClick = { if (generating) onStop() else onSubmit() },
                    enabled = generating || canSubmit,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (generating) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        contentColor = if (generating) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Mobie.signals.track,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    if (generating) {
                        LucideIcon(LucideR.drawable.lucide_ic_square, "Stop generation", Modifier.size(16.dp))
                    } else {
                        LucideIcon(LucideR.drawable.lucide_ic_arrow_up, "Send message", Modifier.size(20.dp))
                    }
                }
            }
            Text(
                "on-device · $runtimeLabel",
                style = Mobie.mono.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
