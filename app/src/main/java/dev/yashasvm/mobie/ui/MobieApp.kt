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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashasvm.mobie.core.AppContainer
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
    if (!state.preferencesLoaded) {
        AppBackdrop(Modifier.fillMaxSize()) {}
        return
    }
    if (!state.welcomeSeen) {
        WelcomeScreen(viewModel::finishWelcome)
        return
    }

    var warningOverride by remember { mutableStateOf(false) }
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

    // Models and model pages sit on top of the chat home; back returns toward it. At the home,
    // back is left to the system so it exits the app (the drawer handles its own back first).
    BackHandler(enabled = state.screen != AppScreen.CHAT) { viewModel.back() }
    AppBackdrop(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                // Shared-axis motion kept short and flat: deeper screens (chat → models → model)
                // slide in from the right, going back reverses it.
                val forward = targetState.ordinal > initialState.ordinal
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
                AppScreen.CHAT -> ChatHome(
                    state = state,
                    onSend = { prompt, imagePath ->
                        viewModel.sendMessage(prompt, imagePath, state.pendingAttachments).also { sent ->
                            if (sent) viewModel.clearPendingAttachments()
                        }
                    },
                    onStop = viewModel::stopGeneration,
                    onAttachDocument = viewModel::attachDocument,
                    onRemoveAttachment = viewModel::removePendingAttachment,
                    onDismissAttachmentError = viewModel::dismissAttachmentError,
                    onNewChat = viewModel::newChat,
                    onOpenThread = viewModel::openThread,
                    onRenameChat = viewModel::renameChat,
                    onDeleteChat = viewModel::deleteChat,
                    onPickModel = viewModel::startInstalledChat,
                    onOpenModels = viewModel::openModels,
                    onGetModel = viewModel::findModel,
                    onSamplerChange = viewModel::updateSampler,
                    onLoadAnyway = { viewModel.runInstalled(allowReclaim = true) },
                )
                AppScreen.MODEL -> ModelScreen(
                    state = state,
                    onDownload = ::requestDownload,
                    onRun = viewModel::runInstalled,
                    onCancelDownload = viewModel::cancelDownload,
                    onBackendChange = viewModel::setComputeBackend,
                    onCpuThreadsChange = viewModel::setCpuThreads,
                    onBack = { viewModel.back() },
                )
                AppScreen.CATALOG -> CatalogScreen(
                    state,
                    viewModel::setQuery,
                    viewModel::search,
                    viewModel::openModelPage,
                    viewModel::loadFeatured,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onSaveToken = viewModel::saveToken,
                    onOpenInstalled = viewModel::startInstalledChat,
                    onDeleteInstalled = viewModel::deleteInstalled,
                    initialTab = state.catalogTab,
                    onBack = { viewModel.back() },
                )
            }
        }
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
