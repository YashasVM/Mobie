package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.runtime.ComputeBackend

@Composable
internal fun ChatControlsRow(state: MobieUiState, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClick, modifier = Modifier.testTag("chat_controls")) {
                LucideIcon(LucideR.drawable.lucide_ic_sliders_horizontal, null, Modifier.size(16.dp))
                Text(
                    if (state.selected?.cloudModel != null) "Chat settings"
                    else "Chat settings · ${state.activeCompute?.backend?.name ?: "Auto"}",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        state.activeCompute?.fallbackReason?.let { reason ->
            Text(
                reason,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("chat_compute_fallback"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimpleChatControlsSheet(
    state: MobieUiState,
    onBackendChange: (ComputeBackend) -> Unit,
    onApply: () -> Unit,
    onReplySpeedChange: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("chat_controls_sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Chat settings", style = MaterialTheme.typography.titleLarge)
            ReplySpeedControl(state.replyTokensPerSecond, onReplySpeedChange)
        }
    }
}

/** Controls the rate text appears, independently of the engine's measured generation speed. */
@Composable
internal fun ReplySpeedControl(tokensPerSecond: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Text display speed", style = MaterialTheme.typography.titleSmall)
        val presets = listOf<Int?>(10, 30, 60, null)
        val choices = if (tokensPerSecond !in presets) listOf(tokensPerSecond) + presets else presets
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { speed ->
                FilterChip(
                    selected = tokensPerSecond == speed,
                    onClick = { onChange(speed) },
                    label = { Text(when (speed) { 10 -> "Slow"; 30 -> "Steady"; 60 -> "Fast"; null -> "Unlimited"; else -> "$speed tokens/s" }) },
                    modifier = Modifier.testTag("reply_speed_${speed ?: "unlimited"}"),
                )
            }
        }
        Text(
            "Display only. Does not change model speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
