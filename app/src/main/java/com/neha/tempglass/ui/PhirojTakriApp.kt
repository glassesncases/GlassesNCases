package com.neha.tempglass.ui

import android.widget.Toast
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neha.tempglass.export.ExportFormat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neha.tempglass.viewmodel.AddModelEvent
import com.neha.tempglass.viewmodel.AddModelViewModel
import com.neha.tempglass.viewmodel.CasesViewModel
import com.neha.tempglass.viewmodel.CaseProtectedActionMode
import com.neha.tempglass.viewmodel.MainViewModel
import com.neha.tempglass.viewmodel.PendingExport
import com.neha.tempglass.viewmodel.ProtectedActionMode
import kotlinx.coroutines.flow.collectLatest

private enum class InventorySection {
    TEMPERED_GLASS,
    MOBILE_CASES
}

@Composable
fun PhirojTakriApp(
    mainViewModel: MainViewModel,
    addModelViewModel: AddModelViewModel,
    casesViewModel: CasesViewModel
) {
    val context = LocalContext.current
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val addModelUiState by addModelViewModel.uiState.collectAsStateWithLifecycle()
    val casesUiState by casesViewModel.uiState.collectAsStateWithLifecycle()
    var activeExport by remember { mutableStateOf<PendingExport?>(null) }
    var selectedSection by rememberSaveable { mutableStateOf(InventorySection.TEMPERED_GLASS) }
    var globalMenuExpanded by remember { mutableStateOf(false) }
    val activePendingExport = mainUiState.pendingExport ?: casesUiState.pendingExport
    val infiniteTransition = rememberInfiniteTransition(label = "sync_icon_rotation")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )

    fun handleDocumentResult(expectedFormat: ExportFormat, uriString: String?) {
        val export = activeExport

        if (uriString != null && export != null && export.format == expectedFormat) {
            val success = runCatching {
                val uri = android.net.Uri.parse(uriString)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(export.contentBytes)
                    stream.flush()
                } ?: error("Unable to open output stream")
            }.isSuccess

            Toast.makeText(
                context,
                if (success) "Export saved" else "Failed to save export",
                if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }

        activeExport = null
        mainViewModel.onPendingExportHandled()
        casesViewModel.onPendingExportHandled()
    }

    var pendingSystemAuthForMain by rememberSaveable { mutableStateOf(false) }
    var pendingSystemAuthForCases by rememberSaveable { mutableStateOf(false) }
    var pendingScreenLockToggleValue by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingScreenLockToggleValue?.let { requestedValue ->
                mainViewModel.setUseScreenLockForProtectedActions(requestedValue)
            }
            if (pendingSystemAuthForMain) {
                mainViewModel.confirmProtectedActionWithSystemAuth()
            }
            if (pendingSystemAuthForCases) {
                casesViewModel.confirmProtectedActionWithSystemAuth()
            }
        } else {
            if (pendingSystemAuthForMain) mainViewModel.dismissPasswordDialog()
            if (pendingSystemAuthForCases) casesViewModel.dismissPasswordDialog()
        }
        pendingSystemAuthForMain = false
        pendingSystemAuthForCases = false
        pendingScreenLockToggleValue = null
    }

    fun launchDeviceCredentialPrompt(
        forMain: Boolean,
        forCases: Boolean,
        forScreenLockToggleValue: Boolean? = null
    ) {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (forScreenLockToggleValue == true && !keyguardManager.isDeviceSecure) {
            Toast.makeText(context, "Set a screen lock on this device first", Toast.LENGTH_LONG).show()
            mainViewModel.setUseScreenLockForProtectedActions(false)
            if (forMain) mainViewModel.dismissPasswordDialog()
            if (forCases) casesViewModel.dismissPasswordDialog()
            pendingSystemAuthForMain = false
            pendingSystemAuthForCases = false
            pendingScreenLockToggleValue = null
            return
        }

        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Verify identity",
            "Use screen lock to continue"
        )
        if (intent == null) {
            Toast.makeText(context, "Screen lock not available", Toast.LENGTH_LONG).show()
            mainViewModel.setUseScreenLockForProtectedActions(false)
            if (forMain) mainViewModel.dismissPasswordDialog()
            if (forCases) casesViewModel.dismissPasswordDialog()
            pendingSystemAuthForMain = false
            pendingSystemAuthForCases = false
            pendingScreenLockToggleValue = null
            return
        }

        pendingSystemAuthForMain = forMain
        pendingSystemAuthForCases = forCases
        pendingScreenLockToggleValue = forScreenLockToggleValue
        deviceCredentialLauncher.launch(intent)
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        handleDocumentResult(ExportFormat.CSV, uri?.toString())
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        handleDocumentResult(ExportFormat.PDF, uri?.toString())
    }

    LaunchedEffect(addModelViewModel) {
        addModelViewModel.events.collectLatest { event ->
            when (event) {
                AddModelEvent.Saved -> {
                    mainViewModel.dismissAddDialog()
                    mainViewModel.markSyncRequired()
                }
            }
        }
    }

    LaunchedEffect(mainUiState.editTargetModel?.id) {
        mainUiState.editTargetModel?.let { target ->
            addModelViewModel.prepareForEdit(target)
        }
    }

    LaunchedEffect(mainUiState.isAddDialogVisible, mainUiState.editTargetModel?.id) {
        if (mainUiState.isAddDialogVisible && mainUiState.editTargetModel == null) {
            addModelViewModel.prepareForOpen()
        }
    }

    LaunchedEffect(activePendingExport?.requestId) {
        val pending = activePendingExport ?: return@LaunchedEffect
        activeExport = pending

        when (pending.format) {
            ExportFormat.CSV -> csvLauncher.launch(pending.suggestedFileName)
            ExportFormat.PDF -> pdfLauncher.launch(pending.suggestedFileName)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSection == InventorySection.TEMPERED_GLASS,
                onClick = { selectedSection = InventorySection.TEMPERED_GLASS },
                label = {
                    Text(
                        text = "T",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            )

            FilterChip(
                selected = selectedSection == InventorySection.MOBILE_CASES,
                onClick = { selectedSection = InventorySection.MOBILE_CASES },
                label = {
                    Text(
                        text = "M",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    if (mainUiState.isLinked && (mainUiState.syncsRemaining ?: 0) > 0) {
                        mainViewModel.consumeSharedSyncSlot()
                    }
                    mainViewModel.syncNow()
                },
                enabled = !mainUiState.isSyncing
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = if (mainUiState.isSyncing) "Syncing" else "Sync now",
                    modifier = Modifier
                        .size(36.dp)
                        .rotate(if (mainUiState.isSyncing) syncRotation else 0f)
                )
            }

            IconButton(
                onClick = {
                    mainViewModel.onInternetEnabledChange(!mainUiState.isInternetEnabled)
                }
            ) {
                Icon(
                    imageVector = if (mainUiState.isInternetEnabled) {
                        Icons.Default.Wifi
                    } else {
                        Icons.Default.WifiOff
                    },
                    contentDescription = if (mainUiState.isInternetEnabled) {
                        "Disable internet"
                    } else {
                        "Enable internet"
                    }
                )
            }

            IconButton(onClick = { globalMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Global options"
                )
            }

            DropdownMenu(
                expanded = globalMenuExpanded,
                onDismissRequest = { globalMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(mainUiState.accountEmail?.let { "Signed in: $it" } ?: "Not signed in")
                    },
                    onClick = { }
                )

                if (mainUiState.accountEmail == null) {
                    DropdownMenuItem(
                        text = { Text("Sign In") },
                        onClick = {
                            globalMenuExpanded = false
                            mainViewModel.openSignInDialog(false)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Create Account") },
                        onClick = {
                            globalMenuExpanded = false
                            mainViewModel.openSignInDialog(true)
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Sign Out") },
                        onClick = {
                            globalMenuExpanded = false
                            mainViewModel.signOutAccount()
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Link Another Device") },
                    onClick = {
                        globalMenuExpanded = false
                        mainViewModel.openLinkDialog()
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Use Screen Lock")
                            Switch(
                                checked = mainUiState.useScreenLockForProtectedActions,
                                onCheckedChange = { enabled ->
                                    launchDeviceCredentialPrompt(
                                        forMain = false,
                                        forCases = false,
                                        forScreenLockToggleValue = enabled
                                    )
                                }
                            )
                        }
                    },
                    onClick = { }
                )

            }

            if (mainUiState.isSyncRequired) {
                Text(
                    text = "!",
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        if (mainUiState.isSyncRequired) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mandatory sync required before data diverges across devices")
                    Button(
                        onClick = { mainViewModel.triggerMandatorySyncInBackground() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync Now")
                    }
                }
            }
        }

        mainUiState.syncStatusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedSection) {
                InventorySection.TEMPERED_GLASS -> {
                    MainScreen(
                        uiState = mainUiState,
                        onSearchChange = mainViewModel::onSearchQueryChange,
                        onBoxFilterInputChange = mainViewModel::onBoxFilterInputChange,
                        onBrandSelected = mainViewModel::onBrandSelected,
                        onShowDeletedChange = mainViewModel::onShowDeletedChange,
                        onFavoritesExpandedChange = mainViewModel::onFavoritesExpandedChange,
                        onToggleFavorite = mainViewModel::onToggleFavorite,
                        onAddClick = mainViewModel::onAddModelClick,
                        onExportCsv = mainViewModel::onExportCsvRequested,
                        onExportPdf = mainViewModel::onExportPdfRequested,
                        onEditModel = mainViewModel::onEditRequested,
                        onDeleteModel = mainViewModel::onDeleteRequested,
                        onRestoreModel = { model -> mainViewModel.restoreModel(model.id) },
                        onPermanentDeleteModel = mainViewModel::onPermanentDeleteRequested
                    )
                }

                InventorySection.MOBILE_CASES -> {
                    CasesScreen(
                        uiState = casesUiState,
                        onSearchChange = casesViewModel::onSearchQueryChange,
                        onBrandSelected = casesViewModel::onBrandSelected,
                        onShowStockOutOnlyChange = casesViewModel::onShowStockOutOnlyChange,
                        onShowDeletedChange = casesViewModel::onShowDeletedChange,
                        onOpenAddModelDialog = {
                            if (mainUiState.isSyncRequired) {
                                mainViewModel.markSyncRequired()
                            } else {
                                casesViewModel.openAddCaseModelDialog()
                            }
                        },
                        onDismissAddModelDialog = casesViewModel::dismissAddCaseModelDialog,
                        onAddBrandInputChange = casesViewModel::onAddCaseBrandInputChange,
                        onAddModelInputChange = casesViewModel::onAddCaseModelInputChange,
                        onAddQuantityInputChange = casesViewModel::onAddCaseQuantityInputChange,
                        onSelectSerialNumber = casesViewModel::onSelectedSerialNumberChange,
                        onSaveAddModel = {
                            if (mainUiState.isSyncRequired) {
                                mainViewModel.markSyncRequired()
                            } else {
                                casesViewModel.saveAddCaseModel()
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onIncreaseQuantity = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.increaseQuantity(model.id)
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onDecreaseQuantity = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.decreaseQuantity(model.id)
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onEditModel = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else casesViewModel.onEditRequested(model)
                        },
                        onDeleteModel = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.onDeleteRequested(model)
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onRestoreModel = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.onRestoreRequested(model)
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onPermanentDeleteModel = { model ->
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.onPermanentDeleteRequested(model)
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onAddToCart = { model -> casesViewModel.addToCart(model.id) },
                        onIncreaseCartQuantity = { cartItem ->
                            casesViewModel.increaseCartQuantity(cartItem.modelId)
                        },
                        onDecreaseCartQuantity = { cartItem ->
                            casesViewModel.decreaseCartQuantity(cartItem.modelId)
                        },
                        onRemoveCartItem = { cartItem ->
                            casesViewModel.removeFromCart(cartItem.modelId)
                        },
                        onSellCart = {
                            if (mainUiState.isSyncRequired) mainViewModel.markSyncRequired()
                            else {
                                casesViewModel.sellCart()
                                mainViewModel.markSyncRequired()
                            }
                        },
                        onDismissSellStatusMessage = casesViewModel::clearSellStatusMessage,
                        onExportCsv = casesViewModel::onExportCsvRequested,
                        onExportPdf = casesViewModel::onExportPdfRequested
                    )
                }
            }
        }
    }

    if (mainUiState.isAddDialogVisible) {
        AddModelDialog(
            state = addModelUiState,
            onDismiss = mainViewModel::dismissAddDialog,
            onBrandInputChange = addModelViewModel::onBrandInputChange,
            onModelInputChange = addModelViewModel::onModelInputChange,
            onAdditionalChange = addModelViewModel::onAdditionalCompatibleChange,
            onTypeSelected = addModelViewModel::onSelectedTypeChange,
            onBoxInputChange = addModelViewModel::onBoxNumberInputChange,
            onSelectBoxNumber = addModelViewModel::onSelectedBoxNumberChange,
            onSave = addModelViewModel::saveModel,
            isEditMode = addModelUiState.editingModelId != null
        )
    }

    mainUiState.passwordMode?.let { passwordMode ->
        if (mainUiState.useScreenLockForProtectedActions) {
            LaunchedEffect(passwordMode) {
                launchDeviceCredentialPrompt(forMain = true, forCases = false)
            }
            return@let
        }
        val message = when (passwordMode) {
            ProtectedActionMode.ADD -> "Enter password to add new model"
            ProtectedActionMode.DELETE -> "Enter password to delete model"
            ProtectedActionMode.EDIT -> "Enter password to edit built-in model"
            ProtectedActionMode.PERMANENT_DELETE -> "Enter password to permanently delete model"
        }

        val confirmText = when (passwordMode) {
            ProtectedActionMode.DELETE -> "Delete"
            ProtectedActionMode.PERMANENT_DELETE -> "Delete permanently"
            ProtectedActionMode.ADD,
            ProtectedActionMode.EDIT -> "Continue"
        }

        PasswordDialog(
            title = "Password Required",
            message = message,
            error = mainUiState.passwordError,
            confirmText = confirmText,
            onDismiss = mainViewModel::dismissPasswordDialog,
            onConfirm = mainViewModel::confirmProtectedAction
        )
    }

    casesUiState.passwordMode?.let { passwordMode ->
        if (mainUiState.useScreenLockForProtectedActions) {
            LaunchedEffect(passwordMode) {
                launchDeviceCredentialPrompt(forMain = false, forCases = true)
            }
            return@let
        }
        val message = when (passwordMode) {
            CaseProtectedActionMode.ADD -> "Enter password to add new case model"
            CaseProtectedActionMode.EDIT -> "Enter password to edit case model"
            CaseProtectedActionMode.DELETE -> "Enter password to delete case model"
            CaseProtectedActionMode.PERMANENT_DELETE -> "Enter password to permanently delete case model"
        }

        val confirmText = when (passwordMode) {
            CaseProtectedActionMode.DELETE -> "Delete"
            CaseProtectedActionMode.PERMANENT_DELETE -> "Delete permanently"
            CaseProtectedActionMode.ADD,
            CaseProtectedActionMode.EDIT -> "Continue"
        }

        PasswordDialog(
            title = "Password Required",
            message = message,
            error = casesUiState.passwordError,
            confirmText = confirmText,
            onDismiss = casesViewModel::dismissPasswordDialog,
            onConfirm = casesViewModel::confirmProtectedAction
        )
    }

    if (mainUiState.isAuthDialogVisible) {
        AuthDialog(
            isRegisterMode = mainUiState.isRegisterMode,
            error = mainUiState.authError,
            onDismiss = mainViewModel::dismissAuthDialog,
            onSubmit = mainViewModel::submitAuth
        )
    }

    if (mainUiState.isLinkDialogVisible) {
        LinkDeviceDialog(
            generatedCode = mainUiState.generatedCode,
            isGenerating = mainUiState.isGeneratingCode,
            error = mainUiState.linkError,
            isLinked = mainUiState.isLinked,
            syncsRemaining = mainUiState.syncsRemaining,
            onGenerateCode = mainViewModel::generateLinkCode,
            onEnterCode = mainViewModel::enterLinkCode,
            onDismiss = mainViewModel::dismissLinkDialog
        )
    }
}
