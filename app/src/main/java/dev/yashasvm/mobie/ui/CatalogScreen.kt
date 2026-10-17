package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.device.CompatibilityResolver
import dev.yashasvm.mobie.core.runtime.ComputeBackend
import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.Compatibility
import dev.yashasvm.mobie.core.model.CompatibilityResult
import dev.yashasvm.mobie.data.download.InstalledModelEntry

/** Model library and catalog. All fit estimates come from the shared compatibility policy. */
@Composable
internal fun CatalogScreen(
    state: MobieUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (AiModel) -> Unit,
    onFeatured: () -> Unit,
    onOpenInstalled: (InstalledModelEntry) -> Unit = {},
    onDeleteInstalled: (InstalledModelEntry) -> Unit = {},
) {
    var installedTab by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InstalledModelEntry?>(null) }
    val resolver = remember { CompatibilityResolver() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (installedTab) 0 else 1, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = installedTab, onClick = { installedTab = true }, modifier = Modifier.testTag("bottom_nav_installed"), text = { Text("Installed") })
            Tab(selected = !installedTab, onClick = { installedTab = false }, modifier = Modifier.testTag("models_browse"), text = { Text("Browse") })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (installedTab) {
                item {
                    Text("Your local models", style = MaterialTheme.typography.titleLarge)
                    Text("Downloaded to this phone. Choose one to chat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let { error ->
                    item {
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (state.installedModels.isEmpty()) {
                    item {
                        ModelNotice("No models yet", "Download a model to start chatting privately on this phone.", "Browse models") { installedTab = false }
                    }
                }
                items(state.installedModels, key = { it.model.id }) { entry ->
                    val compatibility = remember(entry.model, state.device, state.computeBackend) { state.device?.let { resolver.resolve(entry.model.bestArtifact, it, entry.model.supportsVision, state.computeBackend.takeUnless { it == ComputeBackend.AUTO } ?: ComputeBackend.CPU) } }
                    CatalogModelCard(
                        model = entry.model,
                        compatibility = compatibility,
                        installed = true,
                        onClick = { onOpenInstalled(entry) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = { Text("Search models") },
                        leadingIcon = { LucideIcon(LucideR.drawable.lucide_ic_search, null, Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (state.query.isNotBlank()) {
                                Row {
                                    IconButton(onClick = onFeatured) { LucideIcon(LucideR.drawable.lucide_ic_x, "Clear search", Modifier.size(20.dp)) }
                                    IconButton(onClick = onSearch) { LucideIcon(LucideR.drawable.lucide_ic_arrow_right, "Search", Modifier.size(20.dp)) }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("model_search"),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    )
                }
                item {
                    Text(if (state.query.isBlank()) "Recommended" else "Search results", style = MaterialTheme.typography.titleLarge)
                    Text("LiteRT-LM models from litert-community. Review device requirements before downloading.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.device?.let { device ->
                    item {
                        Text("${formatBytes(device.availableRamBytes)} memory available · ${formatBytes(device.availableStorageBytes)} storage free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when {
                    state.loading -> item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Finding models…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.catalogError != null -> item {
                        ModelNotice("Couldn't load models", state.catalogError, "Retry", if (state.query.isBlank()) onFeatured else onSearch)
                    }
                    state.models.isEmpty() -> item {
                        ModelNotice("No matching models", "Try another name or browse the recommended models.", "Show recommended", onFeatured)
                    }
                    else -> items(state.models, key = { it.id }) { model ->
                        val compatibility = remember(model, state.device, state.computeBackend) { state.device?.let { resolver.resolve(model.bestArtifact, it, model.supportsVision, state.computeBackend.takeUnless { it == ComputeBackend.AUTO } ?: ComputeBackend.CPU) } }
                        CatalogModelCard(
                            model = model,
                            compatibility = compatibility,
                            installed = state.installedModels.any { it.model.id == model.id },
                            onClick = { onSelect(model) },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${entry.model.title}?") },
            text = { Text("This removes the downloaded model from this phone. You can download it again later. Your saved chats remain.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDeleteInstalled(entry) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CatalogModelCard(
    model: AiModel,
    compatibility: CompatibilityResult?,
    installed: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val artifact = model.bestArtifact
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleMedium)
                    Text(model.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { LucideIcon(LucideR.drawable.lucide_ic_trash_2, "Delete ${model.title}", Modifier.size(20.dp)) }
                }
            }
            Text(
                listOfNotNull(
                    artifact?.sizeBytes?.takeIf { it > 0 }?.let(::formatBytes) ?: "Size unknown",
                    artifact?.quantization,
                    artifact?.runtimeLabel,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOfNotNull(
                    "Installed".takeIf { installed },
                    "Photos supported".takeIf { model.supportsVision },
                    if (model.gated) "Access required" else null,
                    "License: ${model.license ?: "check publisher"}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            compatibility?.let { fit ->
                val label = when (fit.status) {
                    Compatibility.COMPATIBLE -> "Fits device estimates"
                    Compatibility.WARNING -> "Review memory requirements"
                    Compatibility.INCOMPATIBLE -> "Doesn't fit this device"
                    Compatibility.CONVERSION_REQUIRED -> "Unsupported format"
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (fit.status == Compatibility.INCOMPATIBLE || fit.status == Compatibility.CONVERSION_REQUIRED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (fit.estimatedRamBytes > 0) {
                    Text("Estimated memory: ${formatBytes(fit.estimatedRamBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fit.status != Compatibility.COMPATIBLE) {
                    Text(fit.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModelNotice(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
