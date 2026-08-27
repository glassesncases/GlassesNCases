package com.neha.tempglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neha.tempglass.data.ModelType
import com.neha.tempglass.viewmodel.AddModelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddModelDialog(
    state: AddModelUiState,
    onDismiss: () -> Unit,
    onBrandInputChange: (String) -> Unit,
    onModelInputChange: (String) -> Unit,
    onAdditionalChange: (String) -> Unit,
    onTypeSelected: (ModelType) -> Unit,
    onBoxInputChange: (String) -> Unit,
    onSelectBoxNumber: (Int) -> Unit,
    onSave: () -> Unit,
    isEditMode: Boolean = false
) {
    var boxMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var brandMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Model" else "Add Model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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

                OutlinedTextField(
                    value = state.modelInput,
                    onValueChange = onModelInputChange,
                    label = { Text("Model name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = state.selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ModelType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    onTypeSelected(type)
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = boxMenuExpanded,
                    onExpandedChange = { boxMenuExpanded = !boxMenuExpanded }
                ) {
                    val selectedLabel = state.selectedBoxNumber?.let { selected ->
                        if (selected == state.nextBoxNumber) {
                            "Max + 1 (Box #$selected)"
                        } else if (selected in state.availableGapBoxNumbers) {
                            "Gap Box #$selected"
                        } else {
                            "Existing Box #$selected"
                        }
                    } ?: ""

                    OutlinedTextField(
                        value = state.selectedBoxNumber?.toString().orEmpty(),
                        onValueChange = onBoxInputChange,
                        readOnly = false,
                        label = { Text("Select box assignment") },
                        supportingText = {
                            if (selectedLabel.isNotBlank()) Text(selectedLabel)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = boxMenuExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = boxMenuExpanded,
                        onDismissRequest = { boxMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        state.availableBoxNumbers.forEach { box ->
                            val label = when {
                                box == state.nextBoxNumber -> "Use Max + 1: Box #$box"
                                box in state.availableGapBoxNumbers -> "Use gap: Box #$box"
                                else -> "Use existing: Box #$box"
                            }

                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSelectBoxNumber(box)
                                    boxMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.additionalCompatibleInput,
                    onValueChange = onAdditionalChange,
                    label = { Text("Additional compatible models (optional)") },
                    supportingText = {
                        Text("Comma-separated values")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

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
                Text(
                    when {
                        state.isSaving && isEditMode -> "Updating..."
                        state.isSaving -> "Saving..."
                        isEditMode -> "Update"
                        else -> "Save"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
