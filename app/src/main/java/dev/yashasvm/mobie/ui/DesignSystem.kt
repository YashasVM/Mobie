package dev.yashasvm.mobie.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.DownloadProgress
import dev.yashasvm.mobie.ui.theme.Mobie
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// Shared visual building blocks. Screens compose these instead of styling raw Surfaces so that
// panels, telemetry, and status colors read as one system.

// Terminal windows are square. The names stay so call sites read the same as before.
internal val PanelShape: Shape = RectangleShape
internal val PillShape: Shape = RectangleShape

/** Flat surface with a hairline border. The base container for every grouped block. */
@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = PanelShape,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = Mobie.signals.hairline,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(color, shape)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/** Small uppercase tracked label used above sections and metrics. */
@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    // Reads like a man-page section: NAME followed by a dashed rule to the edge.
    val rule = Mobie.signals.hairline
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text.uppercase(Locale.US),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(
                rule,
                Offset(0f, 0f),
                Offset(size.width, 0f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        trailing?.invoke(this)
    }
}

/** A measured or estimated value: tiny label over a monospace number with an optional unit. */
@Composable
internal fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueStyle: TextStyle = Mobie.numeric.medium,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RollingText(value, style = valueStyle, color = valueColor)
            if (unit != null) {
                Text(
                    unit,
                    style = Mobie.numeric.tiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}

/** Evenly spaced metrics separated by hairlines, e.g. RAM | storage | ABI. */
@Composable
internal fun MetricRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun VerticalHairline(height: Dp = 28.dp) {
    Box(Modifier.width(1.dp).height(height).background(Mobie.signals.hairline))
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Mobie.signals.hairline))
}

/** Square status block. When [pulsing], it blinks like a cursor to show live work. */
@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false, size: Dp = 7.dp) {
    if (!pulsing) {
        Box(modifier.size(size).background(color))
        return
    }
    val transition = rememberInfiniteTransition(label = "status blink")
    // Read in the draw phase so a blinking dot never recomposes its parent.
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = .2f,
        animationSpec = infiniteRepeatable(tween(530, easing = StepEasing), RepeatMode.Reverse),
        label = "status blink alpha",
    )
    Box(modifier.size(size).graphicsLayer { this.alpha = alpha.value }.background(color))
}

/** A block cursor that blinks at terminal cadence. Place it after streaming or prompt text. */
@Composable
internal fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 8.dp,
    height: Dp = 16.dp,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = StepEasing), RepeatMode.Reverse),
        label = "cursor alpha",
    )
    Box(modifier.size(width, height).graphicsLayer { this.alpha = alpha.value }.background(color))
}

/** Jumps between start and end values, like a terminal cursor, instead of fading. */
internal val StepEasing = Easing { fraction -> if (fraction < .5f) 0f else 1f }

/** Compact bordered tag. Use [color] to tint for status; defaults to neutral. */
@Composable
internal fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    @DrawableRes icon: Int? = null,
    mono: Boolean = false,
) {
    val tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .background(color?.copy(alpha = .08f) ?: Color.Transparent)
            .border(1.dp, color?.copy(alpha = .45f) ?: Mobie.signals.hairline)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) LucideIcon(icon, null, Modifier.size(12.dp), tint = tint)
        Text(
            text,
            style = Mobie.numeric.tiny,
            color = tint,
            maxLines = 1,
        )
    }
}

