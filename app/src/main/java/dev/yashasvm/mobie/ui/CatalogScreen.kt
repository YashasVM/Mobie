package dev.yashasvm.mobie.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.Mobie

private const val TAB_DISCOVER = 0
private const val TAB_INSTALLED = 1
private const val TAB_SETTINGS = 2

private val CardShape = RoundedCornerShape(12.dp)
private val FieldShape = RoundedCornerShape(10.dp)
private val IconTileShape = RoundedCornerShape(8.dp)

/**
 * Home of the app: Discover (catalog sized against this phone), Installed (local library), and
 * Settings. All compatibility shown here comes from [CompatibilityResolver]; the screen never
 * parses catalog responses or touches a runtime.
 */
@Composable
internal fun CatalogScreen(
    state: MobieUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
    onOpenInstalled: (InstalledModelEntry) -> Unit = {},
    onDeleteInstalled: (InstalledModelEntry) -> Unit = {},
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
    onSaveToken: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_DISCOVER) }
    var pendingDelete by remember { mutableStateOf<InstalledModelEntry?>(null) }
    val resolver = remember { CompatibilityResolver() }

    BackHandler(enabled = selectedTab != TAB_DISCOVER) { selectedTab = TAB_DISCOVER }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
            label = "catalog tab",
        ) { tab ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    TAB_INSTALLED -> installedTab(
                        state = state,
                        resolver = resolver,
                        onOpen = onOpenInstalled,
                        onDelete = { pendingDelete = it },
                        onBrowse = { selectedTab = TAB_DISCOVER },
                    )
                    TAB_SETTINGS -> settingsTab(
                        tokenConfigured = state.tokenConfigured,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onSaveToken = onSaveToken,
                    )
                    else -> discoverTab(
                        state = state,
                        resolver = resolver,
                        onQuery = onQuery,
                        onSearch = onSearch,
                        onSelect = onSelect,
                        onFeatured = onFeatured,
                    )
                }
            }
        }
        ConsoleNavBar(selectedTab) { selectedTab = it }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { LucideIcon(LucideR.drawable.lucide_ic_trash_2, null, Modifier.size(22.dp)) },
            title = { Text("Delete ${entry.model.title}?") },
            text = {
                val size = entry.model.bestArtifact?.sizeBytes ?: 0
                val freed = if (size > 0) " and frees about ${formatBytes(size)}" else ""
                Text("This removes the model from this phone$freed. You can download it again later.")
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteInstalled(entry) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

// region Discover

private fun LazyListScope.discoverTab(
    state: MobieUiState,
    resolver: CompatibilityResolver,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            section = "local inference",
            title = "Models for this phone",
            subtitle = "Sized against this phone's memory. Once downloaded, they run fully offline.",
        )
    }
    item(key = "rig") { DeviceRig(state.device) }
    item(key = "search") { SearchField(state.query, onQuery, onSearch, onClear = onFeatured) }
    item(key = "section") {
        val searching = state.query.isNotBlank()
        ListHeader(
            title = if (searching) "Compatible results" else "Recommended",
            count = state.models.size.takeIf { !state.loading && state.error == null && it > 0 },
            action = if (searching) ("Clear" to onFeatured) else null,
        )
    }
    when {
        state.loading -> item(key = "loading") { SkeletonList() }
        state.error != null -> item(key = "error") {
            MessagePanel(
                icon = LucideR.drawable.lucide_ic_circle_alert,
                tint = Mobie.signals.blocked,
                title = "Couldn't load models",
                body = state.error,
                actionLabel = "Retry",
                onAction = onFeatured,
                announce = true,
            )
        }
        state.models.isEmpty() -> item(key = "empty") {
            MessagePanel(
                icon = LucideR.drawable.lucide_ic_search_x,
                title = "No matching models",
                body = "Only LiteRT-LM models from litert-community are listed. Try another name.",
                actionLabel = if (state.query.isNotBlank()) "Show recommended" else null,
                onAction = onFeatured,
            )
        }
        else -> items(state.models, key = { it.id }) { model ->
            val compatibility = remember(model, state.device) {
                state.device?.let { resolver.resolve(model.bestArtifact, it) }
            }
            val installed = state.installedModels.any { it.model.id == model.id }
            ModelRow(model, compatibility, state.device, installed, onClick = { onSelect(model) })
        }
    }
}

