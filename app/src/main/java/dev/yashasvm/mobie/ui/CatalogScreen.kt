package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.CLOUD_PRIVACY_URL
import dev.yashasvm.mobie.core.model.CLOUD_DISCLOSURE
import dev.yashasvm.mobie.core.model.CloudModel
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.Mobie

internal enum class CatalogPage { MODELS, SETTINGS }

internal enum class CatalogFilter(val label: String) {
    ALL("All"), FITS_NOW("Fits now"), PHOTOS("Photos"), TEXT("Text");

    fun includes(model: AiModel, compatibility: Compatibility?): Boolean = when (this) {
        ALL -> true
        FITS_NOW -> compatibility == Compatibility.COMPATIBLE
        PHOTOS -> model.supportsVision
        TEXT -> model.type == dev.yashasvm.mobie.core.model.ModelType.TEXT_GENERATION
    }
}

/** List items before this index stagger in; later ones (reached by scrolling) appear without delay. */
private const val STAGGER_LIMIT = 8

/** Vertical gap after each block. Rows inside a list sit flush and are separated by hairlines. */
private val GroupGap = 18.dp

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
    onRefreshCloud: () -> Unit = {},
    onSaveCloudConnection: (String, String) -> Unit = { _, _ -> },
    onDisconnectCloud: () -> Unit = {},
    onSelectCloud: (CloudModel) -> Unit = {},
    page: CatalogPage = CatalogPage.MODELS,
    onMenu: () -> Unit = {},
    onModels: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatsForNerdsChange: (Boolean) -> Unit = {},
    onReplySpeedChange: (Int?) -> Unit = {},
    onAdvancedSettings: () -> Unit = {},
) {
    androidx.compose.runtime.LaunchedEffect(page, state.cloudConnectionUrl) {
        if (page == CatalogPage.MODELS && state.cloudConnectionUrl != null) onRefreshCloud()
    }
    var pendingCloud by remember { mutableStateOf<CloudModel?>(null) }
    var pendingDelete by remember { mutableStateOf<InstalledModelEntry?>(null) }
    val resolver = remember { CompatibilityResolver() }
    var catalogFilter by rememberSaveable { mutableStateOf(CatalogFilter.ALL) }
    val visibleModels = remember(state.models, state.device, state.computeBackend, catalogFilter) {
        state.models.filter { model ->
            val compatibility = if (catalogFilter == CatalogFilter.FITS_NOW) {
                state.device?.let { resolver.resolve(model.bestArtifact, it, model.supportsVision, backend = state.computeBackend).status }
            } else null
            catalogFilter.includes(model, compatibility)
        }
    }
    // Track each page separately so revisiting it does not replay entrances.
    val discoverEntrance = rememberEntranceTracker()
    val installedEntrance = rememberEntranceTracker()
    val settingsEntrance = rememberEntranceTracker()

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.testTag("open_menu")) {
                LucideIcon(LucideR.drawable.lucide_ic_menu, "Open menu", Modifier.size(24.dp))
            }
            Text(
                when (page) {
                    CatalogPage.MODELS -> "Models"
                    CatalogPage.SETTINGS -> "Settings"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("catalog_title").semantics { heading() },
            )
        }
        Hairline()
        key(page) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
            ) {
                when (page) {
                    CatalogPage.SETTINGS -> {
                        item("cloud_connection") {
                            SettingsGroup("Cloud", Modifier.padding(bottom = GroupGap)) {
                                CloudConnectionSettings(state.cloudConnectionUrl, onSaveCloudConnection, onDisconnectCloud)
                                state.cloudError?.let { message ->
                                    Text(message, color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        settingsTab(state, darkTheme, settingsEntrance, onDarkThemeChange, onSaveToken,
                            onStatsForNerdsChange, onReplySpeedChange, onAdvancedSettings)
                    }
                    CatalogPage.MODELS -> {
                        item("device") { DeviceSummary(state.device) }
                        if (state.installedModels.isNotEmpty()) {
                            installedTab(state, installedEntrance, onOpenInstalled,
                                onDelete = { pendingDelete = it }, onBrowse = onModels)
                        }
                        discoverTab(state, visibleModels, catalogFilter, { catalogFilter = it },
                            resolver, discoverEntrance, onQuery, onSearch, onSelect, onFeatured)
                        cloudModels(state, onRefreshCloud, onSettings, onSelect = { pendingCloud = it })
                    }
                }
            }
        }
    }

    pendingCloud?.let { model ->
        CloudModelConsent(model, onDismiss = { pendingCloud = null }, onConfirm = {
            pendingCloud = null
            onSelectCloud(model)
        })
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { LucideIcon(LucideR.drawable.lucide_ic_trash_2, null, Modifier.size(22.dp)) },
            title = { Text("Delete ${entry.model.title}?") },
            text = {
                val size = entry.model.bestArtifact?.sizeBytes ?: 0
                val freed = if (size > 0) " and frees about ${formatBytes(size)}" else ""
                Text(
                    "This removes the model from this phone$freed. You can download it again later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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

private fun LazyListScope.cloudModels(
    state: MobieUiState,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onSelect: (CloudModel) -> Unit,
) {
    item("cloud_header") {
        ListHeader(
            title = "Cloud",
            action = if (state.cloudConnectionUrl != null) {
                (if (state.cloudLoading) "Refreshing…" else "Refresh") to onRefresh
            } else null,
        )
    }
    if (state.cloudConnectionUrl == null) {
        item("cloud_connect") {
            TermButton("Connect cloud models", onClick = onSettings, style = TermButtonStyle.Ghost,
                modifier = Modifier.padding(bottom = GroupGap))
        }
    } else {
        state.cloudError?.let { message ->
            item("cloud_error") { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        }
        if (state.cloudLoading && state.cloudModels.isEmpty()) {
            item("cloud_loading") { SkeletonList() }
        } else if (state.cloudError == null && state.cloudModels.isEmpty()) {
            item("cloud_empty") { Text("No cloud models available.", style = MaterialTheme.typography.bodySmall) }
        }
        itemsIndexed(state.cloudModels, key = { _, model -> "cloud_${model.id}" }) { _, model ->
            ListRow(onClick = { onSelect(model) }, modifier = Modifier.testTag("cloud_model_${model.id}")) { tone ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LucideIcon(LucideR.drawable.lucide_ic_cloud, "Cloud", Modifier.size(18.dp), tint = tone.dim)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(model.title, style = MaterialTheme.typography.titleSmall, color = tone.ink)
                        Text(if (model.supportsImages) "OpenCode · text and photos" else "OpenCode · text",
                            style = Mobie.numeric.tiny, color = tone.dim)
                    }
                }
            }
        }
    }
}

// region Discover

private fun LazyListScope.discoverTab(
    state: MobieUiState,
    visibleModels: List<AiModel>,
    filter: CatalogFilter,
    onFilter: (CatalogFilter) -> Unit,
    resolver: CompatibilityResolver,
    tracker: EntranceTracker,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
) {
    item(key = "download_header") {
        ListHeader("Download models", count = if (!state.loading && state.catalogError == null) visibleModels.size else null,
            modifier = Modifier.padding(top = 12.dp))
    }
    item(key = "search") {
        Column(Modifier.entrance(tracker, "search", 2).padding(bottom = 8.dp)) {
            SearchField(state.query, onQuery, onSearch, onClear = { onQuery(""); onFeatured() })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { onFilter(option) },
                        label = { Text(option.label) }, modifier = Modifier.testTag("catalog_filter_${option.name}"))
                }
            }
            if (filter == CatalogFilter.FITS_NOW) {
                Text("Fit is estimated from available memory and storage. Open a model to review requirements.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    when {
        state.loading -> item(key = "loading") { SkeletonList() }
        state.catalogError != null -> item(key = "catalog_error") {
            MessagePanel(
                title = "Couldn't load models",
                body = state.catalogError,
                mark = "[!]",
                markColor = Mobie.signals.blocked,
                actionLabel = "Retry",
                onAction = if (state.query.isNotBlank()) onSearch else onFeatured,
                announce = true,
                modifier = Modifier.appear(),
            )
        }
        visibleModels.isEmpty() -> item(key = "catalog_empty") {
            MessagePanel(
                title = "No matching models",
                body = if (state.models.isNotEmpty()) "No models in these results match ${filter.label.lowercase()}. Try another filter."
                    else "Try another model name or browse recommended downloads.",
                mark = "[ ]",
                actionLabel = if (filter != CatalogFilter.ALL) "Show all results" else "Show recommended",
                onAction = { if (filter != CatalogFilter.ALL) onFilter(CatalogFilter.ALL) else { onQuery(""); onFeatured() } },
                modifier = Modifier.appear(),
            )
        }
        else -> itemsIndexed(visibleModels, key = { _, model -> "catalog_${model.id}" }) { index, model ->
            val compatibility = remember(model, state.device, state.computeBackend) {
                state.device?.let { resolver.resolve(model.bestArtifact, it, model.supportsVision, backend = state.computeBackend) }
            }
            val installed = state.installedModels.any { it.model.id == model.id }
            Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(MotionShort))) {
                ModelRow(
                    model = model,
                    compatibility = compatibility,
                    installed = installed,
                    device = state.device,
                    onClick = { onSelect(model) },
                    modifier = Modifier.entrance(tracker, "model:${model.id}", if (index < STAGGER_LIMIT) index + 1 else 0),
                )
            }
        }
    }
}

@Composable
private fun DeviceSummary(device: dev.yashasvm.mobie.core.model.DeviceProfile?) {
    TuiWindow("This phone", modifier = Modifier.padding(bottom = GroupGap), meta = deviceName(device)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (device != null && device.totalRamBytes > 0) {
                val used = (device.totalRamBytes - device.availableRamBytes).coerceIn(0, device.totalRamBytes)
                MemoryBar(listOf(MemorySegment("RAM in use", used, MaterialTheme.colorScheme.onSurfaceVariant)), device.totalRamBytes)
                Text("${formatBytes(used)} in use of ${formatBytes(device.totalRamBytes)} RAM",
                    style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Available RAM", device?.availableRamBytes?.let(::formatBytes) ?: "Unknown", Modifier.weight(1f))
                Metric("Free storage", device?.availableStorageBytes?.let(::formatBytes) ?: "Unknown", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val promptColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("model_search"),
        singleLine = true,
        interactionSource = interaction,
        textStyle = Mobie.numeric.medium,
        placeholder = {
            Text("Search models", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(20.dp), tint = promptColor) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = scaleIn(tween(MotionShort, easing = EmphasizedEase), initialScale = .6f) + fadeIn(tween(MotionShort)),
                    exit = scaleOut(tween(MotionShort), targetScale = .6f) + fadeOut(tween(MotionShort)),
                ) {
                    IconButton(onClick = onClear) {
                        LucideIcon(LucideR.drawable.lucide_ic_x, "Clear search", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(
                    visible = query.isNotBlank(),
                    enter = scaleIn(tween(MotionShort, easing = EmphasizedEase), initialScale = .6f) + fadeIn(tween(MotionShort)),
                    exit = scaleOut(tween(MotionShort), targetScale = .6f) + fadeOut(tween(MotionShort)),
                ) {
                    IconButton(onClick = onSearch) {
                        LucideIcon(LucideR.drawable.lucide_ic_arrow_right, "Search", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ModelRow(
    model: AiModel,
    compatibility: CompatibilityResult?,
    installed: Boolean,
    device: dev.yashasvm.mobie.core.model.DeviceProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artifact = model.bestArtifact
    val status = compatibility?.status
    val statusColor = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    ListRow(onClick = onClick, modifier = modifier) { tone ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                statusGlyph(status),
                style = Mobie.numeric.medium,
                color = tone.tint(statusColor),
                modifier = Modifier.width(14.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                model.title,
                style = MaterialTheme.typography.titleSmall,
                color = tone.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatBytes(artifact?.sizeBytes ?: 0), style = Mobie.numeric.small, color = tone.ink, maxLines = 1)
        }
        SpecText(
            listOfNotNull(artifact?.runtimeLabel, artifact?.quantization,
                "Installed".takeIf { installed }, "Photos".takeIf { model.supportsVision },
                statusLabel(status)).joinToString(" · "),
            tone.dim,
        )
        val estimatedRam = compatibility?.estimatedRamBytes ?: 0
        if (estimatedRam > 0) {
            if (device != null && device.totalRamBytes > 0) {
                MemoryBar(
                    segments = listOf(MemorySegment("Estimated RAM", estimatedRam, tone.tint(statusColor))),
                    capacityBytes = device.totalRamBytes,
                    markerBytes = device.availableRamBytes.takeIf { it > 0 },
                    modifier = Modifier.padding(start = 24.dp),
                    height = 6.dp,
                )
            }
            SpecText("Estimated RAM ${formatBytes(estimatedRam)}", tone.dim)
        }

    }
}

/** Dim second line under a row name, indented to sit under the title rather than the status glyph. */
@Composable
private fun SpecText(text: String, color: Color) {
    Text(
        text,
        style = Mobie.numeric.tiny,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 24.dp),
    )
}

private fun statusGlyph(status: Compatibility?): String = when (status) {
    Compatibility.COMPATIBLE -> "✓"
    Compatibility.WARNING -> "!"
    null -> "·"
    else -> "x"
}

@Composable
private fun SkeletonList() {
    Column(
        Modifier.semantics {
            contentDescription = "Loading models"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        repeat(3) { index ->
            Column(
                Modifier.fillMaxWidth().appear(delayMillis = index * 60).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBar(Modifier.width(10.dp), height = 12.dp)
                    SkeletonBar(Modifier.weight(1f), widthFraction = .6f, height = 16.dp)
                    SkeletonBar(Modifier.width(56.dp), height = 14.dp)
                }
                SkeletonBar(Modifier.padding(start = 24.dp), widthFraction = .7f, height = 12.dp)
                SkeletonBar(Modifier.padding(start = 24.dp), widthFraction = .5f, height = 12.dp)
            }
            Hairline()
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
                .shimmer(),
        )
    }
}

// endregion

// region Installed

private fun LazyListScope.installedTab(
    state: MobieUiState,
    tracker: EntranceTracker,
    onOpen: (InstalledModelEntry) -> Unit,
    onDelete: (InstalledModelEntry) -> Unit,
    onBrowse: () -> Unit,
) {
    state.error?.let { message ->
        item(key = "error") {
            TuiWindow(
                title = "error",
                meta = "[!]",
                metaColor = Mobie.signals.blocked,
                borderColor = Mobie.signals.blocked.copy(alpha = .6f),
                modifier = Modifier.fillMaxWidth().padding(bottom = GroupGap).appear().semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
    if (state.installedModels.isEmpty()) {
        item(key = "empty") {
            MessagePanel(
                title = "No downloaded models",
                body = "Download a model to use it offline.",
                mark = "[ ]",
                actionLabel = "Browse models",
                onAction = onBrowse,
                primaryAction = true,
                modifier = Modifier.animateItem().entrance(tracker, "empty", 1),
            )
        }
    } else {
        item(key = "section") {
            val totalBytes = state.installedModels.sumOf { it.model.bestArtifact?.sizeBytes?.coerceAtLeast(0) ?: 0 }
            ListHeader(
                title = "Installed",
                count = state.installedModels.size,
                trailingNote = if (totalBytes > 0) "${formatBytes(totalBytes)} on disk" else null,
                modifier = Modifier.entrance(tracker, "section", 1).padding(bottom = 6.dp),
            )
        }
        itemsIndexed(state.installedModels, key = { _, entry -> "installed_${entry.model.id}" }) { index, entry ->
            // Placement animates when a neighbour is deleted; the entrance plays once on the content.
            Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(MotionShort))) {
                InstalledRow(
                    entry = entry,
                    onOpen = { onOpen(entry) },
                    onDelete = { onDelete(entry) },
                    modifier = Modifier.entrance(tracker, "installed:${entry.model.id}", if (index < STAGGER_LIMIT) index + 2 else 0),
                )
            }
        }
    }
}

@Composable
private fun InstalledRow(
    entry: InstalledModelEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = entry.model
    val artifact = model.bestArtifact
    ListRow(onClick = onOpen, modifier = modifier) { tone ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "✓",
                style = Mobie.numeric.medium,
                color = tone.tint(Mobie.signals.ready),
                modifier = Modifier.width(14.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                model.title,
                style = MaterialTheme.typography.titleSmall,
                color = tone.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatBytes(artifact?.sizeBytes ?: 0), style = Mobie.numeric.small, color = tone.ink, maxLines = 1)
        }
        Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOfNotNull(artifact?.runtimeLabel, artifact?.quantization).joinToString(" · "),
                style = Mobie.numeric.tiny, color = tone.dim, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                LucideIcon(LucideR.drawable.lucide_ic_trash_2, "Delete ${model.title}", Modifier.size(20.dp), tint = tone.dim)
            }
        }
    }
}

// endregion

// region Settings

private fun LazyListScope.settingsTab(
    state: MobieUiState,
    darkTheme: Boolean,
    tracker: EntranceTracker,
    onDarkThemeChange: (Boolean) -> Unit,
    onSaveToken: (String) -> Unit,
    onStatsForNerdsChange: (Boolean) -> Unit,
    onReplySpeedChange: (Int?) -> Unit,
    onAdvancedSettings: () -> Unit,
) {
    item(key = "appearance") {
        SettingsGroup("Appearance", Modifier.entrance(tracker, "appearance", 1).padding(bottom = GroupGap)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        }
    }
    item(key = "access") {
        TokenSettings(state.tokenConfigured, onSaveToken, Modifier.entrance(tracker, "access", 2).padding(bottom = GroupGap))
    }
    item(key = "chat_settings") {
        SettingsGroup("Chat", Modifier.padding(bottom = GroupGap)) {
            TermButton("Chat settings", onClick = onAdvancedSettings, style = TermButtonStyle.Ghost,
                modifier = Modifier.testTag("runtime_settings"))
            Text("Adjust response settings. Local models also have CPU, GPU, and memory options.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show performance stats", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = state.statsForNerds, onCheckedChange = onStatsForNerdsChange,
                    modifier = Modifier.testTag("stats_for_nerds"))
            }
        }
    }
    item(key = "about") {
        val context = LocalContext.current
        SettingsGroup("About") {
            InfoRow("Repository", "github.com/YashasVM/Mobie", trailing = "›",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) })
            InfoRow("Licensing", "LiteRT-LM · Lucide Icons · IBM Plex Mono (OFL)")
            Text("Made by @yashas.vm", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TokenSettings(tokenConfigured: Boolean, onSaveToken: (String) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    // Deliberately not saveable: a credential draft must not be written into saved instance state.
    var draft by remember { mutableStateOf("") }
    SettingsGroup("Account", modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp).animateContentSize(tween(MotionMedium, easing = EmphasizedEase)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Hugging Face access", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (tokenConfigured) "Saved securely on this phone."
                        else "Optional for gated models.",
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
                    textStyle = Mobie.numeric.small,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Access token") },
                    supportingText = { Text("Leave blank and save to remove it.", style = MaterialTheme.typography.bodySmall) },
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TermButton("Cancel", onClick = { editing = false; draft = "" }, style = TermButtonStyle.Ghost)
                    TermButton("Save", onClick = { onSaveToken(draft); editing = false; draft = "" }, style = TermButtonStyle.Primary)
                }
            } else {
                TermButton(
                    if (tokenConfigured) "Change token" else "Add token",
                    onClick = { editing = true },
                    style = TermButtonStyle.Ghost,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(label, Modifier.semantics { heading() }.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun InfoRow(
    title: String,
    body: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick)
                else Modifier,
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Text(trailing, style = Mobie.numeric.large, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// endregion

// region Shared pieces

/**
 * Section heading in the website's `h2` style: amber title, then a dashed rule to the edge.
 * Text is kept in its natural case so screen readers and tests read it as written.
 */
@Composable
private fun ListHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailingNote: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Text("[$count]", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DashedRule(Modifier.weight(1f))
        if (trailingNote != null) {
            Text(trailingNote, style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        action?.let { (label, onClick) -> TermButton(label, onClick = onClick, style = TermButtonStyle.Ghost) }
    }
}

@Composable
private fun DashedRule(modifier: Modifier = Modifier) {
    val rule = MaterialTheme.colorScheme.outline.copy(alpha = .5f)
    Canvas(modifier.height(1.dp)) {
        drawLine(
            rule,
            Offset(0f, 0f),
            Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** Tone of a list row. Colors flip to the inverted palette while the row is pressed, like the website's `.row.on`. */
private class RowTone(val inverted: Boolean, val ink: Color, val dim: Color) {
    fun tint(color: Color): Color = if (inverted) ink else color
}

/** Flat terminal list row with a hairline below. Pressing it inverts the row to amber. */
@Composable
private fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (RowTone) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(MotionShort),
        label = "row inverse",
    )
    val ink = MaterialTheme.colorScheme.background
    val tone = if (pressed) {
        RowTone(inverted = true, ink = ink, dim = ink.copy(alpha = .7f))
    } else {
        RowTone(inverted = false, ink = MaterialTheme.colorScheme.onSurface, dim = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(background)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content(tone)
        }
        Hairline()
    }
}

@Composable
private fun MessagePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    mark: String = "[i]",
    markColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    primaryAction: Boolean = false,
    announce: Boolean = false,
) {
    TuiWindow(
        title = title,
        meta = mark,
        metaColor = markColor,
        borderColor = markColor.copy(alpha = .6f),
        modifier = modifier
            .fillMaxWidth()
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                TermButton(
                    actionLabel,
                    onClick = onAction,
                    style = if (primaryAction) TermButtonStyle.Primary else TermButtonStyle.Ghost,
                    icon = if (actionLabel == "Retry") LucideR.drawable.lucide_ic_refresh_cw else null,
                )
            }
        }
    }
}

// endregion
