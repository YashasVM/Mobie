package dev.yashasvm.mobie.ui

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import dev.yashasvm.mobie.ui.theme.Mobie

/**
 * Bottom sheet for switching the chat to another installed model in one tap. Lists each installed
 * model with its size, quantization, and runtime, marks the active one, and ends with a row that
 * opens the catalog to download more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSwitcherSheet(
    installed: List<InstalledModelEntry>,
    activeId: String?,
    onPick: (InstalledModelEntry) -> Unit,
    onGetMore: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                        Text("switch model", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${installed.size} installed",
                            style = Mobie.numeric.tiny,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Hairline()
                }
            }
            itemsIndexed(installed, key = { _, entry -> entry.model.id }) { index, entry ->
                ModelSwitcherRow(
                    entry = entry,
                    active = entry.model.id == activeId,
                    onClick = { onPick(entry) },
                    modifier = Modifier.entrance(entrance, entry.model.id, index),
                )
            }
            item(key = "get-more") {
                Column {
                    Hairline()
                    SwitcherActionRow(
                        text = "get more models",
                        onClick = onGetMore,
                    )
                }
            }
        }
    }
}

/** One installed model: marker, name, and a dim spec line. Inverts while pressed, like history rows. */
@Composable
private fun ModelSwitcherRow(
    entry: InstalledModelEntry,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    val dim = if (pressed) MaterialTheme.colorScheme.background.copy(alpha = .75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val artifact = entry.model.bestArtifact
    val specs = listOfNotNull(
        artifact?.sizeBytes?.takeIf { it > 0 }?.let(::formatBytes),
        artifact?.quantization,
        artifact?.runtimeLabel,
    ).joinToString(" · ")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (pressed) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Switch to ${entry.model.title}",
                    onClick = onClick,
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (active) "✓" else " ",
                style = Mobie.numeric.small,
                color = if (pressed) ink else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.model.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (specs.isNotBlank()) {
                    Text(
                        specs,
                        style = Mobie.numeric.tiny,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (active) Tag("active", color = MaterialTheme.colorScheme.primary)
        }
        Hairline()
    }
}

/** Terminal-style action row at the foot of the sheet, e.g. "+ get more models". */
@Composable
private fun SwitcherActionRow(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ink = if (pressed) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (pressed) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LucideIcon(LucideR.drawable.lucide_ic_plus, null, Modifier.size(16.dp), tint = ink)
        Text(text, style = Mobie.numeric.small, color = ink)
    }
}
