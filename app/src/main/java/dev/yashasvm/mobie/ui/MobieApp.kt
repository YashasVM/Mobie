package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.AppContainer
import kotlinx.coroutines.launch

internal const val REPOSITORY_URL = "https://github.com/YashasVM/Mobie"
internal enum class AppPage(val title: String, val icon: Int) {
    Chat("Chat", LucideR.drawable.lucide_ic_message_circle),
    Recents("Recents", LucideR.drawable.lucide_ic_history),
    Models("Models", LucideR.drawable.lucide_ic_box),
    Settings("Settings", LucideR.drawable.lucide_ic_settings),
}

@Composable
fun MobieApp(
    container: AppContainer,
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(AppPage.Chat) }
    var showModelSwitcher by rememberSaveable { mutableStateOf(false) }
    var showModelDetail by rememberSaveable { mutableStateOf(false) }
    var detailModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var warningOverride by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()
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
    fun navigate(destination: AppPage) {
        showModelDetail = false
        page = destination
        if (destination == AppPage.Recents) viewModel.refreshRecents()
        if (destination == AppPage.Chat && !state.chatting) viewModel.returnToChat()
        scope.launch { drawer.close() }
    }
    BackHandler(enabled = showModelDetail || page != AppPage.Chat) {
        if (showModelDetail) showModelDetail = false else navigate(AppPage.Chat)
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                Text("Mobie", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(28.dp))
                AppPage.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.title) },
                        icon = { LucideIcon(destination.icon, null, Modifier.size(22.dp)) },
                        selected = page == destination,
                        onClick = { navigate(destination) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("nav_${destination.name.lowercase()}"),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Your conversations stay on this phone.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(28.dp))
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                if (page != AppPage.Chat && !showModelDetail) PageHeader(page.title) { scope.launch { drawer.open() } }
                Box(Modifier.weight(1f)) {
                    when {
                        showModelDetail && state.selected?.id != detailModelId -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        showModelDetail && state.selected != null -> ModelScreen(
                            state = state,
                            onDownload = ::requestDownload,
                            onRun = { allowReclaim ->
                                page = AppPage.Chat
                                showModelDetail = false
                                viewModel.runInstalled(allowReclaim)
                            },
                            onCancelDownload = viewModel::cancelDownload,
                            onBackendChange = viewModel::setComputeBackend,
                            onCpuThreadsChange = viewModel::setCpuThreads,
                            onBack = { showModelDetail = false },
                        )
                        page == AppPage.Chat -> screenState.SaveableStateProvider("chat") { ChatScreen(
                            state = if (state.chatting) state else state.copy(selected = null, messages = emptyList(), error = null),
                            onSend = { prompt, imagePath ->
                                viewModel.sendMessage(prompt, imagePath, state.pendingAttachments).also {
                                    if (it) viewModel.clearPendingAttachments()
                                }
                            },
                            onLoad = { viewModel.runInstalled() },
                            onStop = viewModel::stopGeneration,
                            onBack = { scope.launch { drawer.open() } },
                            onNewChat = viewModel::newChat,
                            onSamplerChange = viewModel::updateSampler,
                            onLoadAnyway = { viewModel.runInstalled(allowReclaim = true) },
                            onSwitchModel = { showModelSwitcher = true },
                            onAttachDocument = viewModel::attachDocument,
                            onRemoveAttachment = viewModel::removePendingAttachment,
                            onDismissAttachmentError = viewModel::dismissAttachmentError,
                        ) }
                        page == AppPage.Models -> screenState.SaveableStateProvider("models") { CatalogScreen(
                            state, viewModel::setQuery, viewModel::search,
                            onSelect = { detailModelId = it.id; viewModel.select(it); showModelDetail = true },
                            onFeatured = viewModel::loadFeatured,
                            onOpenInstalled = { viewModel.startInstalledChat(it); page = AppPage.Chat },
                            onDeleteInstalled = viewModel::deleteInstalled,
                        ) }
                        page == AppPage.Settings -> SettingsScreen(state, darkTheme, onDarkThemeChange, viewModel::saveToken)
                        else -> RecentsScreen(state.recentChats) { viewModel.openRecent(it); page = AppPage.Chat }
                    }
                }
            }
        }
    }
    if (showModelSwitcher) ModelSwitcherSheet(
        installed = state.installedModels,
        activeId = state.selected?.id,
        onPick = { showModelSwitcher = false; viewModel.switchModel(it) },
        onGetMore = { showModelSwitcher = false; navigate(AppPage.Models) },
        onDismiss = { showModelSwitcher = false },
    )
}

@Composable
internal fun PageHeader(title: String, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { LucideIcon(LucideR.drawable.lucide_ic_menu, "Open navigation", Modifier.size(24.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
    }
}

@Composable
internal fun RecentsScreen(chats: List<RecentChat>, onOpen: (RecentChat) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Conversations, kept close.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (chats.isEmpty()) item {
            Column(Modifier.padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No chats yet", style = MaterialTheme.typography.headlineMedium)
                Text("Start a conversation in Chat. You can come back to it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(chats, key = { "${it.modelId}:${it.session.id}" }) { chat ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().clickable { onOpen(chat) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(chat.session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${chat.modelTitle} · ${historyTime(chat.session.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