/** Status tag with a leading dot, colored by compatibility. */
@Composable
internal fun SignalTag(text: String, color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    Row(
        modifier
            .border(1.dp, color.copy(alpha = .7f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color, pulsing = pulsing, size = 6.dp)
        Text(text, style = Mobie.numeric.tiny, color = color, maxLines = 1)
    }
}

/** One segment of a [MemoryBar]. */
internal data class MemorySegment(val label: String, val bytes: Long, val color: Color)

/**
 * Horizontal stacked bar of [segments] against a [capacityBytes] scale (usually total RAM).
 * [markerBytes], when set, draws a tick (usually currently available RAM).
 */
@Composable
internal fun MemoryBar(
    segments: List<MemorySegment>,
    capacityBytes: Long,
    modifier: Modifier = Modifier,
    markerBytes: Long? = null,
    height: Dp = 10.dp,
) {
    val track = Mobie.signals.track
    val markerColor = MaterialTheme.colorScheme.onSurface
    // Bars sweep in from empty the first time they appear, then glide between values.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(MotionLong, easing = EmphasizedEase)) }
    val description = segments.joinToString { "${it.label} ${formatBytes(it.bytes)}" } +
        " of ${formatBytes(capacityBytes)}"
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        // A row of block cells, like a `█████░░░░` meter. Each cell takes the color of the segment
        // covering most of it, so the bar stays crisp at any width.
        val cell = 5.dp.toPx()
        val gap = 1.5.dp.toPx()
        val count = ((size.width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
        val pitch = (size.width + gap) / count
        val visible = segments.filter { it.bytes > 0 }
        val total = if (capacityBytes > 0) capacityBytes.toFloat() else 1f
        for (i in 0 until count) {
            val mid = (i + .5f) / count
            var acc = 0f
            var fill = track
            if (capacityBytes > 0 && mid <= reveal.value) {
                for (segment in visible) {
                    acc += segment.bytes / total
                    if (mid <= acc) { fill = segment.color; break }
                }
            }
            drawRect(fill, topLeft = Offset(i * pitch, 0f), size = Size(pitch - gap, size.height))
        }
        if (capacityBytes <= 0) return@Canvas
        markerBytes?.takeIf { it in 1..capacityBytes }?.let { marker ->
            val mx = marker.toFloat() / capacityBytes * size.width
            drawRect(markerColor.copy(alpha = reveal.value), topLeft = Offset(mx - 1f, -2f), size = Size(2.dp.toPx(), size.height + 4f))
        }
    }
}

/** Thin progress track (download, load). */
@Composable
internal fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val track = Mobie.signals.track
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(MotionMedium, easing = EmphasizedEase), label = "progress")
    val blink = rememberInfiniteTransition(label = "progress head")
    val head = blink.animateFloat(1f, .25f, infiniteRepeatable(tween(530, easing = StepEasing), RepeatMode.Reverse), label = "progress head alpha")
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        // `[██████▒░░░░░]`: filled cells, a blinking head cell while work is ongoing, empty track.
        val cell = 4.dp.toPx()
        val gap = 1.5.dp.toPx()
        val count = ((size.width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
        val pitch = (size.width + gap) / count
        val filled = (animated * count).toInt().coerceIn(0, count)
        for (i in 0 until count) {
            val fill = when {
                i < filled -> color
                i == filled && animated in 0.0001f..0.9999f -> color.copy(alpha = head.value)
                else -> track
            }
            drawRect(fill, topLeft = Offset(i * pitch, 0f), size = Size(pitch - gap, size.height))
        }
    }
}

/**
 * A terminal window: hairline frame with a title bar (`title` left, `meta` right). Use for any
 * grouped block that benefits from a name, such as the device panel or a pre-flight check.
 */
@Composable
internal fun TuiWindow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    metaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Panel(modifier, color = MaterialTheme.colorScheme.surfaceContainer, borderColor = borderColor, onClick = onClick) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = Mobie.numeric.tiny,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (meta != null) Text(meta, style = Mobie.numeric.tiny, color = metaColor, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(borderColor))
            content()
        }
    }
}

/** Visual weight of a [TermButton]. */
internal enum class TermButtonStyle { Primary, Ghost, Danger }

/**
 * The website's `[ download apk ]` button: a bordered label that inverts while pressed. Primary is
 * filled amber because it is the one action we want on a screen; Ghost and Danger stay outlined.
 */
@Composable
internal fun TermButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TermButtonStyle = TermButtonStyle.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    hint: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = when (style) {
        TermButtonStyle.Primary -> MaterialTheme.colorScheme.primary
        TermButtonStyle.Ghost -> MaterialTheme.colorScheme.onSurface
        TermButtonStyle.Danger -> Mobie.signals.blocked
    }
    val filled = enabled && (style == TermButtonStyle.Primary) != pressed
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
        filled -> MaterialTheme.colorScheme.background
        else -> accent
    }
    val border = when {
        !enabled -> Mobie.signals.hairline
        style == TermButtonStyle.Ghost && !pressed -> MaterialTheme.colorScheme.outline
        else -> accent
    }
    Row(
        modifier
            .heightIn(min = 44.dp)
            .background(if (filled) accent else Color.Transparent)
            .border(1.dp, border)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) LucideIcon(icon, null, Modifier.size(16.dp), tint = content)
        // Brackets are separate glyphs so the label keeps its exact text for accessibility and tests.
        Text("[", style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = .7f))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        Text("]", style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = .7f))
        if (hint != null) Text(hint, style = Mobie.numeric.tiny, color = content.copy(alpha = .7f), maxLines = 1)
    }
}

