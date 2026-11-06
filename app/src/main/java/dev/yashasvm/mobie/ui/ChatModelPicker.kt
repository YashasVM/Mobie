package dev.yashasvm.mobie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.CloudModel
import dev.yashasvm.mobie.data.download.InstalledModelEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatModelPicker(
    state: MobieUiState,
    onDismiss: () -> Unit,
    onInstalled: (InstalledModelEntry) -> Unit,
    onCloud: (CloudModel) -> Unit,
    onModels: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().testTag("chat_model_picker_sheet"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("Choose a model", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp)) }
            if (state.installedModels.isNotEmpty()) item { SectionLabel("On this phone") }
            items(state.installedModels, key = { "local_${it.model.id}" }) { entry ->
                PickerModelRow(entry.model.title, false, state.selected?.id == entry.model.id,
                    "pick_local_${entry.model.id}", onClick = { onInstalled(entry) })
            }
            if (state.cloudModels.isNotEmpty()) item { SectionLabel("Cloud") }
            items(state.cloudModels, key = { "cloud_${it.id}" }) { model ->
                PickerModelRow(model.title, true, state.selected?.cloudModel == model,
                    "pick_cloud_${model.id}", onClick = { onCloud(model) })
            }
            item {
                TermButton("All models", onClick = onModels, style = TermButtonStyle.Ghost,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("picker_all_models"))
            }
        }
    }
}

@Composable
private fun PickerModelRow(title: String, cloud: Boolean, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LucideIcon(if (cloud) LucideR.drawable.lucide_ic_cloud else LucideR.drawable.lucide_ic_smartphone,
            if (cloud) "Cloud" else "On device", Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (selected) LucideIcon(LucideR.drawable.lucide_ic_check, "Selected", Modifier.size(18.dp))
    }
}
