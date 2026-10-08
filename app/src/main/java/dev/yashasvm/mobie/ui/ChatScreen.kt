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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ChatScreen(
    state: MobieUiState,
    onSend: (String, String?) -> Unit,
    onStop: () -> Unit = {},
    onBack: () -> Unit = {},
    onHistory: () -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val model = state.selected ?: return
    val context = LocalContext.current
    var prompt by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var imageCopying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            imageError = null
            imageCopying = true
            try {
                val copiedPath = copyImageToCache(context, uri)
                if (copiedPath == null) {
                    imageError = "Couldn't attach that image. Try another one."
                } else {
                    imagePath?.let { File(it).delete() }
                    imagePath = copiedPath
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                imageError = "Couldn't attach that image. Try another one."
            } finally {
                imageCopying = false
            }
        }
    }
    val streamedLengthBucket = state.messages.lastOrNull()?.let { (it.text.length + it.thinking.length) / 120 } ?: 0
    LaunchedEffect(state.messages.size, streamedLengthBucket) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    val ready = state.runtimeState == RuntimeState.READY
    val generating = state.runtimeState == RuntimeState.GENERATING
    val submit = {
        if (ready && !imageCopying && prompt.isNotBlank()) {
            onSend(prompt, imagePath)
            prompt = ""
            imagePath = null
            imageError = null
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
                    if (imageCopying) Text("Attaching image…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    imagePath?.let {
                        FilterChip(
                            selected = true,
                            onClick = {
                                File(it).delete()
                                imagePath = null
                            },
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
                            IconButton(onClick = { imagePicker.launch("image/*") }, enabled = ready && !imageCopying) {
                                LucideIcon(LucideR.drawable.lucide_ic_image, "Attach image", Modifier.size(20.dp))
                            }
                        }} else null,
                        trailingIcon = {
                            FilledIconButton(
                                onClick = { if (generating) onStop() else submit() },
                                enabled = generating || (ready && !imageCopying && prompt.isNotBlank()),
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
                    imageError?.let {
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

internal suspend fun copyImageToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val image = File.createTempFile("chat-image-", ".bin", context.cacheDir)
    var copied = false
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            image.outputStream().use(input::copyTo)
        } ?: return@withContext null
        copied = true
        image.absolutePath
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    } finally {
        if (!copied) image.delete()
    }
}
