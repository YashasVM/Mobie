package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.ui.theme.Mobie

internal const val REPOSITORY_URL = "https://github.com/YashasVM/Mobie"

@Composable
fun MobieApp(
    container: AppContainer,
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    // Saved flags and the last-used model are read off the main thread; show only the backdrop until
    // they arrive, so the app opens straight into chat without flashing the catalog first.
    if (!state.preferencesLoaded || !state.launchResolved) {
        AppBackdrop(Modifier.fillMaxSize()) {}
        return
    }

    var warningOverride by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showComputeSettings by rememberSaveable { mutableStateOf(false) }
    var catalogTab by rememberSaveable { mutableIntStateOf(TAB_DISCOVER) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.download(warningOverride)
        warningOverride = false
    }
    fun requestDownload(allowWarning: Boolean) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(container.appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            warningOverride = allowWarning
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.download(allowWarning)
        }
    }
    // Chat is home; the catalog and its tabs open on top of it from the drawer.
    fun openCatalog(tab: Int) {
        scope.launch { drawerState.close() }
        catalogTab = tab
        viewModel.leaveChat()
        viewModel.select(null)
    }

    BackHandler(enabled = state.selected != null && !state.chatting && !state.openingChat) { viewModel.select(null) }
    AppBackdrop(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when {
                // While returning to chat, stay on the chat pane instead of passing through the model page.
                state.chatting || state.openingChat -> "chat"
                state.selected != null -> "model"
                else -> "catalog"
            },
            transitionSpec = {
                // Shared-axis motion kept short and flat, like a terminal redrawing a pane: deeper
                // screens (catalog → model → chat) slide in from the right, going back reverses it.
                val forward = screenDepth(targetState) > screenDepth(initialState)
                val direction = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(MotionShort, easing = EmphasizedEase)) { it / 10 * direction } +
                        fadeIn(tween(MotionShort, easing = LinearEasing))
                    ) togetherWith (
                    slideOutHorizontally(tween(MotionShort, easing = EmphasizedEase)) { -it / 14 * direction } +
                        fadeOut(tween(MotionShort / 2, easing = LinearEasing))
                    )
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                "chat" -> ModalNavigationDrawer(
                    drawerState = drawerState,
                    scrimColor = Color.Black.copy(alpha = .5f),
                    drawerContent = {
                        ChatDrawer(
                            onNewChat = {
                                scope.launch { drawerState.close() }
                                viewModel.newChat()
                            },
                            onHistory = {
                                scope.launch { drawerState.close() }
                                showHistory = true
                            },
                            onModels = { openCatalog(TAB_DISCOVER) },
                            onLibrary = { openCatalog(TAB_INSTALLED) },
                            onSettings = { openCatalog(TAB_SETTINGS) },
                        )
                    },
                ) {
                    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
                    ChatScreen(
                        state = state,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                        onMenu = { scope.launch { drawerState.open() } },
                        onBack = { openCatalog(TAB_DISCOVER) },
                        onHistory = { showHistory = true },
                        onNewChat = viewModel::newChat,
                        onComputeSettings = { showComputeSettings = true },
                        onLoadAnyway = { viewModel.runInstalled(allowReclaim = true) },
                    )
                }
                "model" -> ModelScreen(
                    state = state,
                    onDownload = ::requestDownload,
                    onRun = viewModel::runInstalled,
                    onCancelDownload = viewModel::cancelDownload,
                    onBackendChange = viewModel::setComputeBackend,
                    onCpuThreadsChange = viewModel::setCpuThreads,
                    onComputeOptionsChange = viewModel::updateComputeOptions,
                    onSamplerChange = viewModel::updateSampler,
                    onApplyCompute = viewModel::applyComputeSettings,
                    onResetCompute = viewModel::resetComputeSettings,
                    onMemoryRefresh = viewModel::refreshMemory,
                    onFreeMemory = viewModel::freeUpMemory,
                    onBack = { viewModel.select(null) },
                )
                else -> CatalogScreen(
                    state,
                    viewModel::setQuery,
                    viewModel::search,
                    viewModel::select,
                    viewModel::loadFeatured,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onSaveToken = viewModel::saveToken,
                    onOpenInstalled = viewModel::startInstalledChat,
                    onDeleteInstalled = viewModel::deleteInstalled,
                    initialTab = catalogTab,
                    onTabChange = { catalogTab = it },
                    // With a model installed, back returns to the chat home instead of leaving the app.
                    onBack = viewModel::openLastChat.takeIf { state.installedModels.isNotEmpty() },
                )
            }
        }
    }
    if (showComputeSettings && state.chatting) {
        ComputeSettingsSheet(state, viewModel::updateComputeOptions, viewModel::updateSampler,
            onApply = { viewModel.applyComputeSettings(); showComputeSettings = false },
            onReset = viewModel::resetComputeSettings, onClose = { showComputeSettings = false })
    }
    if (showHistory) {
        HistorySheet(
            sessions = state.history,
            onDismiss = { showHistory = false },
            onSelect = {
                showHistory = false
                viewModel.selectHistory(it)
            },
        )
    }
}

