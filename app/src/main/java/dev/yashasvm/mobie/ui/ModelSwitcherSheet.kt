package dev.yashasvm.mobie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.data.download.InstalledModelEntry

/** Installed models are selectable without starting the runtime; generation loads on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSwitcherSheet(
    installed: List<InstalledModelEntry>,
    activeId: String?,
    onPick: (InstalledModelEntry) -> Unit,
    onGetMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Select model", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp))
            }
            if (installed.isEmpty()) item {
                Text("Download a model to chat on this phone.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp))
            }
            items(installed, key = { it.model.id }) { entry ->
                val active = entry.model.id == activeId
                val artifact = entry.model.bestArtifact
                val specs = listOfNotNull(
                    artifact?.sizeBytes?.takeIf { it > 0 }?.let(::formatBytes),
                    artifact?.quantization,
                    artifact?.runtimeLabel,
                ).joinToString(" · ")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .semantics { selected = active }
                        .clickable(role = Role.Button, onClickLabel = "Select ${entry.model.title}", onClick = { onPick(entry) })
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.model.title, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (specs.isNotBlank()) Text(specs, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (active) LucideIcon(LucideR.drawable.lucide_ic_check, "Selected", Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).heightIn(min = 64.dp)
                    .clickable(role = Role.Button, onClick = onGetMore).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("More models", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    LucideIcon(LucideR.drawable.lucide_ic_chevron_right, null, Modifier.size(20.dp))
                }
            }
        }
    }
}
