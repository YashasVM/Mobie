package dev.yashasvm.mobie.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import dev.yashasvm.mobie.core.model.Compatibility
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

/** Vertical gap after each block. Rows inside a list sit flush and are separated by hairlines. */
private val GroupGap = 18.dp

/**
 * Home of the app: Discover (catalog sized against this phone), Installed (local library), and
 * Settings. All compatibility shown here comes from [CompatibilityResolver]; the screen never
 * parses catalog responses or touches a runtime.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // One list state per tab so each keeps its scroll position and the top bar knows when content sits under it.
    val listStates = List(TAB_COUNT) { rememberLazyListState() }
    val scrolled by remember(selectedTab) {
        derivedStateOf {
            val list = listStates[selectedTab]
            list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0
        }
    }

    Column(Modifier.fillMaxSize()) {
        HomeTopBar(
            title = TabTitles[selectedTab],
            scrolled = scrolled,
            trailing = if (selectedTab == TAB_DISCOVER) chipName(state.device) ?: deviceName(state.device) else null,
        )
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
            val list = @Composable {
                LazyColumn(
                    state = listStates[tab],
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 28.dp),
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
            if (tab == TAB_DISCOVER) {
                // Pull down to refresh the catalog, the gesture people expect on a phone list.
                PullToRefreshBox(
                    isRefreshing = state.loading && state.models.isNotEmpty(),
                    onRefresh = { if (state.query.isBlank()) onFeatured() else onSearch() },
                    modifier = Modifier.fillMaxSize(),
                ) { list() }
            } else {
                list()
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
    item(key = "rig") { DeviceStrip(state.device, Modifier.entrance(tracker, "rig", 0).padding(top = 4.dp, bottom = 12.dp)) }
    // Search stays pinned under the top bar while the list scrolls beneath it.
    stickyHeader(key = "search") {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .entrance(tracker, "search", 1)
                .padding(bottom = 10.dp),
        ) {
            SearchField(state.query, onQuery, onSearch, onClear = onFeatured)
        }
    }
    item(key = "section") {
        val searching = state.query.isNotBlank()
        ListHeader(
            title = if (searching) "Compatible results" else "Recommended",
            count = state.models.size.takeIf { !state.loading && state.catalogError == null && it > 0 },
            action = if (searching) ("Clear" to onFeatured) else null,
            modifier = Modifier.entrance(tracker, "section", 3).padding(bottom = 6.dp),
        )
    }
    when {
        state.loading -> item(key = "loading") { SkeletonList() }
        state.catalogError != null -> item(key = "error") {
            MessagePanel(
                title = "Couldn't load models",
                body = state.catalogError,
                mark = "[!]",
                markColor = Mobie.signals.blocked,
                actionLabel = "Retry",
                onAction = onFeatured,
                announce = true,
                modifier = Modifier.appear(),
            )
        }
        state.models.isEmpty() -> item(key = "empty") {
            MessagePanel(
                title = "No matching models",
                body = "Only LiteRT-LM models from litert-community are listed. Try another name.",
                mark = "[ ]",
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

/** The phone's budget at a glance: free memory and storage over one meter. Models below are sized against it. */
@Composable
private fun DeviceStrip(device: DeviceProfile?, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StripFact(
                "memory free",
                device?.let { "${bytesOrDash(it.availableRamBytes)} / ${bytesOrDash(it.totalRamBytes)}" } ?: "…",
                valueColor = if (device?.isLowMemory == true) Mobie.signals.caution else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StripFact("storage free", bytesOrDash(device?.availableStorageBytes))
        }
        if (device != null && device.totalRamBytes > 0) {
            val used = (device.totalRamBytes - device.availableRamBytes).coerceAtLeast(0)
            MemoryBar(
                segments = listOf(MemorySegment("In use", used, muted)),
                capacityBytes = device.totalRamBytes,
                height = 6.dp,
            )
        }
    }
}

