package com.example.sportsai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sportsai.ui.theme.GoodGreen
import com.example.sportsai.ui.theme.ScoreLow
import com.example.sportsai.ui.theme.SkyCyan
import com.example.sportsai.ui.theme.WarnAmber
import com.example.sportsai.viewmodel.GeminiSettingsUiState

private val SettingsCardShape = RoundedCornerShape(22.dp)

@Composable
fun SettingsDestination(
    state: GeminiSettingsUiState,
    onSaveAndTest: (String) -> Unit,
    onTestSavedKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onDismissMessage: () -> Unit
) {
    // Deliberately not saveable: plaintext credentials must not enter Android saved state.
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var removeConfirmationOpen by remember { mutableStateOf(false) }
    var previousMaskedKey by remember { mutableStateOf(state.maskedKey) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.maskedKey) {
        if (state.maskedKey != null && state.maskedKey != previousMaskedKey) {
            apiKey = ""
            showKey = false
        }
        previousMaskedKey = state.maskedKey
    }

    Column {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bring your own AI coach",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "SportsAI ships without a Gemini API key. Add your own key for Gemini coaching, or keep using the app's offline analysis without one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))

        ConnectionStatusCard(state)

        state.statusMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            StatusMessage(
                message = message,
                isError = state.isError,
                onDismiss = onDismissMessage
            )
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = SettingsCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (state.configured) "Replace API key" else "Add API key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Create a Gemini API key in Google AI Studio, then paste it below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Gemini API key" },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste your key") },
                    singleLine = true,
                    enabled = !state.isTesting,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    },
                    supportingText = {
                        Text("The full key is hidden again as soon as it is saved.")
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onSaveAndTest(apiKey) },
                    enabled = apiKey.isNotBlank() && !state.isTesting && !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isTesting && apiKey.isNotBlank()) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(9.dp))
                    }
                    Text("Save & test key", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Open Google AI Studio", fontWeight = FontWeight.Bold)
                }

                if (state.configured) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestSavedKey,
                            enabled = !state.isTesting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (state.isTesting && apiKey.isBlank()) {
                                CircularProgressIndicator(
                                    Modifier.size(17.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(7.dp))
                            }
                            Text("Test saved key", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { removeConfirmationOpen = true },
                            enabled = !state.isTesting,
                            modifier = Modifier.weight(0.68f)
                        ) {
                            Text("Remove", color = ScoreLow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SecurityCard()
    }

    if (removeConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { removeConfirmationOpen = false },
            title = { Text("Remove Gemini API key?") },
            text = {
                Text("The encrypted key will be deleted from this device. Offline coaching will remain available.")
            },
            confirmButton = {
                TextButton(onClick = {
                    removeConfirmationOpen = false
                    apiKey = ""
                    onRemoveKey()
                }) {
                    Text("Remove key", color = ScoreLow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirmationOpen = false }) {
                    Text("Keep key")
                }
            }
        )
    }
}

@Composable
private fun ConnectionStatusCard(state: GeminiSettingsUiState) {
    val color = when {
        state.isLoading -> SkyCyan
        state.configured -> GoodGreen
        else -> WarnAmber
    }
    val title = when {
        state.isLoading -> "Checking this device"
        state.configured -> "Gemini key saved"
        else -> "Offline coaching active"
    }
    val detail = when {
        state.isLoading -> "Reading the secure settings on this device…"
        state.configured -> "${state.maskedKey.orEmpty()} · Gemini 3.5 Flash"
        else -> "Pose tracking and the built-in technique coach still work without a key."
    }

    Surface(
        shape = SettingsCardShape,
        color = color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(color.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = color, strokeWidth = 2.dp)
                } else {
                    Box(Modifier.size(10.dp).background(color, CircleShape))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val color = if (isError) ScoreLow else GoodGreen
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isError) "!" else "✓", color = color, fontWeight = FontWeight.Black)
            Spacer(Modifier.size(9.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun SecurityCard() {
    Surface(
        shape = SettingsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("How your key and video are handled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            SecurityLine("ON DEVICE", "Your key is encrypted with Android Keystore and excluded from app backups and device transfer.")
            Spacer(Modifier.height(12.dp))
            SecurityLine("WHEN USED", "The key is sent to Google's Gemini API only when you test it or request cloud coaching. Selected video frames are sent for that coaching request.")
            Spacer(Modifier.height(12.dp))
            SecurityLine("PUBLIC APP", "No developer API key is bundled in SportsAI or committed to this repository.")
            Spacer(Modifier.height(14.dp))
            Text(
                "API keys in any client app can still be exposed on a compromised device. Use a dedicated key, monitor its quota, and revoke it in AI Studio if your phone is lost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun SecurityLine(label: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.size(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