@Composable
private fun DeviceRig(device: DeviceProfile?) {
    Panel(Modifier.fillMaxWidth().animateContentSize(tween(180)), shape = CardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(LucideR.drawable.lucide_ic_cpu)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        deviceName(device) ?: "This phone",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val chip = chipName(device)
                    Text(
                        if (device == null) "Reading hardware…" else listOfNotNull(chip, deviceLabel(device)).joinToString(" · "),
                        style = Mobie.mono.tiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusDot(
                    if (device == null) Mobie.signals.thinking else Mobie.signals.ready,
                    pulsing = device == null,
                )
            }
            if (device != null && device.totalRamBytes > 0) {
                val used = (device.totalRamBytes - device.availableRamBytes).coerceAtLeast(0)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "MEMORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatBytes(used)} in use / ${formatBytes(device.totalRamBytes)}",
                            style = Mobie.mono.tiny,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MemoryBar(
                        segments = listOf(
                            MemorySegment("In use", used, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)),
                        ),
                        capacityBytes = device.totalRamBytes,
                        height = 6.dp,
                    )
                }
            }
            Hairline()
            MetricRow {
                BytesMetric("Total RAM", device?.totalRamBytes ?: 0, Modifier.weight(1f))
                VerticalHairline()
                BytesMetric(
                    "Available",
                    device?.availableRamBytes ?: 0,
                    Modifier.weight(1f),
                    valueColor = if (device?.isLowMemory == true) Mobie.signals.caution else MaterialTheme.colorScheme.onSurface,
                )
                VerticalHairline()
                BytesMetric("Free storage", device?.availableStorageBytes ?: 0, Modifier.weight(1f))
                VerticalHairline()
                Metric(
                    "ABI",
                    device?.supportedAbis?.firstOrNull()?.substringBefore('-') ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("model_search"),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text("Search models, e.g. gemma", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                Row {
                    IconButton(onClick = onClear) {
                        LucideIcon(LucideR.drawable.lucide_ic_x, "Clear search", Modifier.size(18.dp))
                    }
                    if (query.isNotBlank()) {
                        IconButton(onClick = onSearch) {
                            LucideIcon(LucideR.drawable.lucide_ic_arrow_right, "Search", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Mobie.signals.hairline,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ModelRow(
    model: AiModel,
    compatibility: CompatibilityResult?,
    device: DeviceProfile?,
    installed: Boolean,
    onClick: () -> Unit,
) {
    val artifact = model.bestArtifact
    val status = compatibility?.status
    val statusColor = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    val estimatedRam = compatibility?.estimatedRamBytes ?: 0
    val context = compatibility?.contextWindowTokens?.takeIf { it > 0 } ?: artifact?.contextWindowTokens ?: 0
    Panel(
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = CardShape,
        onClick = onClick,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        model.author,
                        style = Mobie.mono.tiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SignalTag(statusLabel(status), statusColor)
            }
            if (model.description.isNotBlank()) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SpecStrip(
                sizeBytes = artifact?.sizeBytes ?: 0,
                quantization = artifact?.quantization,
                estimatedRamBytes = estimatedRam,
                contextTokens = context,
                sizeLabel = "Download",
            )
            if (device != null && device.totalRamBytes > 0 && estimatedRam > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MemoryBar(
                        segments = listOf(MemorySegment("Estimated RAM", estimatedRam, statusColor)),
                        capacityBytes = device.totalRamBytes,
                        markerBytes = device.availableRamBytes.takeIf { it > 0 },
                        height = 4.dp,
                    )
                    Text(
                        "needs ~${formatBytes(estimatedRam)} of ${formatBytes(device.totalRamBytes)} RAM · " +
                            "${formatBytes(device.availableRamBytes)} free now",
                        style = Mobie.mono.tiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (installed || model.supportsVision || model.gated) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (installed) Tag("Installed", color = Mobie.signals.ready, icon = LucideR.drawable.lucide_ic_hard_drive)
                    if (model.supportsVision) Tag("Vision", icon = LucideR.drawable.lucide_ic_eye)
                    if (model.gated) Tag("Needs access", icon = LucideR.drawable.lucide_ic_lock)
                }
            }
        }
    }
}

/** Four mono specs separated by hairlines: size, quantization, estimated RAM, context. */
@Composable
private fun SpecStrip(
    sizeBytes: Long,
    quantization: String?,
    estimatedRamBytes: Long,
    contextTokens: Int,
    sizeLabel: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Mobie.signals.hairline, IconTileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BytesMetric(sizeLabel, sizeBytes, Modifier.weight(1f), small = true)
        VerticalHairline(24.dp)
        Metric("Quant", quantization ?: "—", Modifier.weight(1f), valueStyle = Mobie.mono.small)
        VerticalHairline(24.dp)
        val (ram, ramUnit) = splitBytes(estimatedRamBytes)
        Metric(
            "Est. RAM",
            if (estimatedRamBytes > 0) "~$ram" else "—",
            Modifier.weight(1f),
            unit = ramUnit.takeIf { estimatedRamBytes > 0 },
            valueStyle = Mobie.mono.small,
        )
        VerticalHairline(24.dp)
        Metric(
            "Context",
            formatTokens(contextTokens),
            Modifier.weight(1f),
            unit = "tok".takeIf { contextTokens > 0 },
            valueStyle = Mobie.mono.small,
        )
    }
}

@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = .35f,
        targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton alpha",
    )
    Column(
        Modifier.semantics {
            contentDescription = "Loading models"
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Panel(Modifier.fillMaxWidth(), shape = CardShape) {
                Column(Modifier.padding(14.dp).alpha(alpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBar(Modifier.weight(1f), widthFraction = .55f, height = 16.dp)
                        SkeletonBar(Modifier.width(72.dp), height = 20.dp)
                    }
                    SkeletonBar(widthFraction = .3f, height = 10.dp)
                    SkeletonBar(widthFraction = .9f, height = 10.dp)
                    SkeletonBar(height = 40.dp)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Dp) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .background(Mobie.signals.track, RoundedCornerShape(4.dp)),
        )
    }
}

// endregion

// region Installed

private fun LazyListScope.installedTab(
    state: MobieUiState,
    resolver: CompatibilityResolver,
    onOpen: (InstalledModelEntry) -> Unit,
    onDelete: (InstalledModelEntry) -> Unit,
    onBrowse: () -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            section = "library",
            title = "Your local models",
            subtitle = "Stored on this phone and ready offline. Tap one to start a new chat.",
        )
    }
    state.error?.let { message ->
        item(key = "error") { InlineError(message) }
    }
    if (state.installedModels.isEmpty()) {
        item(key = "empty") {
            MessagePanel(
                icon = LucideR.drawable.lucide_ic_package,
                title = "Nothing downloaded yet",
                body = "Models you download live here, ready for private chats without a connection.",
                actionLabel = "Browse models",
                onAction = onBrowse,
                primaryAction = true,
            )
        }
    } else {
        item(key = "section") {
            val totalBytes = state.installedModels.sumOf { it.model.bestArtifact?.sizeBytes?.coerceAtLeast(0) ?: 0 }
            ListHeader(
                title = "Installed",
                count = state.installedModels.size,
                trailingNote = if (totalBytes > 0) "${formatBytes(totalBytes)} on disk" else null,
            )
        }
        items(state.installedModels, key = { it.model.id }) { entry ->
            val compatibility = remember(entry.model, state.device) {
                state.device?.let { resolver.resolve(entry.model.bestArtifact, it) }
            }
            InstalledRow(entry, compatibility, onOpen = { onOpen(entry) }, onDelete = { onDelete(entry) })
        }
    }
}

@Composable
private fun InstalledRow(
    entry: InstalledModelEntry,
    compatibility: CompatibilityResult?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val model = entry.model
    val artifact = model.bestArtifact
    val context = compatibility?.contextWindowTokens?.takeIf { it > 0 } ?: artifact?.contextWindowTokens ?: 0
    Panel(
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = CardShape,
        onClick = onOpen,
    ) {
        Column(Modifier.padding(start = 14.dp, top = 6.dp, end = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        model.author,
                        style = Mobie.mono.tiny,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    LucideIcon(
                        LucideR.drawable.lucide_ic_trash_2,
                        "Delete ${model.title}",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecStrip(
                    sizeBytes = artifact?.sizeBytes ?: 0,
                    quantization = artifact?.quantization,
                    estimatedRamBytes = compatibility?.estimatedRamBytes ?: 0,
                    contextTokens = context,
                    sizeLabel = "On disk",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(Mobie.signals.ready, size = 6.dp)
                    Text(
                        listOfNotNull("On this phone", artifact?.runtimeLabel, "Vision".takeIf { model.supportsVision }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Start chat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LucideIcon(LucideR.drawable.lucide_ic_chevron_right, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// endregion

// region Settings

private fun LazyListScope.settingsTab(
    tokenConfigured: Boolean,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onSaveToken: (String) -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            section = "settings",
            title = "Settings",
            subtitle = "Access, appearance, and how Mobie handles your data.",
        )
    }
    item(key = "appearance") {
        SettingsGroup("Appearance") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = darkTheme, role = Role.Switch, onValueChange = onDarkThemeChange)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(LucideR.drawable.lucide_ic_sun_moon)
                Column(Modifier.weight(1f)) {
                    Text("Dark mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (darkTheme) "On" else "Off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = darkTheme, onCheckedChange = null)
            }
        }
    }
    item(key = "access") { TokenSettings(tokenConfigured, onSaveToken) }
    item(key = "privacy") {
        SettingsGroup("Privacy") {
            Column {
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_shield_check,
                    title = "Prompts and replies stay here",
                    body = "Every model runs on this phone. What you type and what the model writes is never sent anywhere.",
                    iconTint = Mobie.signals.ready,
                )
                Hairline(Modifier.padding(start = 58.dp))
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_download,
                    title = "Network use",
                    body = "Only to browse the Hugging Face catalog and download model files.",
                )
            }
        }
    }
    item(key = "about") {
        val context = LocalContext.current
        SettingsGroup("About") {
            Column {
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_github,
                    title = "Repository",
                    body = "github.com/YashasVM/Mobie",
                    mono = true,
                    trailingIcon = LucideR.drawable.lucide_ic_external_link,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) },
                )
                Hairline(Modifier.padding(start = 58.dp))
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_file_text,
                    title = "Licensing",
                    body = "LiteRT-LM · Lucide Icons",
                )
                Hairline(Modifier.padding(start = 58.dp))
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_zap,
                    title = "Runtime",
                    body = "On-device inference with LiteRT-LM. Each model's license is shown on its details page.",
                )
            }
        }
    }
    item(key = "credit") {
        Text(
            "made by @yashas.vm",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            textAlign = TextAlign.Center,
            style = Mobie.mono.tiny,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TokenSettings(tokenConfigured: Boolean, onSaveToken: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    // Deliberately not saveable: a credential draft must not be written into saved instance state.
    var draft by remember { mutableStateOf("") }
    SettingsGroup("Account") {
        Column(
            Modifier.padding(14.dp).animateContentSize(tween(180)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(LucideR.drawable.lucide_ic_key_round)
                Column(Modifier.weight(1f)) {
                    Text("Hugging Face access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (tokenConfigured) "A token is securely stored on this device."
                        else "Optional. Add one only when a gated model requires it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tokenConfigured) SignalTag("Saved", Mobie.signals.ready) else Tag("None")
            }
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().testTag("hf_token_input"),
                    singleLine = true,
                    textStyle = Mobie.mono.small,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Access token") },
                    supportingText = { Text("Leave blank and save to remove it.") },
                    shape = FieldShape,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Mobie.signals.hairline),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { editing = false; draft = "" }) { Text("Cancel") }
                    Button(onClick = { onSaveToken(draft); editing = false; draft = "" }) { Text("Save") }
                }
            } else {
                OutlinedButton(onClick = { editing = true }, shape = FieldShape) {
                    Text(if (tokenConfigured) "Change token" else "Add token")
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label, Modifier.padding(start = 4.dp).semantics { heading() })
        Panel(Modifier.fillMaxWidth(), shape = CardShape, content = content)
    }
}

@Composable
private fun InfoRow(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    mono: Boolean = false,
    @DrawableRes trailingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick)
                else Modifier,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, tint = iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = if (mono) Mobie.mono.tiny else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailingIcon != null) {
            LucideIcon(trailingIcon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion

// region Shared pieces

@Composable
private fun ScreenHeader(section: String, title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Text("mobie", style = Mobie.mono.small.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text("/ $section", style = Mobie.mono.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** List section heading. Text is kept in its natural case so screen readers and tests read it as written. */
@Composable
private fun ListHeader(
    title: String,
    count: Int? = null,
    trailingNote: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 2.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (count != null) Tag(count.toString(), mono = true)
        Spacer(Modifier.weight(1f))
        if (trailingNote != null) {
            Text(trailingNote, style = Mobie.mono.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun BytesMetric(
    label: String,
    bytes: Long,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val (value, unit) = splitBytes(bytes)
    Metric(
        label = label,
        value = if (bytes > 0) value else "—",
        modifier = modifier,
        unit = unit,
        valueStyle = if (small) Mobie.mono.small else Mobie.mono.medium,
        valueColor = valueColor,
    )
}

@Composable
private fun IconTile(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier
            .size(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, IconTileShape)
            .border(1.dp, Mobie.signals.hairline, IconTileShape),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, null, Modifier.size(17.dp), tint = tint)
    }
}

@Composable
private fun MessagePanel(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    primaryAction: Boolean = false,
    announce: Boolean = false,
) {
    Panel(
        Modifier
            .fillMaxWidth()
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        shape = CardShape,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon, tint = tint)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(4.dp))
                if (primaryAction) {
                    Button(onClick = onAction, shape = FieldShape) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onAction, shape = FieldShape) {
                        if (actionLabel == "Retry") {
                            LucideIcon(LucideR.drawable.lucide_ic_refresh_cw, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    val color = Mobie.signals.blocked
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = .08f), CardShape)
            .border(1.dp, color.copy(alpha = .28f), CardShape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_circle_alert, null, Modifier.size(16.dp), tint = color)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ConsoleNavBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Discover", LucideR.drawable.lucide_ic_search, "bottom_nav_discover"),
        Triple("Installed", LucideR.drawable.lucide_ic_package, "bottom_nav_installed"),
        Triple("Settings", LucideR.drawable.lucide_ic_settings, "bottom_nav_settings"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            items.forEachIndexed { index, (label, icon, tag) ->
                val selected = selectedTab == index
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(150),
                    label = "$label tint",
                )
                val indicator by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(150),
                    label = "$label indicator",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag(tag)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .width(24.dp)
                            .height(2.dp)
                            .background(indicator, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)),
                    )
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LucideIcon(icon, null, Modifier.size(20.dp), tint = color)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
        }
    }
}

// endregion
