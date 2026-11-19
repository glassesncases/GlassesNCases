package com.neha.tempglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LinkDeviceDialog(
    isPrimaryDevice: Boolean,
    isSignedIn: Boolean,
    isLinked: Boolean,
    generatedCode: String?,
    isGenerating: Boolean,
    isPairingBusy: Boolean,
    error: String?,
    syncsRemaining: Int?,
    needsPasswordForCode: Boolean = false,
    onGenerateCode: () -> Unit,
    onGenerateCodeWithPassword: (String) -> Unit = {},
    onEnterCode: (String) -> Unit,
    onUnlinkDevice: () -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    !isPrimaryDevice && isLinked -> "Linked Secondary Device"
                    isLinked -> "Primary Device (Linked)"
                    else -> "Link Device"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Role & Status Badge
                Surface(
                    color = if (!isPrimaryDevice) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (!isPrimaryDevice) "Linked Secondary Device" else "Primary Device",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (!isPrimaryDevice) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = if (!isPrimaryDevice) {
                                "Connected via link code. All models, cases, and brands stay synchronized in real time with the primary device."
                            } else if (isLinked) {
                                "Linked with secondary devices. All changes sync bi-directionally with no sync limits."
                            } else {
                                "Can generate link codes to link secondary devices (WhatsApp-style), or connect with another device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isPrimaryDevice) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            }
                        )
                    }
                }

                // If this is a Secondary Device: NEVER show the "Generate Link Code" option
                if (!isPrimaryDevice && isLinked) {
                    Text(
                        text = "Continuous sync is active. Both devices share the same inventory catalog, stock sales, and custom brands.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = onUnlinkDevice,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlink Device")
                    }
                } else {
                    // Primary Device section
                    if (needsPasswordForCode) {
                        Text(
                            text = "Confirm account password to generate link code:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Account Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onGenerateCodeWithPassword(passwordInput) },
                            enabled = passwordInput.isNotBlank() && !isGenerating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isGenerating) "Verifying..." else "Authorize & Generate Code")
                        }
                    } else if (generatedCode != null) {
                        Text(
                            text = "Link Code for Secondary Device:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = generatedCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            text = "Open this app on the secondary device, tap 'Link Device', and enter this code. It will log in and sync automatically. Expires in 15 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = onGenerateCode,
                            enabled = !isGenerating && !isPairingBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isGenerating) "Generating Code..." else "Generate Link Code")
                        }
                        Text(
                            text = "Generate a code so another phone can link without entering your password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Enter Code Section (Both primary and secondary can enter codes to link)
                    Text(
                        text = "Enter Link Code from Primary Device:",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase().take(6) },
                            label = { Text("6-Char Code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onEnterCode(codeInput) },
                            enabled = !isPairingBusy && codeInput.trim().length == 6
                        ) {
                            Text(if (isPairingBusy) "Linking..." else "Link")
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
                Text("Close")
            }
        }
    )
}
