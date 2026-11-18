@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neha.tempglass.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.neha.tempglass.viewmodel.CaseCartItemUi
import com.neha.tempglass.viewmodel.CaseModelListItemUi
import com.neha.tempglass.viewmodel.CasesUiState

@Composable
fun CasesScreen(
    uiState: CasesUiState,
    onSearchChange: (String) -> Unit,
    onBrandSelected: (String) -> Unit,
    onShowStockOutOnlyChange: (Boolean) -> Unit,
    onShowDeletedChange: (Boolean) -> Unit,
    onOpenAddModelDialog: () -> Unit,
    onDismissAddModelDialog: () -> Unit,
    onAddBrandInputChange: (String) -> Unit,
    onAddModelInputChange: (String) -> Unit,
    onAddQuantityInputChange: (String) -> Unit,
    onSelectSerialNumber: (Int) -> Unit,
    onSaveAddModel: () -> Unit,
    onIncreaseQuantity: (CaseModelListItemUi) -> Unit,
    onDecreaseQuantity: (CaseModelListItemUi) -> Unit,
    onEditModel: (CaseModelListItemUi) -> Unit,
    onDeleteModel: (CaseModelListItemUi) -> Unit,
    onRestoreModel: (CaseModelListItemUi) -> Unit,
    onPermanentDeleteModel: (CaseModelListItemUi) -> Unit,
    onAddToCart: (CaseModelListItemUi) -> Unit,
    onIncreaseCartQuantity: (CaseCartItemUi) -> Unit,
    onDecreaseCartQuantity: (CaseCartItemUi) -> Unit,
    onRemoveCartItem: (CaseCartItemUi) -> Unit,
    onSellCart: () -> Unit,
    onDismissSellStatusMessage: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onCreateBrand: (String) -> Unit = {},
    onManageBrands: () -> Unit = {}
) {
    var expandedModelIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var optionsMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchFieldFocused by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.searchQuery))
    }
    var shiftModelsDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != searchText.text) {
            searchText = TextFieldValue(
                text = uiState.searchQuery,
                selection = TextRange(uiState.searchQuery.length)
            )
        }
    }

    fun toggleModelExpansion(modelId: Long) {
        expandedModelIds = if (modelId in expandedModelIds) {
            expandedModelIds - modelId
        } else {
            expandedModelIds + modelId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mobile Cases") },
                actions = {
                    Text(
                        text = "Models: ${uiState.totalModels}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    IconButton(onClick = { optionsMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = optionsMenuExpanded,
                        onDismissRequest = { optionsMenuExpanded = false }
                    ) {
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
                                optionsMenuExpanded = false
                                onShowDeletedChange(!uiState.showDeleted)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                optionsMenuExpanded = false
                                onExportCsv()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Export PDF") },
                            onClick = {
                                optionsMenuExpanded = false
                                onExportPdf()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Shift models") },
                            onClick = {
                                optionsMenuExpanded = false
                                shiftModelsDialogVisible = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenAddModelDialog) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add case model")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        onSearchChange(it.text)
                    },
                    label = { Text("Search mobile case model") },
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

            Text(
                text = "Total Quantity: ${uiState.totalQuantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.brands.forEach { brand ->
                    FilterChip(
                        selected = !uiState.showStockOutOnly && brand == uiState.selectedBrand,
                        onClick = { onBrandSelected(brand) },
                        label = { Text(brand) }
                    )
                }

                FilterChip(
                    selected = !uiState.showDeleted && uiState.showStockOutOnly,
                    onClick = { onShowStockOutOnlyChange(!uiState.showStockOutOnly) },
                    label = { Text("Stock Out") }
                )
            }

            uiState.sellStatusMessage?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = onDismissSellStatusMessage) {
                        Text("Dismiss")
                    }
                }
            }

            if (uiState.models.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching case models found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.models,
                        key = { it.id }
                    ) { model ->
                        CaseModelItem(
                            model = model,
                            isExpanded = model.id in expandedModelIds,
                            onToggleExpand = { toggleModelExpansion(model.id) },
                            onIncreaseQuantity = { onIncreaseQuantity(model) },
                            onDecreaseQuantity = { onDecreaseQuantity(model) },
                            onEdit = { onEditModel(model) },
                            onDelete = { onDeleteModel(model) },
                            onRestore = { onRestoreModel(model) },
                            onPermanentDelete = { onPermanentDeleteModel(model) },
                            onAddToCart = { onAddToCart(model) }
                        )
                    }
                }
            }

            if (uiState.cartTotalUnits > 0) {
                CartSummaryCard(
                    cartItems = uiState.cartItems,
                    cartItemCount = uiState.cartItemCount,
                    cartTotalUnits = uiState.cartTotalUnits,
                    onIncreaseCartQuantity = onIncreaseCartQuantity,
                    onDecreaseCartQuantity = onDecreaseCartQuantity,
                    onRemoveCartItem = onRemoveCartItem,
                    onSellCart = onSellCart
                )
            }
        }
    }

    if (uiState.addCaseModelUiState.isVisible) {
        AddCaseModelDialog(
            state = uiState.addCaseModelUiState,
            onDismiss = onDismissAddModelDialog,
            onBrandInputChange = onAddBrandInputChange,
            onModelInputChange = onAddModelInputChange,
            onQuantityInputChange = onAddQuantityInputChange,
            onSelectSerialNumber = onSelectSerialNumber,
            onSave = onSaveAddModel,
            onCreateBrand = onCreateBrand,
            onManageBrands = onManageBrands
        )
    }

    if (shiftModelsDialogVisible) {
        // Placeholder for the actual ShiftModelsDialog implementation
        // This dialog will allow selecting models and specifying a target serial number
        // For now, it just dismisses itself.
        ShiftModelsDialog(
            onDismiss = { shiftModelsDialogVisible = false }
        )
    }
}