/** The chat home's side menu: the rest of the app, reached from the chat. */
@Composable
private fun ChatDrawer(
    onNewChat: () -> Unit,
    onHistory: () -> Unit,
    onModels: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.testTag("chat_drawer"),
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        windowInsets = WindowInsets(0),
    ) {
        Column(Modifier.statusBarsPadding()) {
            StatusLine(listOf(StatusSegment("mobie"), StatusSegment("menu")), selected = 1)
            Spacer(Modifier.height(8.dp))
            DrawerRow(LucideR.drawable.lucide_ic_plus, "new chat", "drawer_new_chat", onNewChat)
            DrawerRow(LucideR.drawable.lucide_ic_history, "chat history", "drawer_history", onHistory)
            Hairline(Modifier.padding(vertical = 8.dp))
            DrawerRow(LucideR.drawable.lucide_ic_compass, "discover models", "drawer_models", onModels)
            DrawerRow(LucideR.drawable.lucide_ic_library, "library", "drawer_library", onLibrary)
            DrawerRow(LucideR.drawable.lucide_ic_settings, "settings", "drawer_settings", onSettings)
        }
    }
}

@Composable
private fun DrawerRow(@DrawableRes icon: Int, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(">", style = Mobie.numeric.medium, color = MaterialTheme.colorScheme.primary)
        LucideIcon(icon, null, Modifier.size(20.dp))
        Text(label, style = Mobie.numeric.medium)
    }
}

@Composable
private fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    // A faint scanline texture, drawn once per size. Lines are light on dark and dark on light,
    // at alpha 0.03, so they read as CRT texture rather than as a pattern.
    val scanline = if (background.luminance() < .5f) Color.White.copy(alpha = .03f) else Color.Black.copy(alpha = .03f)
    Box(
        modifier
            .background(background)
            .drawWithCache {
                val step = 4.dp.toPx()
                val thickness = 1.dp.toPx()
                val lines = Path().apply {
                    var y = 0f
                    while (y < size.height) {
                        addRect(Rect(0f, y, size.width, y + thickness))
                        y += step
                    }
                }
                onDrawBehind { drawPath(lines, scanline) }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(sessions: List<ChatHistorySession>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val visible = remember(sessions) { sessions.filter { it.messages.isNotEmpty() } }
    val entrance = rememberEntranceTracker()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = .6f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)),
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("history", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${visible.size} saved",
                            style = Mobie.numeric.tiny,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LucideIcon(LucideR.drawable.lucide_ic_lock, null, Modifier.size(12.dp), tint = Mobie.signals.ready)
                        Text(
                            "Stored only on this phone",
                            style = Mobie.numeric.tiny,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Hairline()
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No saved chats yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Chats you finish with this model are saved here, on this phone only.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(visible, key = { _, session -> session.id }) { index, session ->
                    HistoryRow(
                        session = session,
                        onClick = { onSelect(session.id) },
                        modifier = Modifier.entrance(entrance, session.id, index),
                    )
                }
            }
        }
    }
}

/** One saved chat as a terminal list row: dim timestamp over the first prompt. Inverts while pressed. */
@Composable
private fun HistoryRow(session: ChatHistorySession, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val dim = if (pressed) MaterialTheme.colorScheme.background.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(if (pressed) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                ">",
                style = Mobie.numeric.small,
                color = if (pressed) ink else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${historyTime(session.updatedAt)} · ${session.messages.size} msg",
                    style = Mobie.numeric.tiny,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Hairline()
    }
}

private fun screenDepth(screen: String): Int = when (screen) {
    "chat" -> 2
    "model" -> 1
    else -> 0
}
