package dev.yashasvm.mobie.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Appearance and access settings. The token stays in memory only until saved or dismissed. */
@Composable
internal fun SettingsScreen(
    state: MobieUiState,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onSaveToken: (String) -> Unit,
) {
    var editingToken by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            SettingsCard("Appearance") {
                Text("Color mode", style = MaterialTheme.typography.bodyLarge)
                Text("Choose the look that's comfortable for you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = !darkTheme, onClick = { onDarkThemeChange(false) }, label = { Text("Light") }, modifier = Modifier.testTag("theme_light"))
                    FilterChip(selected = darkTheme, onClick = { onDarkThemeChange(true) }, label = { Text("Dark") }, modifier = Modifier.testTag("theme_dark"))
                }
            }
        }
        item {
            SettingsCard("Hugging Face") {
                Text(if (state.tokenConfigured) "Token saved securely" else "No token added", style = MaterialTheme.typography.bodyLarge)
                Text("Public models don't need an account. Add a token for models you've been granted access to.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draft = ""; editingToken = true }) { Text(if (state.tokenConfigured) "Change token" else "Add token") }
                    if (state.tokenConfigured) {
                        TextButton(onClick = { onSaveToken("") }) { Text("Remove token") }
                    }
                }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))) }) { Text("Manage Hugging Face tokens") }
            }
        }
        item {
            SettingsCard("Private by design") {
                Text("Your chats stay on this phone", style = MaterialTheme.typography.bodyLarge)
                Text("Models run locally after download. Your prompts, responses, and attachments aren't sent to an inference service.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("An internet connection is used to browse and download models from Hugging Face. Access tokens are kept in encrypted storage backed by Android Keystore.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("Mobie · Local AI, at your pace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
    if (editingToken) {
        AlertDialog(
            onDismissRequest = { draft = ""; editingToken = false },
            title = { Text("Hugging Face token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Use a read token with access to the model you want. Accept any model license on Hugging Face first.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Access token") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("hf_token_input"),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = { onSaveToken(draft.trim()); draft = ""; editingToken = false }) { Text("Save token") }
            },
            dismissButton = { TextButton(onClick = { draft = ""; editingToken = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
