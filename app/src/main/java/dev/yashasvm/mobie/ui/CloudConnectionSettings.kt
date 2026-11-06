package dev.yashasvm.mobie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.yashasvm.mobie.core.model.DEFAULT_CLOUD_GATEWAY_URL

/** Pairing credentials stay in memory until Save and never enter saved UI state. */
@Composable
internal fun CloudConnectionSettings(
    configuredUrl: String?,
    onSave: (String, String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var editing by remember(configuredUrl) { mutableStateOf(configuredUrl == null) }
    var url by remember(configuredUrl) { mutableStateOf(configuredUrl ?: DEFAULT_CLOUD_GATEWAY_URL) }
    var token by remember(configuredUrl) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OpenCode", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (configuredUrl != null) "Connected" else "Connect your Linux box",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (configuredUrl != null && !editing) {
                TermButton("Change", onClick = { editing = true }, style = TermButtonStyle.Ghost)
            }
        }
        if (editing) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("HTTPS server address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("cloud_gateway_url"),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Pairing key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().testTag("cloud_pairing_key"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton(
                    "Save connection",
                    onClick = { onSave(url, token); token = "" },
                    enabled = url.isNotBlank() && token.isNotBlank(),
                )
                if (configuredUrl != null) {
                    TermButton("Cancel", onClick = { token = ""; url = configuredUrl; editing = false }, style = TermButtonStyle.Ghost)
                }
            }
        }
        if (configuredUrl != null) {
            TermButton("Disconnect", onClick = { token = ""; onDisconnect() }, style = TermButtonStyle.Ghost)
        }
    }
}
