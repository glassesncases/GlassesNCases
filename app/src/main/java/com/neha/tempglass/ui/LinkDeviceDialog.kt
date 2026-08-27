package com.neha.tempglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LinkDeviceDialog(
    generatedCode: String?,
    isGenerating: Boolean,
    error: String?,
    isLinked: Boolean,
    syncsRemaining: Int?,
    onGenerateCode: () -> Unit,
    onEnterCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (isLinked) "Devices Linked" else "Link Another Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isLinked) {
                    Text(
                        text = "Your account is linked. Shared syncs remaining: ${syncsRemaining ?: 0}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "After 5 shared syncs, the two accounts will become independent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Generate a code to share your data with another device, or enter a code from another device to receive their data.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (generatedCode != null) {
                        Text(
                            text = generatedCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Text(
                            text = "Share this 6-character code with the other device. It expires in 15 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = onGenerateCode,
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isGenerating) "Generating..." else "Generate Link Code")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase().take(6) },
                            label = { Text("Enter code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { onEnterCode(codeInput) }) {
                            Text("Link")
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isLinked) "Done" else "Cancel")
            }
        }
    )
}