@Composable
private fun ShiftModelsDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shift Models") },
        text = { Text("This is a placeholder for the Shift Models dialog.") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CaseModelItem(
    model: CaseModelListItemUi,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onIncreaseQuantity: () -> Unit,
    onDecreaseQuantity: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onAddToCart: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = {
                    if (!model.isDeleted) menuExpanded = true
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${model.brandSerialNumber}. ${model.modelName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = model.brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Qty: ${model.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.quantity == 0) {
                        Text(
                            text = "Stock out",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Text(
                    text = if (isExpanded) "Tap to hide controls" else "Tap to show controls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (model.isDeleted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRestore) {
                        Text("Restore")
                    }
                    TextButton(onClick = onPermanentDelete) {
                        Text("Delete permanently")
                    }
                }
            }

            if (isExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDecreaseQuantity,
                        enabled = model.quantity > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease quantity"
                        )
                    }

                    Text(
                        text = model.quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(onClick = onIncreaseQuantity) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase quantity"
                        )
                    }
                }
            }

            if (!model.isDeleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onAddToCart,
                        enabled = model.quantity > 0
                    ) {
                        Text("Add to cart")
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit this model") },
                onClick = {
                    menuExpanded = false
                    onEdit()
                }
            )

            DropdownMenuItem(
                text = { Text("Delete this model") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun CartSummaryCard(
    cartItems: List<CaseCartItemUi>,
    cartItemCount: Int,
    cartTotalUnits: Int,
    onIncreaseCartQuantity: (CaseCartItemUi) -> Unit,
    onDecreaseCartQuantity: (CaseCartItemUi) -> Unit,
    onRemoveCartItem: (CaseCartItemUi) -> Unit,
    onSellCart: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Cart ($cartItemCount items / $cartTotalUnits units)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            cartItems.forEach { cartItem ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cartItem.modelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Available: ${cartItem.availableQuantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDecreaseCartQuantity(cartItem) }) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease cart quantity"
                            )
                        }

                        Text(
                            text = cartItem.quantityInCart.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        IconButton(
                            onClick = { onIncreaseCartQuantity(cartItem) },
                            enabled = cartItem.quantityInCart < cartItem.availableQuantity
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase cart quantity"
                            )
                        }

                        TextButton(onClick = { onRemoveCartItem(cartItem) }) {
                            Text("Remove")
                        }
                    }
                }
            }

            Button(
                onClick = onSellCart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sell")
            }
        }
    }
}