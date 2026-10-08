package dev.yashasvm.mobie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.R
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
                (fadeIn(tween(220, easing = LinearOutSlowInEasing)) + scaleIn(tween(220), initialScale = .99f)) togetherWith
                    fadeOut(tween(120, easing = FastOutLinearInEasing))
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
                )
                "model" -> ModelScreen(
                    state = state,
                    onDownload = ::requestDownload,
                    onRun = viewModel::runInstalled,
                    onCancelDownload = viewModel::cancelDownload,
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
private fun WelcomeScreen(onContinue: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(450)
        onContinue()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClickLabel = "Skip welcome", onClick = onContinue),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Mobie logo",
                modifier = Modifier.size(84.dp),
            )
            Text("mobie", style = MaterialTheme.typography.displaySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(Mobie.signals.ready, pulsing = true)
                Text(
                    "on-device inference",
                    style = Mobie.mono.small,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(sessions: List<ChatHistorySession>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val visible = remember(sessions) { sessions.filter { it.messages.isNotEmpty() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = .6f),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Chat history", style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LucideIcon(LucideR.drawable.lucide_ic_lock, null, Modifier.size(12.dp), tint = Mobie.signals.ready)
                        Text(
                            "Stored only on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(
                            "Finished conversations with this model will appear here.",
                            Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(visible, key = { it.id }) { session ->
                    Panel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = { onSelect(session.id) }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LucideIcon(
                                LucideR.drawable.lucide_ic_message_square,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${session.messages.size} msg · ${historyTime(session.updatedAt)}",
                                    style = Mobie.mono.tiny,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LucideIcon(
                                LucideR.drawable.lucide_ic_chevron_right,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
