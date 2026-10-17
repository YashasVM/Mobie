package dev.yashasvm.mobie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.runtime.SamplerSettings
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.data.history.ChatThread
import dev.yashasvm.mobie.ui.theme.Mobie
import kotlinx.coroutines.launch

/**
 * The app's home: the current chat with a chats drawer on the left and a model picker in the top
 * bar, the way familiar chat apps are laid out. With no model installed it shows how to get one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHome(
    state: MobieUiState,
    onSend: (String, String?) -> Boolean,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onOpenThread: (ChatThread) -> Unit,
    onRenameChat: (ChatThread, String) -> Unit,
    onDeleteChat: (ChatThread) -> Unit,
    onPickModel: (InstalledModelEntry) -> Unit,
    onOpenModels: (CatalogTab) -> Unit,
    onGetModel: (String) -> Unit,
    onSamplerChange: (SamplerSettings) -> String? = { null },
    onLoadAnyway: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatThread?>(null) }
    var deleting by remember { mutableStateOf<ChatThread?>(null) }
    fun closeDrawerThen(action: () -> Unit) {
        action()
        scope.launch { drawerState.close() }
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    // Back closes the drawer first; with it closed, back leaves the app from the chat home.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = .6f),
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                drawerShape = RectangleShape,
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
                modifier = Modifier.widthIn(max = 340.dp).testTag("chats_drawer"),
            ) {
                ChatsDrawer(
                    state = state,
                    onNewChat = { closeDrawerThen(onNewChat) },
                    onOpenThread = { thread -> closeDrawerThen { onOpenThread(thread) } },
                    onRename = { renaming = it },
                    onDelete = { deleting = it },
                    onOpenModels = { tab -> closeDrawerThen { onOpenModels(tab) } },
                )
            }
        },
    ) {
        val showChat = state.readOnlyChat != null || (state.chatting && state.selected != null)
        if (showChat) {
            key(state.readOnlyChat?.modelId ?: state.selected?.id, state.sessionId) {
                ChatScreen(
                    state = state,
                    onSend = onSend,
                    onStop = onStop,
                    onOpenDrawer = openDrawer,
                    onOpenModelPicker = { showPicker = true },
                    onNewChat = onNewChat,
                    onSamplerChange = onSamplerChange,
                    onLoadAnyway = onLoadAnyway,
                    onGetModel = onGetModel,
                )
            }
        } else {
            EmptyHome(
                pending = state.chatPending,
                hasInstalledModels = state.installedModels.isNotEmpty(),
                onOpenDrawer = openDrawer,
                onOpenModelPicker = { showPicker = true },
                onBrowseModels = { onOpenModels(CatalogTab.DISCOVER) },
            )
        }
    }

    if (showPicker) {
        ModelPickerSheet(
            installed = state.installedModels,
            activeModelId = state.selected?.id?.takeIf { state.chatting && state.readOnlyChat == null },
            onDismiss = { showPicker = false },
            onPick = { entry ->
                showPicker = false
                onPickModel(entry)
            },
            onFindMore = {
                showPicker = false
                onOpenModels(CatalogTab.DISCOVER)
            },
        )
    }
    renaming?.let { thread ->
        RenameChatDialog(
            thread = thread,
            onDismiss = { renaming = null },
            onRename = { title ->
                renaming = null
                onRenameChat(thread, title)
            },
        )
    }
    deleting?.let { thread ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { LucideIcon(LucideR.drawable.lucide_ic_trash_2, null, Modifier.size(22.dp)) },
            title = { Text("Delete this chat?") },
            text = {
                Text(
                    "“${thread.session.title}” will be removed from this phone. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDeleteChat(thread) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Chats drawer

@Composable
private fun ChatsDrawer(
    state: MobieUiState,
    onNewChat: () -> Unit,
    onOpenThread: (ChatThread) -> Unit,
    onRename: (ChatThread) -> Unit,
    onDelete: (ChatThread) -> Unit,
    onOpenModels: (CatalogTab) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val installedTitles = remember(state.installedModels) {
        state.installedModels.associate { it.model.id to it.model.title }
    }
    val now = remember(state.chats) { System.currentTimeMillis() }
    val groups = remember(state.chats, query, now) { groupChatsByAge(filterChats(state.chats, query), now) }
    val activeModelId = state.readOnlyChat?.modelId ?: state.selected?.id
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MobieMark()
        }
        DrawerAction(
            text = "New chat",
            icon = LucideR.drawable.lucide_ic_square_pen,
            onClick = onNewChat,
            emphasized = true,
            modifier = Modifier.testTag("drawer_new_chat"),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("chat_search"),
            placeholder = { Text("Search chats") },
            leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(18.dp)) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        LucideIcon(LucideR.drawable.lucide_ic_x, "Clear search", Modifier.size(18.dp))
                    }
                }
            } else null,
            singleLine = true,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (query.isBlank()) "Your chats will show up here. They're saved only on this phone."
                        else "No chats match “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            groups.forEach { group ->
                item(key = "header:${group.age}") {
                    Text(
                        group.age.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(group.chats, key = { "${it.modelId}\u0000${it.session.id}" }) { thread ->
                    ChatRow(
                        thread = thread,
                        modelName = shortModelName(thread.modelId, installedTitles[thread.modelId] ?: thread.session.modelTitle),
                        active = thread.session.id == state.sessionId && thread.modelId == activeModelId,
                        onClick = { onOpenThread(thread) },
                        onRename = { onRename(thread) },
                        onDelete = { onDelete(thread) },
                    )
                }
            }
        }
        Hairline()
        DrawerAction(
            text = "Models",
            icon = LucideR.drawable.lucide_ic_box,
            onClick = { onOpenModels(CatalogTab.LIBRARY) },
            modifier = Modifier.testTag("drawer_models"),
        )
        DrawerAction(
            text = "Settings",
            icon = LucideR.drawable.lucide_ic_settings,
            onClick = { onOpenModels(CatalogTab.SETTINGS) },
            modifier = Modifier.testTag("drawer_settings"),
        )
    }
}

@Composable
private fun DrawerAction(
    text: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LucideIcon(
            icon,
            null,
            Modifier.size(20.dp),
            tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One saved chat: title over the model's name. Long-press or the trailing menu offers rename and delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    thread: ChatThread,
    modelName: String,
    active: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open chat",
                onLongClickLabel = "Rename or delete",
                onLongClick = { menuOpen = true },
                onClick = onClick,
            )
            .heightIn(min = 56.dp)
            .padding(start = 12.dp)
            .semantics { selected = active }
            .testTag("chat_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                thread.session.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                LucideIcon(
                    LucideR.drawable.lucide_ic_ellipsis_vertical,
                    "More options for ${thread.session.title}",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_pencil, null, Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        LucideIcon(LucideR.drawable.lucide_ic_trash_2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun RenameChatDialog(thread: ChatThread, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var title by rememberSaveable(thread.session.id) { mutableStateOf(thread.session.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                singleLine = true,
                shape = RectangleShape,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().testTag("rename_chat_input"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(title) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Model picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    installed: List<InstalledModelEntry>,
    activeModelId: String?,
    onDismiss: () -> Unit,
    onPick: (InstalledModelEntry) -> Unit,
    onFindMore: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = .6f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("model_picker"),
    ) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Models on this phone",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Picking a model starts a new chat with it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (installed.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No models downloaded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            items(installed, key = { it.model.id }) { entry ->
                val active = entry.model.id == activeModelId
                val size = entry.model.bestArtifact?.sizeBytes?.takeIf { it > 0 }?.let(::formatBytes)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .clickable(role = Role.Button, onClick = { onPick(entry) })
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .semantics { selected = active },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            entry.model.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(size, if (active) "In use" else null).joinToString(" · ").ifEmpty { "On this phone" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active) {
                        LucideIcon(
                            LucideR.drawable.lucide_ic_check,
                            "Current model",
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item(key = "more") {
                Column {
                    Hairline(Modifier.padding(top = 8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = onFindMore)
                            .padding(horizontal = 20.dp)
                            .testTag("find_more_models"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Find more models", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home without an open chat

@Composable
private fun EmptyHome(
    pending: Boolean,
    hasInstalledModels: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onBrowseModels: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("chat_home_empty")) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("open_chats")) {
                    LucideIcon(LucideR.drawable.lucide_ic_menu, "Open chats", Modifier.size(22.dp))
                }
                MobieMark(Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.weight(1f))
            }
            Hairline()
        }
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            when {
                pending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(MaterialTheme.colorScheme.primary, pulsing = true)
                    Text(
                        "Getting your chat ready…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                hasInstalledModels -> EmptyHomeMessage(
                    title = "Choose a model to start chatting",
                    body = "Your downloaded models run right on this phone.",
                    action = "Choose a model",
                    icon = LucideR.drawable.lucide_ic_box,
                    onAction = onOpenModelPicker,
                )
                else -> EmptyHomeMessage(
                    title = "Download a model to start chatting",
                    body = "Mobie runs AI models right on this phone. Pick one that fits, download it once, and chat offline. Nothing you type leaves your phone.",
                    action = "Browse models",
                    icon = LucideR.drawable.lucide_ic_download,
                    onAction = onBrowseModels,
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeMessage(title: String, body: String, action: String, icon: Int, onAction: () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TermButton(action, onClick = onAction, icon = icon, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LucideIcon(LucideR.drawable.lucide_ic_lock, null, Modifier.size(12.dp), tint = Mobie.signals.ready)
            Text("Private and offline", style = Mobie.numeric.tiny, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
