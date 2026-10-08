package dev.yashasvm.mobie.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
private const val TAB_COUNT = 3

/** List items before this index stagger in; later ones (reached by scrolling) appear without delay. */
private const val STAGGER_LIMIT = 8

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
    // One tracker per list, hoisted above the tab switcher so revisiting a tab doesn't replay entrances.
    val discoverEntrance = rememberEntranceTracker()
    val installedEntrance = rememberEntranceTracker()
    val settingsEntrance = rememberEntranceTracker()

    BackHandler(enabled = selectedTab != TAB_DISCOVER) { selectedTab = TAB_DISCOVER }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                // Shared-axis: content moves in the direction of the tab that was picked.
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(tween(MotionMedium, easing = EmphasizedEase)) { it / 8 * direction } +
                        fadeIn(tween(MotionMedium, delayMillis = 40))
                    ) togetherWith (
                    slideOutHorizontally(tween(MotionMedium, easing = EmphasizedEase)) { -it / 8 * direction } +
                        fadeOut(tween(MotionShort))
                    )
            },
            label = "catalog tab",
        ) { tab ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    TAB_INSTALLED -> installedTab(
                        state = state,
                        resolver = resolver,
                        tracker = installedEntrance,
                        onOpen = onOpenInstalled,
                        onDelete = { pendingDelete = it },
                        onBrowse = { selectedTab = TAB_DISCOVER },
                    )
                    TAB_SETTINGS -> settingsTab(
                        tokenConfigured = state.tokenConfigured,
                        darkTheme = darkTheme,
                        tracker = settingsEntrance,
                        onDarkThemeChange = onDarkThemeChange,
                        onSaveToken = onSaveToken,
                    )
                    else -> discoverTab(
                        state = state,
                        resolver = resolver,
                        tracker = discoverEntrance,
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

// region Discover

private fun LazyListScope.discoverTab(
    state: MobieUiState,
    resolver: CompatibilityResolver,
    tracker: EntranceTracker,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            title = "Models for this phone",
            subtitle = "Sized against this phone's memory. Once downloaded, they run fully offline.",
            modifier = Modifier.entrance(tracker, "header", 0),
        )
    }
    item(key = "rig") { DeviceRig(state.device, Modifier.entrance(tracker, "rig", 1)) }
    item(key = "search") {
        Box(Modifier.entrance(tracker, "search", 2)) {
            SearchField(state.query, onQuery, onSearch, onClear = onFeatured)
        }
    }
    item(key = "section") {
        val searching = state.query.isNotBlank()
        ListHeader(
            title = if (searching) "Compatible results" else "Recommended",
            count = state.models.size.takeIf { !state.loading && state.error == null && it > 0 },
            action = if (searching) ("Clear" to onFeatured) else null,
            modifier = Modifier.entrance(tracker, "section", 3),
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
                modifier = Modifier.appear(),
            )
        }
        state.models.isEmpty() -> item(key = "empty") {
            MessagePanel(
                icon = LucideR.drawable.lucide_ic_search_x,
                title = "No matching models",
                body = "Only LiteRT-LM models from litert-community are listed. Try another name.",
                actionLabel = if (state.query.isNotBlank()) "Show recommended" else null,
                onAction = onFeatured,
                modifier = Modifier.appear(),
            )
        }
        else -> itemsIndexed(state.models, key = { _, model -> model.id }) { index, model ->
            val compatibility = remember(model, state.device) {
                state.device?.let { resolver.resolve(model.bestArtifact, it) }
            }
            val installed = state.installedModels.any { it.model.id == model.id }
            Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(MotionShort))) {
                ModelRow(
                    model = model,
                    compatibility = compatibility,
                    device = state.device,
                    installed = installed,
                    onClick = { onSelect(model) },
                    modifier = Modifier.entrance(tracker, "model:${model.id}", if (index < STAGGER_LIMIT) index + 1 else 0),
                )
            }
        }
    }
}

