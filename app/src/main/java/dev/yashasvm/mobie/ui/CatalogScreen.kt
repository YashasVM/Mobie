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
