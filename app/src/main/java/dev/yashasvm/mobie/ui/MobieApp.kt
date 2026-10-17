package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.AppContainer
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.ui.theme.Mobie
import kotlinx.coroutines.delay

internal const val REPOSITORY_URL = "https://github.com/YashasVM/Mobie"

@Composable
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
    AppBackdrop(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when {
                state.chatting -> "chat"
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
                "chat" -> ChatScreen(
                    state = state,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    onBack = viewModel::leaveChat,
                    onHistory = { showHistory = true },
                    onNewChat = viewModel::newChat,
                    onSamplerChange = viewModel::updateSampler,
                    onLoadAnyway = { viewModel.runInstalled(allowReclaim = true) },
                )
                "model" -> ModelScreen(
                    state = state,
                    onDownload = ::requestDownload,
                    onRun = viewModel::runInstalled,
                    onCancelDownload = viewModel::cancelDownload,
                    onBackendChange = viewModel::setComputeBackend,
                    onCpuThreadsChange = viewModel::setCpuThreads,
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
                )
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
private fun AppBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.background(background)) {
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
                        PromptLine("ls history")
                        Text(
                            "Finished conversations with this model will appear here.",
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
