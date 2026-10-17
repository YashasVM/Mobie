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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import dev.yashasvm.mobie.core.AppContainer
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
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
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

// The website's hero wordmark, character for character. It is 39 columns wide.
private val WordmarkLines = listOf(
    "███╗   ███╗ ██████╗ ██████╗ ██╗███████╗",
    "████╗ ████║██╔═══██╗██╔══██╗██║██╔════╝",
    "██╔████╔██║██║   ██║██████╔╝██║█████╗  ",
    "██║╚██╔╝██║██║   ██║██╔══██╗██║██╔══╝  ",
    "██║ ╚═╝ ██║╚██████╔╝██████╔╝██║███████╗",
    "╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚══════╝",
)
private const val WordmarkColumns = 39

/** Advance width of a monospace cell in em, with a little slack so box glyphs never clip. */
private const val MonoCellEm = .62f

/** One boot log line: a status tag in green followed by the message. Only true statements. */
private data class BootLine(val tag: String, val message: String) {
    val length: Int get() = tag.length + message.length
}

private val BootLines = listOf(
    BootLine("[  ok  ]", " probing device"),
    BootLine("[  ok  ]", " local runtime: LiteRT-LM"),
    BootLine("[  ok  ]", " offline chat after download"),
)

private const val BootCharDelayMillis = 16L
private const val BootLineDelayMillis = 120L

private fun prefersReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) { prefersReducedMotion(context) }
    // Characters revealed so far on each boot line. The list is written only from the effect below.
    val revealed = remember { mutableStateListOf<Int>().apply { BootLines.forEach { add(0) } } }
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        for ((index, line) in BootLines.withIndex()) {
            if (!reduceMotion) {
                // Driven by elapsed time, not per-character delays, so dropped frames skip ahead
                // instead of stretching the whole boot log.
                val start = withFrameMillis { it }
                while (revealed[index] < line.length) {
                    val elapsed = withFrameMillis { it } - start
                    revealed[index] = (elapsed / BootCharDelayMillis).toInt().coerceIn(1, line.length)
                }
                delay(BootLineDelayMillis)
            }
            revealed[index] = line.length
        }
        booted = true
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Tapping anywhere still skips the welcome, as it always has.
            .clickable(role = Role.Button, onClickLabel = "Skip welcome", onClick = onContinue),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            StatusLine(listOf(StatusSegment("mobie")), trailing = "first run")
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Wordmark(Modifier.appear(offsetDp = 6f))
                BootLog(revealed)
                AnimatedVisibility(
                    visible = booted,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(MotionShort, easing = LinearEasing)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Lead()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CheckLine("Your prompts and replies stay on this phone.", "✓", Mobie.signals.ready)
                            CheckLine("Public models need no Hugging Face account.", "✓", Mobie.signals.ready)
                        }
                    }
                }
            }
            // Pinned at the bottom so the one action on this screen sits under the thumb.
            AnimatedVisibility(
                visible = booted,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(MotionShort, easing = LinearEasing)),
            ) {
                TermButton("continue", onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
        }
    }
}

/**
 * The MOBIE wordmark sized so its 39 columns fill the available width at any font scale. The
 * clamp keeps it at or above the 12sp floor used everywhere else.
 */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val size = (maxWidth.value / (WordmarkColumns * MonoCellEm) / fontScale).coerceIn(12f, 20f).sp
        Text(
            WordmarkLines.joinToString("\n"),
            style = Mobie.numeric.medium.copy(
                fontSize = size,
                lineHeight = size,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.primary,
            softWrap = false,
            maxLines = WordmarkLines.size,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun BootLog(revealed: List<Int>) {
    val ok = Mobie.signals.ready
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BootLines.forEachIndexed { index, line ->
            val full = buildAnnotatedString {
                withStyle(SpanStyle(color = ok)) { append(line.tag) }
                append(line.message)
            }
            // Always rendered, even while empty, so the layout does not jump as lines type in.
            Text(
                full.subSequence(0, revealed[index]),
                style = Mobie.numeric.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The one-line pitch, with the amber phrase and a blinking cursor at the end of the text. */
@Composable
private fun Lead() {
    val cursorId = "cursor"
    val amber = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append("Run AI models ")
        withStyle(SpanStyle(color = amber, fontWeight = FontWeight.SemiBold)) { append("on your phone") }
        append(", offline.")
        appendInlineContent(cursorId, " ")
    }
    Text(
        text,
        style = Mobie.numeric.large.copy(fontWeight = FontWeight.Medium, lineHeight = 30.sp),
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = mapOf(
            cursorId to InlineTextContent(
                Placeholder(width = .6.em, height = 1.1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
            ) {
                BlinkingCursor(color = amber, width = 12.dp, height = 22.dp)
            },
        ),
    )
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
