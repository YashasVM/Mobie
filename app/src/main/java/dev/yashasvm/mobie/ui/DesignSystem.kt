package dev.yashasvm.mobie.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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

// Shared visual building blocks. Screens compose these instead of styling raw Surfaces so that
// panels, telemetry, and status colors read as one system.

internal val PanelShape = RoundedCornerShape(20.dp)
internal val PillShape = RoundedCornerShape(100)

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
    Box(
        modifier
            .clip(shape)
            .background(color, shape)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(Locale.US),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
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
            Text(value, style = valueStyle, color = valueColor, maxLines = 1)
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

/** Status dot. When [pulsing], it breathes to show live work (loading, generating). */
@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false, size: Dp = 7.dp) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "status pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = .25f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "status pulse alpha",
        )
        value
    } else 1f
    Box(modifier.size(size).alpha(alpha).background(color, CircleShape))
}

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
            .clip(PillShape)
            .background(color?.copy(alpha = .10f) ?: Color.Transparent, PillShape)
            .border(1.dp, color?.copy(alpha = .28f) ?: Mobie.signals.hairline, PillShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) LucideIcon(icon, null, Modifier.size(12.dp), tint = tint)
        Text(
            text,
            style = if (mono) Mobie.numeric.tiny else MaterialTheme.typography.labelSmall,
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
            .clip(PillShape)
            .background(color.copy(alpha = .10f), PillShape)
            .border(1.dp, color.copy(alpha = .28f), PillShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color, pulsing = pulsing, size = 6.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
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
    val description = segments.joinToString { "${it.label} ${formatBytes(it.bytes)}" } +
        " of ${formatBytes(capacityBytes)}"
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (capacityBytes <= 0) return@Canvas
        var x = 0f
        val gap = 2.dp.toPx()
        segments.filter { it.bytes > 0 }.forEach { segment ->
            val width = (segment.bytes.toFloat() / capacityBytes * size.width).coerceAtMost(size.width - x)
            if (width <= 0f) return@forEach
            drawRoundRect(
                segment.color,
                topLeft = Offset(x, 0f),
                size = Size((width - gap).coerceAtLeast(2f), size.height),
                cornerRadius = radius,
            )
            x += width
        }
        markerBytes?.takeIf { it in 1..capacityBytes }?.let { marker ->
            val mx = marker.toFloat() / capacityBytes * size.width
            drawRect(markerColor, topLeft = Offset(mx - 1f, -2f), size = Size(2.dp.toPx(), size.height + 4f))
        }
    }
}

/** Thin progress track (download, load). */
@Composable
internal fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val track = Mobie.signals.track
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        drawRoundRect(color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height), cornerRadius = radius)
    }
}

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