/**
 * tmux-like status line: an amber bar holding [segments]. The [selected] segment is shown inverted,
 * the way tmux marks the current window. Purely informational unless a segment has an action.
 */
internal data class StatusSegment(val text: String, val onClick: (() -> Unit)? = null)

@Composable
internal fun StatusLine(
    segments: List<StatusSegment>,
    modifier: Modifier = Modifier,
    selected: Int = 0,
    trailing: String? = null,
) {
    val bar = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.background
    Row(modifier.fillMaxWidth().height(26.dp).background(bar), verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { index, segment ->
            val on = index == selected
            Text(
                segment.text,
                style = Mobie.numeric.tiny.copy(fontWeight = FontWeight.Medium),
                color = if (on) bar else ink,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (on) ink else Color.Transparent)
                    .then(if (segment.onClick != null) Modifier.clickable(onClick = segment.onClick) else Modifier)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 9.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = Mobie.numeric.tiny.copy(fontWeight = FontWeight.Medium),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 9.dp),
            )
        }
    }
}

/** A `$ command` line: dim prompt glyph then text. Use for empty states and screen intros. */
@Composable
internal fun PromptLine(
    text: String,
    modifier: Modifier = Modifier,
    prompt: String = "$",
    color: Color = MaterialTheme.colorScheme.onSurface,
    cursor: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(prompt, style = Mobie.numeric.small, color = MaterialTheme.colorScheme.primary)
        Text(text, style = Mobie.numeric.small, color = color)
        if (cursor) BlinkingCursor(width = 7.dp, height = 14.dp)
    }
}

/** A `[✓] text` / `[!] text` / `[x] text` checklist line in the matching signal color. */
@Composable
internal fun CheckLine(text: String, mark: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("[$mark]", style = Mobie.numeric.small, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------------------------
// Motion. Durations and easing are shared so every screen moves with the same rhythm.

internal const val MotionShort = 160
internal const val MotionMedium = 280
internal const val MotionLong = 520
internal val EmphasizedEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Shrinks slightly while pressed, springing back on release. */
internal fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = .975f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press scale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Remembers which list items have already played their entrance, so scrolling back up does not
 * replay it. Create one per list with [rememberEntranceTracker].
 */
internal class EntranceTracker {
    val seen = mutableSetOf<Any>()
}

@Composable
internal fun rememberEntranceTracker(): EntranceTracker = remember { EntranceTracker() }

/**
 * Fades and lifts an item into place the first time [key] is shown. Items appearing together are
 * staggered by [index] (capped, so long lists don't wait).
 */
internal fun Modifier.entrance(tracker: EntranceTracker, key: Any, index: Int = 0): Modifier = composed {
    val first = remember(key) { key !in tracker.seen }
    val progress = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (first) {
            tracker.seen += key
            delay(index.coerceIn(0, 8) * 45L)
            progress.animateTo(1f, tween(MotionMedium + 140, easing = EmphasizedEase))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** One-shot entrance for a single element; [delayMillis] staggers siblings. */
internal fun Modifier.appear(delayMillis: Int = 0, offsetDp: Float = 14f): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, tween(MotionMedium + 140, easing = EmphasizedEase))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetDp.dp.toPx()
    }
}

/** Placeholder shimmer for skeleton content. */
internal fun Modifier.shimmer(): Modifier = composed {
    val base = Mobie.signals.track
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    // Read the animated value in the draw phase only; reading it during composition would
    // recompose the skeleton on every animation frame.
    val x = transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer x")
    drawWithCache {
        onDrawBehind {
            val offset = x.value
            val brush = Brush.linearGradient(
                listOf(base, highlight, base),
                start = Offset(size.width * offset - size.width / 2, 0f),
                end = Offset(size.width * offset + size.width / 2, size.height),
            )
            drawRect(base)
            drawRect(brush)
        }
    }
}

/**
 * Text whose changes roll vertically (up when the value grows, down when it shrinks for numbers).
 * Use for live or recomputed values such as tok/s, progress, and RAM.
 */
@Composable
internal fun RollingText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Mobie.numeric.medium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val up = (targetState.leadingNumber() ?: 0.0) >= (initialState.leadingNumber() ?: 0.0)
            val direction = if (up) 1 else -1
            (slideInVertically(tween(MotionMedium, easing = EmphasizedEase)) { it / 2 * direction } + fadeIn(tween(MotionMedium))) togetherWith
                (slideOutVertically(tween(MotionShort)) { -it / 2 * direction } + fadeOut(tween(MotionShort))) using
                SizeTransform(clip = false)
        },
        label = "rolling text",
    ) { text ->
        Text(text, style = style, color = color, maxLines = 1)
    }
}

