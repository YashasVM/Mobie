package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import dev.yashasvm.mobie.R
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.core.model.DeviceProfile
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.isCancellable
import dev.yashasvm.mobie.data.history.ChatHistorySession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REPOSITORY_URL = "https://github.com/YashasVM/Mobie"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MobieApp(
    container: AppContainer,
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
) {
    val viewModel: MobieViewModel = viewModel(factory = MobieViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    if (!state.welcomeSeen) {
        WelcomeScreen(viewModel::finishWelcome)
        return
    }

    var warningOverride by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(enabled = state.selected != null) {
        if (state.chatting) viewModel.leaveChat() else viewModel.select(null)
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (state.selected != null) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = .92f),
                ),
                title = {
                    Column {
                        Text(
                            if (state.chatting) "Local chat" else "Model details",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            state.selected?.title.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.chatting) viewModel.leaveChat() else viewModel.select(null)
                    }) { LucideIcon(LucideR.drawable.lucide_ic_arrow_left, "Back", Modifier.size(21.dp)) }
                },
                actions = {
                    if (state.chatting) {
                        IconButton(onClick = { showHistory = true }) {
                            LucideIcon(LucideR.drawable.lucide_ic_history, "Chat history", Modifier.size(21.dp))
                        }
                        IconButton(onClick = viewModel::newChat) {
                            LucideIcon(LucideR.drawable.lucide_ic_plus, "Start a new chat", Modifier.size(21.dp))
                        }
                    }
                },
            )
        },
    ) { padding ->
        AppBackdrop(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = when {
                    state.chatting -> "chat"
                    state.selected != null -> "model"
                    else -> "catalog"
                },
                transitionSpec = {
                    (fadeIn(tween(240, easing = LinearOutSlowInEasing)) + scaleIn(tween(240), initialScale = .985f)) togetherWith
                        (fadeOut(tween(140, easing = FastOutLinearInEasing)) + scaleOut(tween(140), targetScale = .995f))
                },
                label = "screen",
            ) { screen ->
                when (screen) {
                    "chat" -> ChatScreen(
                        state = state,
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                    )
                    "model" -> ModelScreen(state, ::requestDownload, viewModel::runInstalled, viewModel::cancelDownload)
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
                    )
                }
            }
        }
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

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(450)
        onContinue()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClickLabel = "Skip welcome", onClick = onContinue)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Mobie logo",
                modifier = Modifier.size(100.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text("mobie", style = MaterialTheme.typography.displaySmall)
            Text(
                "intelligence, kept local",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text("Tap anywhere to continue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CatalogScreen(
    state: MobieUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
    onOpenInstalled: (dev.yashasvm.mobie.data.download.InstalledModelEntry) -> Unit = {},
    onDeleteInstalled: (dev.yashasvm.mobie.data.download.InstalledModelEntry) -> Unit = {},
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
    onSaveToken: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<dev.yashasvm.mobie.data.download.InstalledModelEntry?>(null) }
    var editingToken by rememberSaveable { mutableStateOf(false) }
    var tokenDraft by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val installedOnly = selectedTab == 1
    val settingsOnly = selectedTab == 2
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            FloatingNavigation(selectedTab) { tab ->
                selectedTab = tab
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!installedOnly && !settingsOnly) {
                item {
                    Text("Mobie", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        when {
                            installedOnly -> "Your local models"
                            settingsOnly -> "Settings"
                            else -> "Models for this phone"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        when {
                            installedOnly -> "Tap a model to start a fresh private chat."
                            settingsOnly -> "Control access and local app preferences."
                            else -> "Private intelligence that stays on this device."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (!settingsOnly) item {
                DeviceSummary(
                    ram = formatBytes(state.device?.totalRamBytes ?: 0),
                    storage = formatBytes(state.device?.availableStorageBytes ?: 0),
                )
            }
            if (!installedOnly && !settingsOnly) {
                item {
                    TextField(
                        value = state.query,
                        onValueChange = onQuery,
                        modifier = Modifier.fillMaxWidth().testTag("model_search"),
                        singleLine = true,
                        placeholder = { Text("Search the model catalog") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = if (state.query.isNotBlank()) {
                            {
                                IconButton(onClick = onSearch) {
                                    Icon(Icons.Filled.Search, "Search")
                                }
                            }
                        } else null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .58f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        shape = RoundedCornerShape(28.dp),
                    )
                }
            }
            if (!settingsOnly) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (installedOnly) "Installed" else if (state.query.isBlank()) "Recommended" else "Compatible results",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (state.query.isNotBlank() && !installedOnly) TextButton(onClick = onFeatured) { Text("Clear") }
                }
            }
            if (settingsOnly) {
                item {
                    TonalPanel {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
                                    Box(contentAlignment = Alignment.Center) { LucideIcon(LucideR.drawable.lucide_ic_sun_moon, null, Modifier.size(20.dp)) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                                    Text(if (darkTheme) "Dark mode" else "Light mode", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Hugging Face access", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (state.tokenConfigured) "A token is securely stored on this device."
                                else "Optional. Add one only when a gated model requires it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (editingToken) {
                                OutlinedTextField(
                                    value = tokenDraft,
                                    onValueChange = { tokenDraft = it },
                                    modifier = Modifier.fillMaxWidth().testTag("hf_token_input"),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Access token") },
                                    supportingText = { Text("Leave blank and save to remove it.") },
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { editingToken = false; tokenDraft = "" }) { Text("Cancel") }
                                    Button(onClick = { onSaveToken(tokenDraft); editingToken = false; tokenDraft = "" }) { Text("Save") }
                                }
                            } else {
                                OutlinedButton(onClick = { editingToken = true }) {
                                    LucideIcon(LucideR.drawable.lucide_ic_key_round, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (state.tokenConfigured) "Change token" else "Add token")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("About Mobie", style = MaterialTheme.typography.titleLarge)
                            SettingsRow(
                                icon = LucideR.drawable.lucide_ic_github,
                                title = "Repository",
                                value = "github.com/YashasVM/Mobie",
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) },
                            )
                            SettingsRow(
                                icon = LucideR.drawable.lucide_ic_file_text,
                                title = "Licensing",
                                value = "LiteRT-LM · Lucide Icons",
                            )
                            Text(
                                "Mobie runs inference on-device with LiteRT-LM. Model licenses are shown on each model's details page.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "made by @yashas.vm",
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            } else if (installedOnly) {
                state.error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (state.installedModels.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.CloudDownload,
                            title = "Nothing downloaded yet",
                            body = "Models you download will live here, ready for private chats.",
                            actionLabel = "Browse models",
                            onAction = { selectedTab = 0 },
                        )
                    }
                } else {
                    items(state.installedModels, key = { it.model.id }) { entry ->
                        ModelCard(
                            model = entry.model,
                            status = Compatibility.COMPATIBLE,
                            installed = true,
                            onSelect = { onOpenInstalled(entry) },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            } else {
                when {
                    state.loading -> item { LoadingState() }
                    state.error != null -> item { ErrorState(state.error, onFeatured) }
                    state.models.isEmpty() -> item {
                        EmptyState(Icons.Filled.ErrorOutline, "No matching models", "Try a different search or return to recommendations.")
                    }
                    else -> items(state.models, key = { it.id }) { model ->
                        val status = state.device?.let {
                            dev.yashasvm.mobie.core.device.CompatibilityResolver().resolve(model.bestArtifact, it).status
                        } ?: Compatibility.INCOMPATIBLE
                        ModelCard(model, status, state.installedModels.any { it.model.id == model.id }, onSelect)
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Delete ${entry.model.title}?") },
            text = { Text("This removes the downloaded model from this phone. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteInstalled(entry) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FloatingNavigation(selectedTab: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Discover", LucideR.drawable.lucide_ic_search, "bottom_nav_discover"),
        Triple("Installed", LucideR.drawable.lucide_ic_package, "bottom_nav_installed"),
        Triple("Settings", LucideR.drawable.lucide_ic_settings, "bottom_nav_settings"),
    )
    TonalPanel(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp)) {
            items.forEachIndexed { index, (label, icon, tag) ->
                val selected = selectedTab == index
                val selectionColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(220),
                    label = "$label selection",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .testTag(tag)
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        color = selectionColor,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(34.dp).animateContentSize(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (selected) 12.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (selected) 6.dp else 0.dp),
                        ) {
                            LucideIcon(icon, null, Modifier.size(19.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140)),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!selected) {
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSummary(ram: String, storage: String) {
    TonalPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("This phone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$ram total RAM", style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Free storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(storage, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AiModel,
    status: Compatibility,
    installed: Boolean,
    onSelect: (AiModel) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val statusColor = when (status) {
        Compatibility.COMPATIBLE -> Color(0xFF55D68B)
        Compatibility.WARNING -> Color(0xFFFFC857)
        else -> MaterialTheme.colorScheme.error
    }
    TonalPanel(
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onSelect(model) }),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (onDelete == null) {
                    StatusBadge(statusLabel(status), statusColor)
                } else {
                    Badge("Installed")
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete ${model.title}") }
                }
            }
            Text(model.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${model.author} · ${model.type.displayLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                model.description.ifBlank { "A mobile-ready local model." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.bestArtifact?.let { Badge(formatBytes(it.sizeBytes)) }
                if (installed) Badge("Installed")
                if (model.supportsVision) Badge("Vision")
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(100)) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

private fun statusLabel(status: Compatibility) = when (status) {
    Compatibility.COMPATIBLE -> "Ready"
    Compatibility.WARNING -> "Free memory"
    Compatibility.INCOMPATIBLE -> "Not compatible"
    Compatibility.CONVERSION_REQUIRED -> "Conversion"
}

@Composable
internal fun ModelScreen(
    state: MobieUiState,
    onDownload: (Boolean) -> Unit,
    onRun: () -> Unit,
    onCancelDownload: () -> Unit = {},
) {
    val model = state.selected ?: return
    val artifact = model.bestArtifact ?: return
    var confirmWarning by remember { mutableStateOf(false) }
    val downloading = state.download?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
    val gatedWithoutToken = model.gated && !state.tokenConfigured
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TonalPanel(shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.downloadedPath != null) {
                        if (!downloading) Button(
                            onClick = onRun,
                            shape = RoundedCornerShape(100),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Run locally")
                        }
                    } else {
                        if (gatedWithoutToken) {
                            Text("Hugging Face access required", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Add a token in Settings to download this gated model.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = {
                                if (state.compatibility?.status == Compatibility.WARNING) confirmWarning = true else onDownload(false)
                            },
                            enabled = !downloading && !gatedWithoutToken &&
                                state.compatibility?.status in setOf(Compatibility.COMPATIBLE, Compatibility.WARNING) &&
                                (!model.gated || state.tokenConfigured),
                            shape = RoundedCornerShape(100),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("model_primary_action"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, null)
                            Spacer(Modifier.size(8.dp))
                            Text(when {
                                downloading -> "Downloading…"
                                gatedWithoutToken -> "Download requires token"
                                else -> "Download model"
                            })
                        }
                    }
                    state.download?.let { download ->
                        val progress = if (download.totalBytes > 0) {
                            (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
                        } else 0f
                        val animatedProgress by animateFloatAsState(
                            progress,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                            label = "download progress",
                        )
                        if (!download.state.isFinished) {
                            if (download.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            download.error ?: downloadProgressLabel(download),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (download.isCancellable) {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cancel_download"),
                                shape = RoundedCornerShape(100),
                            ) {
                                LucideIcon(LucideR.drawable.lucide_ic_x, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Cancel download")
                            }
                        }
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 180.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Ready check", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(100)) {
                            Text(artifact.runtimeLabel, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(model.title, style = MaterialTheme.typography.headlineMedium)
                    Text("${model.author} · ${model.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(model.description.ifBlank { "Ready-to-run Hugging Face LiteRT-LM model." }, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Badge("Type: ${model.type.displayLabel()}")
                        Badge(if (model.gated) "Gated" else "Public")
                        Badge(if (model.supportsVision) "Vision" else "Text")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Compatibility", "Can this model run comfortably here?")
                    TonalPanel {
                        CompatibilityCard(state.compatibility, state.device)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Model package", "The exact file Mobie will download")
                    TonalPanel {
                        ArtifactCard(artifact, model)
                    }
                }
            }
        }
    }
    if (confirmWarning) {
        AlertDialog(
            onDismissRequest = { confirmWarning = false },
            title = { Text("Free memory before running") },
            text = { Text(state.compatibility?.reason.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { confirmWarning = false; onDownload(true) }) { Text("Download anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmWarning = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ChatScreen(
    state: MobieUiState,
    onSend: (String, String?) -> Unit,
    onStop: () -> Unit = {},
) {
    val model = state.selected ?: return
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imagePath = uri?.let { copyImageToCache(context, it) }
    }
    val streamedLengthBucket = state.messages.lastOrNull()?.let { (it.text.length + it.thinking.length) / 120 } ?: 0
    LaunchedEffect(state.messages.size, streamedLengthBucket) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    val ready = state.runtimeState == RuntimeState.READY
    val generating = state.runtimeState == RuntimeState.GENERATING
    val submit = {
        if (ready && prompt.isNotBlank()) {
            onSend(prompt, imagePath)
            prompt = ""
            imagePath = null
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("chat_screen"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TonalPanel(shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.stats?.let {
                        Text(
                            String.format(Locale.US, "%.1f tokens/s · %s RAM", it.tokensPerSecond, formatBytes(it.ramBytes)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (generating) Text("Generating on this device…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    imagePath?.let {
                        FilterChip(
                            selected = true,
                            onClick = { imagePath = null },
                            label = { Text("Image attached") },
                            leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_check, null, Modifier.size(16.dp)) },
                            trailingIcon = { LucideIcon(LucideR.drawable.lucide_ic_x, "Remove image", Modifier.size(16.dp)) },
                        )
                    }
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                         enabled = ready,
                        placeholder = { Text("Ask privately…") },
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(28.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .58f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        leadingIcon = if (model.supportsVision) {{
                            IconButton(onClick = { imagePicker.launch("image/*") }, enabled = ready) {
                                LucideIcon(LucideR.drawable.lucide_ic_image, "Attach image", Modifier.size(20.dp))
                            }
                        }} else null,
                        trailingIcon = {
                            FilledIconButton(
                                onClick = { if (generating) onStop() else submit() },
                                enabled = generating || (ready && prompt.isNotBlank()),
                                modifier = Modifier.size(48.dp),
                            ) {
                                LucideIcon(
                                    if (generating) LucideR.drawable.lucide_ic_square else LucideR.drawable.lucide_ic_send,
                                    if (generating) "Stop generation" else "Send message",
                                    Modifier.size(19.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                    )
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        },
    ) { padding ->
        when (state.runtimeState) {
            RuntimeState.LOADING -> LoadingChat(model.title, padding)
            RuntimeState.ERROR -> Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ErrorOutline, "Chat could not start", state.error ?: "The model could not run.")
            }
            else -> if (state.messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LucideIcon(LucideR.drawable.lucide_ic_sparkles, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Start a conversation", style = MaterialTheme.typography.titleLarge)
                        Text("Nothing you type here leaves this phone.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    itemsIndexed(state.messages, key = { index, _ -> index }) { _, message ->
                        MessageBubble(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingChat(modelTitle: String, padding: PaddingValues = PaddingValues()) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("Preparing private chat", style = MaterialTheme.typography.titleMedium)
            Text("Loading $modelTitle into memory…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.fillMaxWidth(if (user) .84f else .92f),
            horizontalAlignment = if (user) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!user && message.thinking.isNotBlank()) {
                ThinkingPanel(message.thinking)
            }
            if (message.text.isNotBlank()) {
                if (user) Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(18.dp)) {
                    Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(message.text, Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ThinkingPanel(thinking: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .clickable { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                    Text("Thinking", style = MaterialTheme.typography.titleSmall)
                }
                Text(if (expanded) "Hide" else "Inspect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Text(
                    thinking,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

@Composable
private fun TonalPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = .06f),
                spotColor = Color.Black.copy(alpha = .18f),
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .78f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f), shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(sessions: List<ChatHistorySession>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Chat history", style = MaterialTheme.typography.headlineMedium)
                    Text("Saved locally on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
            }
            val visible = sessions.filter { it.messages.isNotEmpty() }
            if (visible.isEmpty()) {
                Text("Your completed conversations will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                visible.forEach { session ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { onSelect(session.id) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${session.messages.size} messages  /  ${historyTime(session.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompatibilityCard(result: CompatibilityResult?, device: DeviceProfile?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val status = result?.status
                Icon(
                    when (status) {
                        Compatibility.COMPATIBLE -> Icons.Filled.CheckCircle
                        Compatibility.WARNING -> Icons.Filled.WarningAmber
                        else -> Icons.Filled.ErrorOutline
                    },
                    null,
                    tint = when (status) {
                        Compatibility.COMPATIBLE -> Color(0xFF55D68B)
                        Compatibility.WARNING -> Color(0xFFFFC857)
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    when (status) {
                        Compatibility.COMPATIBLE -> "Ready for this phone"
                        Compatibility.WARNING -> "Needs memory first"
                        Compatibility.INCOMPATIBLE -> "Cannot run on this phone"
                        else -> "Not directly runnable"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(result?.reason.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            InfoRow("Model weights", formatBytes(result?.modelWeightsBytes ?: 0))
            InfoRow("Runtime overhead", formatBytes(result?.runtimeOverheadBytes ?: 0))
            InfoRow("KV cache", formatBytes(result?.kvCacheBytes ?: 0))
            InfoRow("Prompt context", "${result?.contextWindowTokens ?: 0} tokens")
            InfoRow("Estimated RAM", formatBytes(result?.estimatedRamBytes ?: 0))
            InfoRow("Total RAM", formatBytes(device?.totalRamBytes ?: 0))
            InfoRow("Available RAM", formatBytes(device?.availableRamBytes ?: 0))
            InfoRow("Storage needed", formatBytes(result?.requiredStorageBytes ?: 0))
            InfoRow("Free storage", formatBytes(device?.availableStorageBytes ?: 0))
            InfoRow("Device", deviceLabel(device))
    }
}

@Composable
private fun ArtifactCard(artifact: ModelArtifact, model: AiModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("Runtime", "LiteRT-LM")
            InfoRow("Artifact", artifact.fileName.substringAfterLast('/'))
            InfoRow("Download", formatBytes(artifact.sizeBytes))
            InfoRow("Quantization", artifact.quantization ?: "Publisher default")
            InfoRow("Checksum", if (artifact.sha256.isNullOrBlank()) "Size validation only" else "SHA-256 available")
            InfoRow("Vision", if (model.supportsVision) "Supported" else "Text only")
            InfoRow("License", model.license ?: "Check model card")
            InfoRow("Access", if (model.gated) "Hugging Face approval required" else "Public")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.46f))
        Text(
            value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.54f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(100), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, shape = RoundedCornerShape(100)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(onClick = retry, shape = RoundedCornerShape(100)) { Text("Retry") }
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LucideIcon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (onClick != null) LucideIcon(LucideR.drawable.lucide_ic_external_link, "Open $title", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LucideIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(painterResource(icon), contentDescription, modifier, tint)
}

private fun copyImageToCache(context: Context, uri: Uri): String? = runCatching {
    val file = File(context.cacheDir, "chat-image-${System.currentTimeMillis()}.bin")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) } ?: return null
    file.absolutePath
}.getOrNull()

private fun formatBytes(value: Long): String {
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

private fun downloadProgressLabel(download: dev.yashasvm.mobie.data.download.DownloadProgress): String {
    if (download.state == WorkInfo.State.CANCELLED) return "Download cancelled"
    if (download.totalBytes <= 0) return "Starting download…"
    val percent = (download.downloadedBytes.toDouble() / download.totalBytes * 100).toInt().coerceIn(0, 100)
    val speed = if (download.bytesPerSecond > 0) " · ${formatBytes(download.bytesPerSecond)}/s" else ""
    return "$percent% · ${formatBytes(download.downloadedBytes)} of ${formatBytes(download.totalBytes)}$speed"
}

private fun deviceLabel(device: DeviceProfile?): String {
    if (device == null) return "Unknown"
    val release = device.releaseVersion.ifBlank { "API ${device.sdkInt}" }
    val api = if (device.releaseVersion.isBlank()) "" else " (API ${device.sdkInt})"
    return "Android $release$api · ${device.supportedAbis.firstOrNull() ?: "Unknown ABI"}"
}

private fun historyTime(timestamp: Long): String =
    if (timestamp <= 0) "saved chat" else SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun ModelType.displayLabel(): String = when (this) {
    ModelType.TEXT_GENERATION -> "Text generation"
    ModelType.VISION -> "Vision chat"
    ModelType.EMBEDDING -> "Embedding"
    ModelType.AUDIO -> "Audio"
    ModelType.UNKNOWN -> "Unknown"
}
