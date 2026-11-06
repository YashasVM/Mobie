package dev.yashasvm.mobie.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import dev.yashasvm.mobie.core.model.CLOUD_DISCLOSURE
import dev.yashasvm.mobie.core.model.CLOUD_PRIVACY_URL
import dev.yashasvm.mobie.core.model.CloudModel

@Composable
internal fun CloudModelConsent(model: CloudModel, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { LucideIcon(LucideR.drawable.lucide_ic_cloud, "Cloud model", Modifier.size(24.dp)) },
        title = { Text("Use ${model.title}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(CLOUD_DISCLOSURE)
                Text(model.privacyNotice)
                Text("This model's saved conversation is included. Local model chats stay on this phone.")
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CLOUD_PRIVACY_URL))) }) {
                    Text("Privacy terms")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_cloud_model")) { Text("Use cloud model") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