@Composable
private fun DeviceRig(device: DeviceProfile?, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth().animateContentSize(tween(MotionMedium, easing = EmphasizedEase)), shape = CardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Memory in use",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatBytes(used)} of ${formatBytes(device.totalRamBytes)}",
                            style = Mobie.numeric.small,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    MemoryBar(
                        segments = listOf(
                            MemorySegment("In use", used, MaterialTheme.colorScheme.onSurfaceVariant),
                        ),
                        capacityBytes = device.totalRamBytes,
                        height = 6.dp,
                    )
                }
            }
            Hairline()
            // Two rows of two keep labels unclipped on narrow phones.
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BytesSpec("Total RAM", device?.totalRamBytes ?: 0, Modifier.weight(1f))
                    VerticalHairline(36.dp)
                    BytesSpec(
                        "Available RAM",
                        device?.availableRamBytes ?: 0,
                        Modifier.weight(1f),
                        valueColor = if (device?.isLowMemory == true) Mobie.signals.caution else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BytesSpec("Free storage", device?.availableStorageBytes ?: 0, Modifier.weight(1f))
                    VerticalHairline(36.dp)
                    Spec(
                        "Processor",
                        device?.supportedAbis?.firstOrNull()?.substringBefore('-') ?: "—",
                        Modifier.weight(1f),
                        valueStyle = Mobie.numeric.medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val iconTint by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(MotionShort),
        label = "search icon tint",
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().testTag("model_search"),
        singleLine = true,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text("Search models, e.g. gemma", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(20.dp), tint = iconTint) },
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
        shape = FieldShape,
        // Material animates the border between these two colors when focus changes.
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
    modifier: Modifier = Modifier,
) {
    val artifact = model.bestArtifact
    val status = compatibility?.status
    val statusColor = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else status.signalColor()
    val estimatedRam = compatibility?.estimatedRamBytes ?: 0
    val context = compatibility?.contextWindowTokens?.takeIf { it > 0 } ?: artifact?.contextWindowTokens ?: 0
    Panel(
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = CardShape,
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "by ${model.author}",
                        style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemoryBar(
                        segments = listOf(MemorySegment("Estimated RAM", estimatedRam, statusColor)),
                        capacityBytes = device.totalRamBytes,
                        markerBytes = device.availableRamBytes.takeIf { it > 0 },
                        height = 6.dp,
                    )
                    Text(
                        "Needs about ${formatBytes(estimatedRam)} of ${formatBytes(device.totalRamBytes)} RAM · " +
                            "${formatBytes(device.availableRamBytes)} free now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (installed || model.supportsVision || model.gated) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed) Tag("Installed", color = Mobie.signals.ready, icon = LucideR.drawable.lucide_ic_hard_drive)
                    if (model.supportsVision) Tag("Vision", icon = LucideR.drawable.lucide_ic_eye)
                    if (model.gated) Tag("Needs access", icon = LucideR.drawable.lucide_ic_lock)
                }
            }
        }
    }
}

/** Four specs separated by hairlines: size, quantization, estimated RAM, context. */
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BytesSpec(sizeLabel, sizeBytes, Modifier.weight(1f), valueStyle = Mobie.numeric.small)
        VerticalHairline(34.dp)
        Spec("Quant", quantization ?: "—", Modifier.weight(1f))
        VerticalHairline(34.dp)
        val (ram, ramUnit) = splitBytes(estimatedRamBytes)
        Spec(
            "Est. RAM",
            if (estimatedRamBytes > 0) "~$ram" else "—",
            Modifier.weight(1f),
            unit = ramUnit.takeIf { estimatedRamBytes > 0 },
        )
        VerticalHairline(34.dp)
        Spec(
            "Context",
            formatTokens(contextTokens),
            Modifier.weight(1f),
            unit = "tok".takeIf { contextTokens > 0 },
        )
    }
}

