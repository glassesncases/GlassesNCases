package com.neha.tempglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neha.tempglass.viewmodel.AddCaseModelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCaseModelDialog(
    state: AddCaseModelUiState,
    onDismiss: () -> Unit,
    onBrandInputChange: (String) -> Unit,
    onModelInputChange: (String) -> Unit,
    onQuantityInputChange: (String) -> Unit,
    onSelectSerialNumber: (Int) -> Unit,
    onSave: () -> Unit,
    onCreateBrand: (String) -> Unit = {}
) {
    var brandMenuExpanded by remember { mutableStateOf(false) }
    var serialMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.editingModelId == null) {
                    "Add Mobile Case Model"
                } else {
                    "Edit Mobile Case Model"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = brandMenuExpanded,
                    onExpandedChange = { brandMenuExpanded = !brandMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = state.brandInput,
                        onValueChange = {
                            onBrandInputChange(it)
                            brandMenuExpanded = true
                        },
                        label = { Text("Brand *") },
                        placeholder = { Text("Select or type brand") },
                        singleLine = true,
                        trailingIcon = {
                            if (state.brandInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        onBrandInputChange("")
                                        brandMenuExpanded = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear brand"
                                    )
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandMenuExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = brandMenuExpanded,
                        onDismissRequest = { brandMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        val typedBrand = state.brandInput.trim()
                        val exactMatch = state.brandSuggestions.any { it.equals(typedBrand, ignoreCase = true) }
                        if (typedBrand.isNotBlank() && !exactMatch) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "+ Create \"$typedBrand\"",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    onCreateBrand(typedBrand)
                                    brandMenuExpanded = false
                                }
                            )
                        }

                        state.brandSuggestions.forEach { brand ->
                            DropdownMenuItem(
                                text = { Text(brand) },
                                onClick = {
                                    onBrandInputChange(brand)
                                    brandMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (state.brandInput.isNotBlank() && state.brandSuggestions.isEmpty()) {
                    Text(
                        text = "Brand not found. You can add it manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = state.modelInput,
                    onValueChange = onModelInputChange,
                    label = { Text("Model name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.quantityInput,
                    onValueChange = onQuantityInputChange,
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.editingModelId == null) {
                    ExposedDropdownMenuBox(
                        expanded = serialMenuExpanded,
                        onExpandedChange = { serialMenuExpanded = !serialMenuExpanded }
                    ) {
                        val selectedLabel = state.selectedSerialNumber?.let { selected ->
                            if (selected == state.nextSerialNumber) {
                                "Max + 1 (Serial #$selected)"
                            } else {
                                "Gap Serial #$selected"
                            }
                        } ?: ""

                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select serial assignment") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = serialMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = serialMenuExpanded,
                            onDismissRequest = { serialMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            state.availableGapSerialNumbers.forEach { serial ->
                                DropdownMenuItem(
                                    text = { Text("Use gap: Serial #$serial") },
                                    onClick = {
                                        onSelectSerialNumber(serial)
                                        serialMenuExpanded = false
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Use Max + 1: Serial #${state.nextSerialNumber}") },
                                onClick = {
                                    onSelectSerialNumber(state.nextSerialNumber)
                                    serialMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isSaving
            ) {
                val label = if (state.isSaving) {
                    "Saving..."
                } else if (state.editingModelId == null) {
                    "Save"
                } else {
                    "Update"
                }

                Text(label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
