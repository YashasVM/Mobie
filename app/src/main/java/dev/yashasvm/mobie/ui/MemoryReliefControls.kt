package dev.yashasvm.mobie.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun MemoryReliefControls(
    decision: MemoryReliefDecision,
    busy: Boolean,
    onFreeMemory: (() -> Unit) -> Unit,
) {
    var explain by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val reclaimLabel = if (decision.reclaimableBytes == 0L) "0 MB" else formatBytes(decision.reclaimableBytes)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Android may reclaim about $reclaimLabel more RAM; this is an estimate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("memory_reclaim_estimate"),
        )
        if (decision.showFreeMemory) {
            TextButton(
                onClick = { explain = true },
                enabled = !busy,
                modifier = Modifier.testTag("free_up_memory"),
            ) { Text(if (busy) "Releasing Mobie's memory…" else "Free up memory") }
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text("Free up memory") },
            text = { Text("Android doesn't let Mobie close other apps, so close apps yourself in Recents or system settings, then return here to check memory.") },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    onFreeMemory {
                        if (!MemoryReliefNavigator.open(context)) {
                            Toast.makeText(context, "Open Recents yourself to close apps, then return to Mobie.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Open system screen") }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text("Cancel") } },
        )
    }
}

/** Re-measures on entry and whenever the user returns from a system screen. */
@Composable
internal fun MemoryReliefResumeEffect(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        // addObserver brings the observer up to the owner's current state, including ON_RESUME.
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