private fun String.leadingNumber(): Double? = Regex("[0-9]+(?:\\.[0-9]+)?").find(this)?.value?.toDoubleOrNull()

@Composable
internal fun LucideIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(icon), contentDescription, modifier, tint)
}

@Composable
internal fun Compatibility?.signalColor(): Color = when (this) {
    Compatibility.COMPATIBLE -> Mobie.signals.ready
    Compatibility.WARNING -> Mobie.signals.caution
    else -> Mobie.signals.blocked
}

internal fun statusLabel(status: Compatibility?) = when (status) {
    Compatibility.COMPATIBLE -> "Runs here"
    Compatibility.WARNING -> "Free memory first"
    Compatibility.INCOMPATIBLE -> "Won't fit"
    Compatibility.CONVERSION_REQUIRED -> "Needs conversion"
    null -> "Checking"
}

internal fun formatBytes(value: Long): String {
    if (value <= 0) return "Unknown"
    val kib = value / 1024.0
    val mib = kib / 1024.0
    val gib = mib / 1024.0
    return when {
        value < 1024 -> "$value B"
        mib < 1 -> String.format(Locale.US, "%.0f KB", kib)
        gib < 1 -> String.format(Locale.US, "%.0f MB", mib)
        else -> String.format(Locale.US, "%.1f GB", gib)
    }
}

/** Splits [formatBytes] output into number and unit for [Metric]. */
internal fun splitBytes(value: Long): Pair<String, String?> {
    val formatted = formatBytes(value)
    val space = formatted.lastIndexOf(' ')
    return if (space < 0 || value <= 0) formatted to null else formatted.substring(0, space) to formatted.substring(space + 1)
}

internal fun formatMillis(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 1000 -> "$ms ms"
    else -> String.format(Locale.US, "%.1f s", ms / 1000.0)
}

internal fun formatTokens(tokens: Int): String = when {
    tokens <= 0 -> "—"
    tokens >= 1024 && tokens % 1024 == 0 -> "${tokens / 1024}K"
    else -> tokens.toString()
}

internal fun downloadProgressLabel(download: DownloadProgress): String {
    if (download.state == WorkInfo.State.CANCELLED) return "Download cancelled"
    if (download.totalBytes <= 0) return "Starting download…"
    val percent = (download.downloadedBytes.toDouble() / download.totalBytes * 100).toInt().coerceIn(0, 100)
    val speed = if (download.bytesPerSecond > 0) " · ${formatBytes(download.bytesPerSecond)}/s" else ""
    return "$percent% · ${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)}$speed"
}

/** Remaining time for a download, or null when speed is unknown. */
internal fun downloadEta(download: DownloadProgress): String? {
    if (download.bytesPerSecond <= 0 || download.totalBytes <= 0) return null
    val seconds = (download.totalBytes - download.downloadedBytes).coerceAtLeast(0) / download.bytesPerSecond
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
}

internal fun deviceLabel(device: DeviceProfile?): String {
    if (device == null) return "Unknown"
    val release = device.releaseVersion.ifBlank { "API ${device.sdkInt}" }
    val api = if (device.releaseVersion.isBlank()) "" else " (API ${device.sdkInt})"
    return "Android $release$api · ${device.supportedAbis.firstOrNull() ?: "Unknown ABI"}"
}

/** Human device name, e.g. "Google Pixel 8", or null when the platform reports nothing. */
internal fun deviceName(device: DeviceProfile?): String? {
    if (device == null) return null
    val maker = device.manufacturer.replaceFirstChar { it.titlecase(Locale.US) }
    val model = device.model
    return when {
        model.isBlank() -> maker.ifBlank { null }
        maker.isBlank() || model.startsWith(maker, ignoreCase = true) -> model
        else -> "$maker $model"
    }
}

/** Chip name, e.g. "Tensor G3", or null. */
internal fun chipName(device: DeviceProfile?): String? =
    device?.socModel?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }

internal fun historyTime(timestamp: Long): String =
    if (timestamp <= 0) "saved chat" else SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

internal fun ModelType.displayLabel(): String = when (this) {
    ModelType.TEXT_GENERATION -> "Text generation"
    ModelType.VISION -> "Vision chat"
    ModelType.EMBEDDING -> "Embedding"
    ModelType.AUDIO -> "Audio"
    ModelType.UNKNOWN -> "Unknown"
}