@Composable
private fun StripFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = Mobie.numeric.medium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text("Search models, e.g. gemma", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        // A shell prompt glyph in place of a magnifier: this field is a `grep` over the catalog.
        leadingIcon = { Text("/", style = Mobie.numeric.medium, color = promptColor) },
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
    val ramShownBelow = device != null && device.totalRamBytes > 0 && estimatedRam > 0
    val specs = listOfNotNull(
        specLine(artifact?.quantization, estimatedRam, context, ramShownBelow).ifBlank { null },
        "installed".takeIf { installed },
        "vision".takeIf { model.supportsVision },
        "needs access".takeIf { model.gated },
    ).joinToString(" · ")
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
        if (specs.isNotBlank()) SpecText(specs, tone.dim)
        Row(
            Modifier.padding(start = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                statusLabel(status),
                style = Mobie.numeric.tiny.copy(fontWeight = FontWeight.SemiBold),
                color = tone.tint(statusColor),
                maxLines = 1,
            )
            if (device != null && device.totalRamBytes > 0 && estimatedRam > 0) {
                MemoryBar(
                    segments = listOf(MemorySegment("Estimated RAM", estimatedRam, tone.tint(statusColor))),
                    capacityBytes = device.totalRamBytes,
                    markerBytes = device.availableRamBytes.takeIf { it > 0 },
                    height = 6.dp,
                    modifier = Modifier.weight(1f),
                )
                Text("~${formatBytes(estimatedRam)} RAM", style = Mobie.numeric.tiny, color = tone.dim, maxLines = 1)
            } else {
                Spacer(Modifier.weight(1f))
            }
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

/** Byte count for a key/value row, or a dash when the platform reports nothing. */
private fun bytesOrDash(bytes: Long?): String = if (bytes == null || bytes <= 0) "—" else formatBytes(bytes)

/** `quant · ~RAM · context`, skipping any value that is unknown. */
private fun specLine(quantization: String?, estimatedRamBytes: Long, contextTokens: Int, ramShownBelow: Boolean = false): String =
    listOfNotNull(
        quantization,
        // RAM is spelled out under the meter below, so the spec line carries only file facts.
        "~${formatBytes(estimatedRamBytes)} RAM".takeIf { estimatedRamBytes > 0 && !ramShownBelow },
        "${formatTokens(contextTokens)} ctx".takeIf { contextTokens > 0 },
    ).joinToString(" · ")

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
    resolver: CompatibilityResolver,
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
                title = "Nothing downloaded yet",
                body = "Models you download live here, ready for private chats without a connection.",
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
                modifier = Modifier.entrance(tracker, "section", 1),
            )
            Text(
                "Tap to chat · swipe left to delete",
                style = Mobie.numeric.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
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
@OptIn(ExperimentalMaterial3Api::class)
private fun InstalledRow(
    entry: InstalledModelEntry,
    compatibility: CompatibilityResult?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = entry.model
    val artifact = model.bestArtifact
    val estimatedRam = compatibility?.estimatedRamBytes ?: 0
    val context = compatibility?.contextWindowTokens?.takeIf { it > 0 } ?: artifact?.contextWindowTokens ?: 0
    val specs = specLine(artifact?.quantization, estimatedRam, context)
    val haptics = LocalHapticFeedback.current
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            // Swiping only asks; the dialog confirms. The row springs back either way.
            if (it == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
            false
        },
        positionalThreshold = { it * .35f },
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(Mobie.signals.blocked).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text("delete", style = Mobie.numeric.small, color = MaterialTheme.colorScheme.background)
                LucideIcon(LucideR.drawable.lucide_ic_trash_2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.background)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            InstalledRowContent(entry, specs, onOpen, onDelete)
        }
    }
}