@Composable
private fun SkeletonList() {
    Column(
        Modifier.semantics {
            contentDescription = "Loading models"
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) { index ->
            Panel(Modifier.fillMaxWidth().appear(delayMillis = index * 60), shape = CardShape) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBar(Modifier.weight(1f), widthFraction = .55f, height = 20.dp)
                        SkeletonBar(Modifier.width(80.dp), height = 22.dp)
                    }
                    SkeletonBar(widthFraction = .3f, height = 12.dp)
                    SkeletonBar(widthFraction = .9f, height = 12.dp)
                    SkeletonBar(height = 48.dp)
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
                .clip(RoundedCornerShape(4.dp))
                .shimmer(),
        )
    }
}

// endregion

// region Installed

private fun LazyListScope.installedTab(
    state: MobieUiState,
    resolver: CompatibilityResolver,
    tracker: EntranceTracker,
    onOpen: (InstalledModelEntry) -> Unit,
    onDelete: (InstalledModelEntry) -> Unit,
    onBrowse: () -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            title = "Your local models",
            subtitle = "Stored on this phone and ready offline. Tap one to start a new chat.",
            modifier = Modifier.entrance(tracker, "header", 0),
        )
    }
    state.error?.let { message ->
        item(key = "error") { InlineError(message, Modifier.appear()) }
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
                modifier = Modifier.entrance(tracker, "section", 1),
            )
        }
        itemsIndexed(state.installedModels, key = { _, entry -> entry.model.id }) { index, entry ->
            val compatibility = remember(entry.model, state.device) {
                state.device?.let { resolver.resolve(entry.model.bestArtifact, it) }
            }
            // Placement animates when a neighbour is deleted; the entrance plays once on the content.
            Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(MotionShort))) {
                InstalledRow(
                    entry = entry,
                    compatibility = compatibility,
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
    compatibility: CompatibilityResult?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = entry.model
    val artifact = model.bestArtifact
    val context = compatibility?.contextWindowTokens?.takeIf { it > 0 } ?: artifact?.contextWindowTokens ?: 0
    Panel(
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = CardShape,
        onClick = onOpen,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "by ${model.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    LucideIcon(
                        LucideR.drawable.lucide_ic_trash_2,
                        "Delete ${model.title}",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpecStrip(
                    sizeBytes = artifact?.sizeBytes ?: 0,
                    quantization = artifact?.quantization,
                    estimatedRamBytes = compatibility?.estimatedRamBytes ?: 0,
                    contextTokens = context,
                    sizeLabel = "On disk",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(Mobie.signals.ready, size = 7.dp)
                    Text(
                        listOfNotNull("On this phone", artifact?.runtimeLabel, "Vision".takeIf { model.supportsVision }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Start chat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LucideIcon(LucideR.drawable.lucide_ic_chevron_right, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
    tracker: EntranceTracker,
    onDarkThemeChange: (Boolean) -> Unit,
    onSaveToken: (String) -> Unit,
) {
    item(key = "header") {
        ScreenHeader(
            title = "Settings",
            subtitle = "Access, appearance, and how Mobie handles your data.",
            modifier = Modifier.entrance(tracker, "header", 0),
        )
    }
    item(key = "appearance") {
        SettingsGroup("Appearance", Modifier.entrance(tracker, "appearance", 1)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = darkTheme, role = Role.Switch, onValueChange = onDarkThemeChange)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconTile(LucideR.drawable.lucide_ic_sun_moon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    item(key = "access") { TokenSettings(tokenConfigured, onSaveToken, Modifier.entrance(tracker, "access", 2)) }
    item(key = "privacy") {
        SettingsGroup("Privacy", Modifier.entrance(tracker, "privacy", 3)) {
            Column {
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_shield_check,
                    title = "Prompts and replies stay here",
                    body = "Every model runs on this phone. What you type and what the model writes is never sent anywhere.",
                    iconTint = Mobie.signals.ready,
                )
                Hairline(Modifier.padding(start = 64.dp))
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
        SettingsGroup("About", Modifier.entrance(tracker, "about", 4)) {
            Column {
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_github,
                    title = "Repository",
                    body = "github.com/YashasVM/Mobie",
                    trailingIcon = LucideR.drawable.lucide_ic_external_link,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) },
                )
                Hairline(Modifier.padding(start = 64.dp))
                InfoRow(
                    icon = LucideR.drawable.lucide_ic_file_text,
                    title = "Licensing",
                    body = "LiteRT-LM · Lucide Icons",
                )
                Hairline(Modifier.padding(start = 64.dp))
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
            "Made by @yashas.vm",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).entrance(tracker, "credit", 5),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TokenSettings(tokenConfigured: Boolean, onSaveToken: (String) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    // Deliberately not saveable: a credential draft must not be written into saved instance state.
    var draft by remember { mutableStateOf("") }
    SettingsGroup("Account", modifier) {
        Column(
            Modifier.padding(16.dp).animateContentSize(tween(MotionMedium, easing = EmphasizedEase)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(LucideR.drawable.lucide_ic_key_round)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    textStyle = Mobie.numeric.small,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Access token") },
                    supportingText = { Text("Leave blank and save to remove it.", style = MaterialTheme.typography.bodySmall) },
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
private fun SettingsGroup(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tint = iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingIcon != null) {
            LucideIcon(trailingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion

// region Shared pieces

@Composable
private fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** List section heading. Text is kept in its natural case so screen readers and tests read it as written. */
@Composable
private fun ListHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailingNote: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 2.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (count != null) Tag(count.toString(), mono = true)
        Spacer(Modifier.weight(1f))
        if (trailingNote != null) {
            Text(trailingNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

/**
 * Sentence-case label over a numeric value. Values roll when they change. Used instead of the
 * shared all-caps `Metric` so dense rows stay easy to read.
 */
@Composable
private fun Spec(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueStyle: TextStyle = Mobie.numeric.small,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RollingText(value, style = valueStyle.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
            if (unit != null) {
                Text(
                    unit,
                    style = Mobie.numeric.tiny,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun BytesSpec(
    label: String,
    bytes: Long,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = Mobie.numeric.medium,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val (value, unit) = splitBytes(bytes)
    Spec(
        label = label,
        value = if (bytes > 0) value else "—",
        modifier = modifier,
        unit = unit,
        valueStyle = valueStyle,
        valueColor = valueColor,
    )
}

@Composable
private fun IconTile(@DrawableRes icon: Int, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, IconTileShape)
            .border(1.dp, Mobie.signals.hairline, IconTileShape),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, null, Modifier.size(18.dp), tint = tint)
    }
}

@Composable
private fun MessagePanel(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    primaryAction: Boolean = false,
    announce: Boolean = false,
) {
    Panel(
        modifier
            .fillMaxWidth()
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        shape = CardShape,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(icon, tint = tint)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
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
                            LucideIcon(LucideR.drawable.lucide_ic_refresh_cw, null, Modifier.size(16.dp))
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
private fun InlineError(message: String, modifier: Modifier = Modifier) {
    val color = Mobie.signals.blocked
    Row(
        modifier
            .fillMaxWidth()
            .background(color.copy(alpha = .08f), CardShape)
            .border(1.dp, color.copy(alpha = .28f), CardShape)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_circle_alert, null, Modifier.size(18.dp), tint = color)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
            // One indicator that glides to the selected tab rather than three that blink.
            val tabWidth = maxWidth / TAB_COUNT
            val indicatorWidth = 28.dp
            val indicatorX by animateDpAsState(
                tabWidth * selectedTab + (tabWidth - indicatorWidth) / 2,
                tween(MotionMedium, easing = EmphasizedEase),
                label = "nav indicator x",
            )
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToPx(), 0) }
                    .width(indicatorWidth)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, (label, icon, tag) ->
                    val selected = selectedTab == index
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(MotionShort),
                        label = "$label tint",
                    )
                    val iconScale by animateFloatAsState(
                        if (selected) 1.1f else 1f,
                        tween(MotionMedium, easing = EmphasizedEase),
                        label = "$label icon scale",
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag(tag)
                            .selectable(
                                selected = selected,
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                            ) { onSelect(index) }
                            .pressScale(interaction, pressedScale = .92f),
                    ) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            LucideIcon(icon, null, Modifier.size(22.dp).scale(iconScale), tint = color)
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion
