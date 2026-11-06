package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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

internal enum class AppDestination { EXPLORE, TASK, CHAT, MODELS, SETTINGS, MODEL_DETAILS }

@Composable
fun MobieApp(
    container: AppContainer,
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    if (!state.preferencesLoaded || !state.launchResolved) {
        AppBackdrop(Modifier.fillMaxSize()) {}
        return
    }

    var destination by rememberSaveable {
        mutableStateOf(if (state.chatting || state.openingChat) AppDestination.CHAT else AppDestination.EXPLORE)
    }
    var exploreTask by rememberSaveable { mutableStateOf(ExploreTask.LAB) }
    val taskDrafts = rememberSaveableStateHolder()
    var warningOverride by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showComputeSettings by rememberSaveable { mutableStateOf(false) }
    var showTaskSettings by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var pendingHistory by remember { mutableStateOf<ChatHistorySession?>(null) }
    var pendingCloud by remember { mutableStateOf<dev.yashasvm.mobie.core.model.CloudModel?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.download(warningOverride)
        warningOverride = false
    }
    fun requestDownload(allowWarning: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(container.appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            warningOverride = allowWarning
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.download(allowWarning)
    }
    fun navigate(target: AppDestination) {
        scope.launch { drawerState.close() }
        destination = target
    }
    fun openMenu() {
        viewModel.refreshAllHistory()
        if (state.cloudConnectionUrl != null) viewModel.loadCloudModels()
        scope.launch { drawerState.open() }
    }
    fun openChat() {
        navigate(AppDestination.CHAT)
        if (!state.chatting && !state.openingChat) viewModel.openLastChat()
    }
    LaunchedEffect(state.chatting, state.openingChat) {
        if (state.chatting || state.openingChat) destination = AppDestination.CHAT
    }
    BackHandler(enabled = destination != AppDestination.CHAT && destination != AppDestination.EXPLORE) {
        when (destination) {
            AppDestination.MODEL_DETAILS -> navigate(AppDestination.MODELS)
            AppDestination.TASK -> navigate(AppDestination.EXPLORE)
            else -> navigate(AppDestination.EXPLORE)
        }
    }

    AppBackdrop(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = .5f),
            drawerContent = {
                ChatDrawer(
                    sessions = state.allHistory,
                    activeSessionId = state.sessionId,
                    activeModelId = state.selected?.id,
                    onNewChat = {
                        val hasChatModel = state.selected?.cloudModel != null ||
                            (state.chatting && state.selectedArtifact != null && state.downloadedPath != null)
                        if (hasChatModel) {
                            navigate(AppDestination.CHAT)
                            viewModel.newChat()
                        } else {
                            navigate(AppDestination.MODELS)
                        }
                    },
                    onSelectHistory = { session ->
                        val cloud = state.cloudModels.firstOrNull { it.asAiModel().id == session.modelId }
                        if (cloud != null) {
                            pendingHistory = session
                            pendingCloud = cloud
                            scope.launch { drawerState.close() }
                        } else {
                            viewModel.openSavedChat(session)
                            if (state.installedModels.any { it.model.id == session.modelId }) navigate(AppDestination.CHAT)
                        }
                    },
                    onModels = { navigate(AppDestination.MODELS) },
                    onSettings = { navigate(AppDestination.SETTINGS) },
                    onExplore = { navigate(AppDestination.EXPLORE) },
                )
            },
        ) {
            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn(tween(MotionShort)) togetherWith fadeOut(tween(MotionShort / 2)) },
                label = "screen",
            ) { screen ->
                when (screen) {
                    AppDestination.EXPLORE -> ExploreScreen(
                        state = state,
                        onMenu = ::openMenu,
                        onTask = { exploreTask = it; navigate(AppDestination.TASK) },
                        onModels = { navigate(AppDestination.MODELS) },
                    )
                    AppDestination.TASK -> taskDrafts.SaveableStateProvider(exploreTask.name) {
                        TaskWorkspace(
                            task = exploreTask, state = state,
                            onBack = { navigate(AppDestination.EXPLORE) },
                            onModels = { navigate(AppDestination.MODELS) },
                            onRun = { entry, prompt ->
                                viewModel.startInstalledChat(entry, prompt)
                                navigate(AppDestination.CHAT)
                            },
                            onSettings = { showTaskSettings = true },
                        )
                    }
                    AppDestination.CHAT -> {
                        if (state.chatting || state.openingChat) {
                            ChatScreen(
                                state = state,
                                onSend = viewModel::sendMessage,
                                onStop = viewModel::stopGeneration,
                                onMenu = ::openMenu,
                                onBack = { navigate(AppDestination.MODELS) },
                                onHistory = { showHistory = true },
                                onNewChat = viewModel::newChat,
                                onComputeSettings = { showComputeSettings = true },
                                onModelPicker = {
                                    showModelPicker = true
                                    if (state.cloudConnectionUrl != null) viewModel.loadCloudModels()
                                },
                                onBackendChange = viewModel::setComputeBackend,
                                onApplyCompute = viewModel::applyComputeSettings,
                                onReplySpeedChange = viewModel::setReplyTokensPerSecond,
                                onLoadAnyway = { viewModel.runInstalled(allowReclaim = true) },
                            )
                        } else {
                            ChatLanding(
                                onMenu = ::openMenu,
                                onModels = { navigate(AppDestination.MODELS) },
                            )
                        }
                    }
                    AppDestination.MODEL_DETAILS -> ModelScreen(
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
                        onBack = { navigate(AppDestination.MODELS) },
                    )
                    else -> CatalogScreen(
                        state = state,
                        onQuery = viewModel::setQuery,
                        onSearch = viewModel::search,
                        onSelect = { viewModel.select(it); navigate(AppDestination.MODEL_DETAILS) },
                        onFeatured = viewModel::loadFeatured,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onSaveToken = viewModel::saveToken,
                        onRefreshCloud = viewModel::loadCloudModels,
                        onSelectCloud = { viewModel.selectCloud(it); navigate(AppDestination.CHAT) },
                        onSaveCloudConnection = viewModel::saveCloudConnection,
                        onDisconnectCloud = viewModel::disconnectCloud,
                        onOpenInstalled = { viewModel.switchInstalledChat(it); navigate(AppDestination.CHAT) },
                        onDeleteInstalled = viewModel::deleteInstalled,
                        page = when (screen) {
                            AppDestination.SETTINGS -> CatalogPage.SETTINGS
                            else -> CatalogPage.MODELS
                        },
                        onMenu = ::openMenu,
                        onModels = { navigate(AppDestination.MODELS) },
                        onSettings = { navigate(AppDestination.SETTINGS) },
                        onStatsForNerdsChange = viewModel::setStatsForNerds,
                        onReplySpeedChange = viewModel::setReplyTokensPerSecond,
                        onAdvancedSettings = { showComputeSettings = true },
                    )
                }
            }
        }
    }
    if (showModelPicker) {
        ChatModelPicker(state,
            onDismiss = { showModelPicker = false },
            onInstalled = {
                showModelPicker = false
                if (state.selected?.id != it.model.id || !state.chatting) viewModel.switchInstalledChat(it)
            },
            onCloud = {
                showModelPicker = false
                if (state.selected?.cloudModel != it || !state.chatting) pendingCloud = it
            },
            onModels = { showModelPicker = false; navigate(AppDestination.MODELS) },
        )
    }
    pendingCloud?.let { cloud ->
        CloudModelConsent(cloud, onDismiss = { pendingCloud = null; pendingHistory = null }, onConfirm = {
            pendingCloud = null
            val history = pendingHistory
            pendingHistory = null
            if (history == null) viewModel.selectCloud(cloud) else viewModel.openSavedChat(history)
            navigate(AppDestination.CHAT)
        })
    }
    if (showTaskSettings) {
        ComputeSettingsSheet(
            state.copy(selected = null, selectedArtifact = null, activeCompute = null, computeCapabilities = null,
                appliedComputeOptions = null, appliedSampler = null, downloadedPath = null,
                decodeMeasurements = emptyList(), error = null),
            viewModel::updateComputeOptions, viewModel::updateSampler,
            onApply = { showTaskSettings = false }, onReset = viewModel::resetComputeSettings,
            onClose = { showTaskSettings = false }, onReplySpeedChange = viewModel::setReplyTokensPerSecond,
        )
    }
    if (showComputeSettings && state.selected != null && state.selected?.cloudModel == null) {
        ComputeSettingsSheet(state, viewModel::updateComputeOptions, viewModel::updateSampler,
            onApply = viewModel::applyComputeSettings,
            onReset = viewModel::resetComputeSettings, onClose = { showComputeSettings = false },
            onReplySpeedChange = viewModel::setReplyTokensPerSecond)
    }
    if (showComputeSettings && (state.selected == null || state.selected?.cloudModel != null)) {
        SimpleChatControlsSheet(state, viewModel::setComputeBackend, viewModel::applyComputeSettings,
            viewModel::setReplyTokensPerSecond, onClose = { showComputeSettings = false })
    }
    if (showHistory) {
        HistorySheet(state.history, onDismiss = { showHistory = false }, onSelect = {
            showHistory = false
            viewModel.selectHistory(it)
        })
    }
}

@Composable
private fun ChatLanding(onMenu: () -> Unit, onModels: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onMenu, modifier = Modifier.testTag("open_menu")) {
                LucideIcon(LucideR.drawable.lucide_ic_menu, "Open menu", Modifier.size(24.dp))
            }
            Text("Chat", style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TermButton("Choose a model", onClick = onModels, modifier = Modifier.testTag("choose_chat_model"))
        }
    }
}

/** Saved chats stay visible while model management and settings stay at the bottom. */
@Composable
internal fun ChatDrawer(
    sessions: List<ChatHistorySession>,
    activeSessionId: String? = null,
    activeModelId: String? = null,
    onNewChat: () -> Unit,
    onSelectHistory: (ChatHistorySession) -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onExplore: () -> Unit = {},
) {
    val visible = remember(sessions) {
        sessions.filter { it.messages.isNotEmpty() }.sortedByDescending { it.updatedAt }
    }
    ModalDrawerSheet(
        modifier = Modifier.testTag("chat_drawer"),
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        windowInsets = WindowInsets(0),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text("Mobie", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
            DrawerRow(LucideR.drawable.lucide_ic_plus, "New chat", "drawer_new_chat", onNewChat)
            Text(
                "Chat history",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("drawer_history")) {
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No saved chats yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(visible, key = { _, session -> "${session.modelId}:${session.id}" }) { _, session ->
                    HistoryRow(
                        session = session,
                        onClick = { onSelectHistory(session) },
                        selected = session.id == activeSessionId && session.modelId == activeModelId,
                        modifier = Modifier.testTag("drawer_session_${session.id}"),
                    )
                }
            }
            Hairline(Modifier.padding(vertical = 8.dp))
            DrawerRow(LucideR.drawable.lucide_ic_compass, "Explore", "drawer_explore", onExplore)
            DrawerRow(LucideR.drawable.lucide_ic_box, "Models", "drawer_models", onModels)
            DrawerRow(LucideR.drawable.lucide_ic_settings, "Settings", "drawer_settings", onSettings)
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
        LucideIcon(icon, null, Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
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
                        Text("Chat history", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
                            "Chat history saved on this phone",
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
                            "Chats are saved here on this phone. Cloud chats also send conversation history to the selected provider.",
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

/** A saved chat with its date and message count. */
@Composable
private fun HistoryRow(
    session: ChatHistorySession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val dim = if (pressed) MaterialTheme.colorScheme.background.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(when {
                    pressed -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                })
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${historyTime(session.updatedAt)} · ${session.messages.size} messages",
                    style = Mobie.numeric.tiny,
                    color = dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                session.modelId?.let { modelId ->
                    Text(
                        modelId.substringAfter('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