@Composable
private fun InstalledRowContent(entry: InstalledModelEntry, specs: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    val model = entry.model
    val artifact = model.bestArtifact
    ListRow(onClick = onOpen) { tone ->
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
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                LucideIcon(
                    LucideR.drawable.lucide_ic_trash_2,
                    "Delete ${model.title}",
                    Modifier.size(18.dp),
                    tint = tone.dim,
                )
            }
            LucideIcon(LucideR.drawable.lucide_ic_chevron_right, null, Modifier.size(18.dp), tint = tone.tint(MaterialTheme.colorScheme.primary))
        }
        SpecText(
            listOfNotNull(
                formatBytes(artifact?.sizeBytes ?: 0),
                specs.ifBlank { null },
                artifact?.runtimeLabel,
                "vision".takeIf { model.supportsVision },
            ).joinToString(" · "),
            tone.dim,
        )
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
    item(key = "appearance") {
        SettingsGroup("Appearance", Modifier.entrance(tracker, "appearance", 1).padding(bottom = GroupGap)) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = darkTheme, role = Role.Switch, onValueChange = onDarkThemeChange)
                        .heightIn(min = 52.dp)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Dark mode", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
    item(key = "access") {
        TokenSettings(tokenConfigured, onSaveToken, Modifier.entrance(tracker, "access", 2).padding(bottom = GroupGap))
    }
    item(key = "privacy") {
        SettingsGroup("Privacy", Modifier.entrance(tracker, "privacy", 3).padding(bottom = GroupGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PrivacyLine(
                    title = "prompts never leave this phone",
                    body = "Every model runs on this phone. What you type and what the model writes is never sent anywhere.",
                    mark = "✓",
                    color = Mobie.signals.ready,
                )
                PrivacyLine(
                    title = "network is used only for models",
                    body = "Only to browse the Hugging Face catalog and download model files.",
                    mark = "i",
                    color = Mobie.signals.thinking,
                )
            }
        }
    }
    item(key = "about") {
        val context = LocalContext.current
        SettingsGroup("About", Modifier.entrance(tracker, "about", 4).padding(bottom = GroupGap)) {
            Column {
                InfoRow(
                    title = "Repository",
                    body = "github.com/YashasVM/Mobie",
                    trailing = "›",
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) },
                )
                Hairline()
                InfoRow(
                    title = "Licensing",
                    body = "LiteRT-LM · Lucide Icons · IBM Plex Mono (OFL)",
                )
                Hairline()
                InfoRow(
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
            Modifier.fillMaxWidth().padding(vertical = 10.dp).animateContentSize(tween(MotionMedium, easing = EmphasizedEase)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Hugging Face access", style = MaterialTheme.typography.titleSmall)
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

/** `[✓] title` checklist line, with its explanation indented beneath. */
@Composable
private fun PrivacyLine(title: String, body: String, mark: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckLine(title, mark, color)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp),
        )
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

private val TabTitles = listOf("Discover", "Library", "Settings")

/**
 * App bar for the home tabs: a prompt and the tab title, with an optional dim note on the right.
 * A hairline fades in once content scrolls beneath it, the way native top bars lift on scroll.
 */
@Composable
private fun HomeTopBar(title: String, scrolled: Boolean, trailing: String? = null) {
    val rule by animateColorAsState(
        if (scrolled) MaterialTheme.colorScheme.outline.copy(alpha = .5f) else Color.Transparent,
        tween(MotionShort),
        label = "top bar rule",
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$", style = Mobie.numeric.large, color = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (trailing != null) {
                Text(trailing, style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
    }
}

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

@Composable
private fun ConsoleNavBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    // Icon-over-label tabs, like any phone app. The selected tab gets an amber indicator above its icon.
    val items = listOf(
        Triple("discover", "bottom_nav_discover", LucideR.drawable.lucide_ic_compass),
        Triple("library", "bottom_nav_installed", LucideR.drawable.lucide_ic_hard_drive),
        Triple("settings", "bottom_nav_settings", LucideR.drawable.lucide_ic_settings),
    )
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEachIndexed { index, (label, tag, icon) ->
                val selected = selectedTab == index
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val ink by animateColorAsState(
                    when {
                        selected -> MaterialTheme.colorScheme.primary
                        pressed -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    tween(MotionShort),
                    label = "$label tab ink",
                )
                val indicator by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent,
                    tween(MotionShort),
                    label = "$label tab indicator",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag(tag)
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.width(56.dp).height(28.dp).background(indicator),
                        contentAlignment = Alignment.Center,
                    ) {
                        LucideIcon(icon, null, Modifier.size(20.dp), tint = ink)
                    }
                    Text(label, style = Mobie.numeric.tiny, color = ink, maxLines = 1)
                }
            }
        }
    }
}

// endregion
