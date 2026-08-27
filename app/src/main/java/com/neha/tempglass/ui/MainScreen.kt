@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neha.tempglass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.PopupProperties
import com.neha.tempglass.viewmodel.MainUiState
import com.neha.tempglass.viewmodel.ModelListItemUi

@Composable
fun MainScreen(
    uiState: MainUiState,
    onSearchChange: (String) -> Unit,
    onBoxFilterInputChange: (String) -> Unit,
    onBrandSelected: (String) -> Unit,
    onShowDeletedChange: (Boolean) -> Unit,
    onFavoritesExpandedChange: (Boolean) -> Unit,
    onToggleFavorite: (ModelListItemUi) -> Unit,
    onAddClick: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onEditModel: (ModelListItemUi) -> Unit,
    onDeleteModel: (ModelListItemUi) -> Unit,
    onRestoreModel: (ModelListItemUi) -> Unit,
    onPermanentDeleteModel: (ModelListItemUi) -> Unit
) {
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var boxMenuExpanded by remember { mutableStateOf(false) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(TextFieldValue(uiState.searchQuery)) }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != searchText.text) {
            searchText = TextFieldValue(
                text = uiState.searchQuery,
                selection = TextRange(uiState.searchQuery.length)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Phiroj Takri") },
                actions = {
                    Text(
                        text = "Total: ${uiState.totalAvailableModels}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    IconButton(onClick = { exportMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Export options"
                        )
                    }

                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    uiState.accountEmail?.let { "Signed in: $it" } ?: "Not signed in"
                                )
                            },
                            onClick = { }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (uiState.isFavoritesExpanded) {
                                        "Hide favorites"
                                    } else {
                                        "Show favorites"
                                    }
                                )
                            },
                            onClick = {
                                exportMenuExpanded = false
                                onFavoritesExpandedChange(!uiState.isFavoritesExpanded)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (uiState.showDeleted) {
                                        "Hide deleted models"
                                    } else {
                                        "Show deleted models"
                                    }
                                )
                            },
                            onClick = {
                                exportMenuExpanded = false
                                onShowDeletedChange(!uiState.showDeleted)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                exportMenuExpanded = false
                                onExportCsv()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export PDF") },
                            onClick = {
                                exportMenuExpanded = false
                                onExportPdf()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add model")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        onSearchChange(it.text)
                    },
                    label = { Text("Search by model") },
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchText = TextFieldValue("")
                                    onSearchChange("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            searchFieldFocused = focusState.isFocused
                        }
                )

                DropdownMenu(
                    expanded = searchFieldFocused &&
                        uiState.searchQuery.isNotBlank() &&
                        uiState.searchSuggestions.isNotEmpty(),
                    onDismissRequest = { },
                    properties = PopupProperties(focusable = false)
                ) {
                    uiState.searchSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                val suggestionWithSpace = if (suggestion.endsWith(" ")) {
                                    suggestion
                                } else {
                                    "$suggestion "
                                }
                                searchText = TextFieldValue(
                                    text = suggestionWithSpace,
                                    selection = TextRange(suggestionWithSpace.length)
                                )
                                onSearchChange(suggestionWithSpace)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.selectedBoxInput,
                    onValueChange = onBoxFilterInputChange,
                    label = { Text("Filter by box") },
                    placeholder = { Text("All boxes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = { boxMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select box"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = boxMenuExpanded,
                    onDismissRequest = { boxMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All boxes") },
                        onClick = {
                            boxMenuExpanded = false
                            onBoxFilterInputChange("")
                        }
                    )

                    uiState.availableBoxes.forEach { box ->
                        DropdownMenuItem(
                            text = { Text("Box #$box") },
                            onClick = {
                                boxMenuExpanded = false
                                onBoxFilterInputChange(box.toString())
                            }
                        )
                    }
                }
            }

            if (uiState.selectedBoxInput.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Results: ${uiState.models.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites (${uiState.favoriteModels.size})",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (uiState.isFavoritesExpanded) {
                if (uiState.favoriteModels.isEmpty()) {
                    Text(
                        text = "No favorites yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.favoriteModels.forEach { favoriteModel ->
                            ModelItem(
                                model = favoriteModel,
                                onToggleFavorite = { onToggleFavorite(favoriteModel) },
                                onEdit = { onEditModel(favoriteModel) },
                                onDelete = { onDeleteModel(favoriteModel) },
                                onRestore = { onRestoreModel(favoriteModel) },
                                onPermanentDelete = { onPermanentDeleteModel(favoriteModel) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.brands.forEach { brand ->
                    val count = uiState.filterCounts[brand] ?: 0
                    FilterChip(
                        selected = brand == uiState.selectedBrand,
                        onClick = { onBrandSelected(brand) },
                        label = { Text("$brand ($count)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.models.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching models found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.models,
                        key = { it.id }
                    ) { model ->
                        ModelItem(
                            model = model,
                            onToggleFavorite = { onToggleFavorite(model) },
                            onEdit = { onEditModel(model) },
                            onDelete = { onDeleteModel(model) },
                            onRestore = { onRestoreModel(model) },
                            onPermanentDelete = { onPermanentDeleteModel(model) }
                        )
                    }
                }
            }
        }
    }
}
