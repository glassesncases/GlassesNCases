package com.neha.tempglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neha.tempglass.data.CompatibleModelsCodec
import com.neha.tempglass.data.ModelEntity
import com.neha.tempglass.data.ModelType
import com.neha.tempglass.export.ExportFilterRow
import com.neha.tempglass.export.ExportFormat
import com.neha.tempglass.export.ExportModelRow
import com.neha.tempglass.export.ExportPayload
import com.neha.tempglass.export.buildExportCsvBytes
import com.neha.tempglass.export.buildExportPdfBytes
import com.neha.tempglass.repository.AddBrandResult
import com.neha.tempglass.repository.BrandRepository
import com.neha.tempglass.repository.CaseRepository
import com.neha.tempglass.repository.ModelRepository
import com.neha.tempglass.repository.UpdateBrandResult
import com.neha.tempglass.util.BrandDetector
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DELETE_PASSWORD = "DigitalGarage"
private const val PASSWORD_TRUST_WINDOW_MS = 10 * 60 * 1000L
private const val ALL_BRANDS = "All"
private const val USER_ADDED_FILTER = "User Added"
private const val CURVED_GLASSES_FILTER = "Curved Glasses"
private const val MEMBRANE_FILTER = "Membrane"
private val EXTRA_SEARCH_KEYWORDS = listOf(
    "Reno",
    "Narzo",
    "Nord",
    "Galaxy",
    "iPhone",
    "Redmi",
    "Poco",
    "Realme",
    "OnePlus",
    "Samsung",
    "Oppo",
    "Vivo",
    "Apple",
    "Pro",
    "Plus",
    "Prime",
    "Ultra",
    "Lite",
    "Neo",
    "Find",
    "Ace",
    "Max"
)

enum class ProtectedActionMode {
    ADD,
    DELETE,
    EDIT,
    PERMANENT_DELETE
}

private sealed interface ProtectedActionRequest {
    data object Add : ProtectedActionRequest
    data class Delete(val model: ModelListItemUi) : ProtectedActionRequest
    data class Edit(val model: ModelListItemUi) : ProtectedActionRequest
    data class PermanentDelete(val model: ModelListItemUi) : ProtectedActionRequest
}

data class ModelListItemUi(
    val id: Long,
    val modelName: String,
    val boxNumber: Int,
    val additionalCompatible: List<String>,
    val type: ModelType,
    val isUserAdded: Boolean,
    val isDeleted: Boolean,
    val isFavorite: Boolean,
    val brand: String
)

data class PendingExport(
    val requestId: Long,
    val format: ExportFormat,
    val suggestedFileName: String,
    val contentBytes: ByteArray
)

data class MainUiState(
    val isLinkDialogVisible: Boolean = false,
    val generatedCode: String? = null,
    val isGeneratingCode: Boolean = false,
    val linkError: String? = null,
    val isLinked: Boolean = false,
    val syncsRemaining: Int? = null,
    val isPairingBusy: Boolean = false,
    val searchQuery: String = "",
    val searchSuggestions: List<String> = emptyList(),
    val selectedBrand: String = ALL_BRANDS,
    val selectedBoxInput: String = "",
    val availableBoxes: List<Int> = emptyList(),
    val showDeleted: Boolean = false,
    val totalAvailableModels: Int = 0,
    val filterCounts: Map<String, Int> = emptyMap(),
    val favoriteModels: List<ModelListItemUi> = emptyList(),
    val isFavoritesExpanded: Boolean = true,
    val models: List<ModelListItemUi> = emptyList(),
    val brands: List<String> = listOf(
        ALL_BRANDS,
        USER_ADDED_FILTER,
        CURVED_GLASSES_FILTER,
        MEMBRANE_FILTER
    ),
    val isAddDialogVisible: Boolean = false,
    val editTargetModel: ModelListItemUi? = null,
    val passwordTargetModel: ModelListItemUi? = null,
    val passwordMode: ProtectedActionMode? = null,
    val passwordError: String? = null,
    val pendingExport: PendingExport? = null,
    val isInternetEnabled: Boolean = false,
    val useScreenLockForProtectedActions: Boolean = false,
    val isSyncing: Boolean = false,
    val isSyncRequired: Boolean = false,
    val syncStatusMessage: String? = null,
    val accountEmail: String? = null,
    val isAuthDialogVisible: Boolean = false,
    val isRegisterMode: Boolean = false,
    val authError: String? = null,
    val isAddBrandDialogVisible: Boolean = false,
    val addBrandError: String? = null,
    val isManageBrandsDialogVisible: Boolean = false,
    val brandToEdit: String? = null,
    val editBrandError: String? = null,
    val allBrandsList: List<String> = emptyList()
)

class MainViewModel(
    private val repository: ModelRepository,
    private val caseRepository: CaseRepository,
    private val brandRepository: BrandRepository
) : ViewModel() {

    private var lastPasswordVerifiedAtMs: Long = 0L

    private val searchQuery = MutableStateFlow("")
    private val selectedBrand = MutableStateFlow(ALL_BRANDS)
    private val selectedBoxInput = MutableStateFlow("")
    private val showDeleted = MutableStateFlow(false)
    private val isFavoritesExpanded = MutableStateFlow(true)
    private val isAddDialogVisible = MutableStateFlow(false)
    private val editTargetModel = MutableStateFlow<ModelListItemUi?>(null)
    private val protectedAction = MutableStateFlow<ProtectedActionRequest?>(null)
    private val passwordError = MutableStateFlow<String?>(null)
    private val pendingExport = MutableStateFlow<PendingExport?>(null)
    private val isInternetEnabled = MutableStateFlow(repository.isInternetEnabled())
    private val useScreenLockForProtectedActions = MutableStateFlow(repository.isUseScreenLockEnabled())
    private val isSyncing = MutableStateFlow(false)
    private val isSyncRequired = MutableStateFlow(false)
    private val syncStatusMessage = MutableStateFlow<String?>(null)
    private val accountEmail = MutableStateFlow(repository.currentUserEmail())
    private val isAuthDialogVisible = MutableStateFlow(false)
    private val isRegisterMode = MutableStateFlow(false)
    private val authError = MutableStateFlow<String?>(null)
    private val isAddBrandDialogVisible = MutableStateFlow(false)
    private val addBrandError = MutableStateFlow<String?>(null)
    private val isManageBrandsDialogVisible = MutableStateFlow(false)
    private val brandToEdit = MutableStateFlow<String?>(null)
    private val editBrandError = MutableStateFlow<String?>(null)
    private val isLinkDialogVisible = MutableStateFlow(false)
    private val generatedCode = MutableStateFlow<String?>(null)
    private val isGeneratingCode = MutableStateFlow(false)
    private val linkError = MutableStateFlow<String?>(null)
    private val isLinked = MutableStateFlow(repository.currentOtherUid() != null)
    private val syncsRemaining = MutableStateFlow<Int?>(null)
    private val isPairingBusy = MutableStateFlow(false)

    private var exportRequestCounter: Long = 0

    private val allKnownBrands = brandRepository.observeAllBrands()

    private val allModels = repository.observeAllModels().map { entities ->
        entities.map { it.toUi() }
    }

    private val brands = combine(
        repository.observeActiveModels(),
        allKnownBrands
    ) { entities, _ ->
        val dynamicBrands = entities
            .map { BrandDetector.detectBrand(it.modelName) }
            .filterNot {
                it == USER_ADDED_FILTER ||
                    it == CURVED_GLASSES_FILTER ||
                    it == MEMBRANE_FILTER
            }
            .distinct()
            .sorted()
        listOf(
            ALL_BRANDS,
            USER_ADDED_FILTER,
            CURVED_GLASSES_FILTER,
            MEMBRANE_FILTER
        ) + dynamicBrands
    }

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<MainUiState> = combine(
        allModels,
        brands,
        searchQuery,
        searchQuery.debounce(300),
        selectedBrand,
        selectedBoxInput,
        showDeleted,
        isFavoritesExpanded,
        isAddDialogVisible,
        protectedAction,
        passwordError,
        editTargetModel,
        pendingExport,
        isInternetEnabled,
        useScreenLockForProtectedActions,
        isSyncing,
        isSyncRequired,
        syncStatusMessage,
        accountEmail,
        isAuthDialogVisible,
        isRegisterMode,
        authError,
        isLinkDialogVisible,
        generatedCode,
        isGeneratingCode,
        linkError,
        isLinked,
        syncsRemaining,
        isPairingBusy,
        isAddBrandDialogVisible,
        addBrandError,
        isManageBrandsDialogVisible,
        brandToEdit,
        editBrandError,
        allKnownBrands
    ) { data ->
        val modelItems = data[0] as List<ModelListItemUi>
        val brandItems = data[1] as List<String>
        val rawQuery = data[2] as String
        val query = (data[3] as String).trim()
        val selected = data[4] as String
        val selectedBoxRawInput = data[5] as String
        val includeDeleted = data[6] as Boolean
        val favoritesExpanded = data[7] as Boolean
        val addDialogVisible = data[8] as Boolean
        val action = data[9] as ProtectedActionRequest?
        val passError = data[10] as String?
        val editTarget = data[11] as ModelListItemUi?
        val pendingExportRequest = data[12] as PendingExport?
        val internetEnabled = data[13] as Boolean
        val useScreenLock = data[14] as Boolean
        val syncing = data[15] as Boolean
        val syncRequired = data[16] as Boolean
        val syncMessage = data[17] as String?
        val signedInEmail = data[18] as String?
        val authDialogVisible = data[19] as Boolean
        val registerMode = data[20] as Boolean
        val authErr = data[21] as String?
        val linkDialogVisible = data[22] as Boolean
        val codeGenerated = data[23] as String?
        val generatingCode = data[24] as Boolean
        val linkErr = data[25] as String?
        val linked = data[26] as Boolean
        val remainingSyncs = data[27] as Int?
        val pairingBusy = data[28] as Boolean
        val addBrandDialogVisible = data[29] as Boolean
        val addBrandErr = data[30] as String?
        val manageBrandsVisible = data[31] as Boolean
        val editingBrand = data[32] as String?
        val editBrandErr = data[33] as String?
        @Suppress("UNCHECKED_CAST")
        val knownBrandsList = data[34] as List<String>

        val passwordTarget = when (action) {
            is ProtectedActionRequest.Add -> null
            is ProtectedActionRequest.Delete -> action.model
            is ProtectedActionRequest.Edit -> action.model
            is ProtectedActionRequest.PermanentDelete -> action.model
            null -> null
        }

        val passwordMode = when (action) {
            is ProtectedActionRequest.Add -> ProtectedActionMode.ADD
            is ProtectedActionRequest.Delete -> ProtectedActionMode.DELETE
            is ProtectedActionRequest.Edit -> ProtectedActionMode.EDIT
            is ProtectedActionRequest.PermanentDelete -> ProtectedActionMode.PERMANENT_DELETE
            null -> null
        }

        val safeSelectedBrand = selected.takeIf { it in brandItems } ?: ALL_BRANDS

        val shouldSortByBrandThenModel =
            safeSelectedBrand == ALL_BRANDS ||
                safeSelectedBrand == USER_ADDED_FILTER ||
                safeSelectedBrand == CURVED_GLASSES_FILTER ||
                safeSelectedBrand == MEMBRANE_FILTER

        val selectedBoxNumber = selectedBoxRawInput.trim().toIntOrNull()

        val availableBoxes = modelItems
            .asSequence()
            .map { it.boxNumber }
            .distinct()
            .sorted()
            .toList()

        val activeModelsForCounts = modelItems
            .asSequence()
            .filter { !it.isDeleted }
            .toList()

        val searchSuggestions = if (rawQuery.isBlank()) {
            emptyList()
        } else {
            (
                activeModelsForCounts
                    .asSequence()
                    .map { it.brand } +
                    EXTRA_SEARCH_KEYWORDS.asSequence()
                )
                .filter { it.startsWith(rawQuery, ignoreCase = true) }
                .distinct()
                .sortedBy { it.lowercase() }
                .take(10)
                .toList()
        }

        val filtered = modelItems
            .asSequence()
            .filter { if (includeDeleted) it.isDeleted else !it.isDeleted }
            .filter {
                query.isBlank() || it.modelName.contains(query, ignoreCase = true)
            }
            .filter {
                selectedBoxNumber == null || it.boxNumber == selectedBoxNumber
            }
            .filter {
                when (safeSelectedBrand) {
                    ALL_BRANDS -> true
                    USER_ADDED_FILTER -> it.isUserAdded
                    CURVED_GLASSES_FILTER -> it.type == ModelType.CURVED
                    MEMBRANE_FILTER -> it.type == ModelType.MEMBRANE
                    else -> it.brand == safeSelectedBrand
                }
            }
            .sortedWith(
                if (shouldSortByBrandThenModel) {
                    compareBy<ModelListItemUi> { it.brand.lowercase() }
                        .thenComparator(::compareModelNamesNaturally)
                        .thenBy { it.id }
                } else {
                    Comparator(::compareModelNamesNaturally).thenBy { it.id }
                }
            )
            .toList()

        val favorites = modelItems
            .asSequence()
            .filter { !it.isDeleted && it.isFavorite }
            .filter {
                query.isBlank() || it.modelName.contains(query, ignoreCase = true)
            }
            .filter {
                selectedBoxNumber == null || it.boxNumber == selectedBoxNumber
            }
            .filter {
                when (safeSelectedBrand) {
                    ALL_BRANDS -> true
                    USER_ADDED_FILTER -> it.isUserAdded
                    CURVED_GLASSES_FILTER -> it.type == ModelType.CURVED
                    MEMBRANE_FILTER -> it.type == ModelType.MEMBRANE
                    else -> it.brand == safeSelectedBrand
                }
            }
            .sortedWith(
                if (shouldSortByBrandThenModel) {
                    compareBy<ModelListItemUi> { it.brand.lowercase() }
                        .thenComparator(::compareModelNamesNaturally)
                        .thenBy { it.id }
                } else {
                    Comparator(::compareModelNamesNaturally).thenBy { it.id }
                }
            )
            .toList()

        val totalAvailableCount = modelItems.count { !it.isDeleted }

        val filterCounts = buildMap {
            put(ALL_BRANDS, activeModelsForCounts.size)
            put(
                USER_ADDED_FILTER,
                activeModelsForCounts.count { it.isUserAdded }
            )
            put(
                CURVED_GLASSES_FILTER,
                activeModelsForCounts.count { it.type == ModelType.CURVED }
            )
            put(
                MEMBRANE_FILTER,
                activeModelsForCounts.count { it.type == ModelType.MEMBRANE }
            )

            brandItems.forEach { brand ->
                if (
                    brand != ALL_BRANDS &&
                    brand != USER_ADDED_FILTER &&
                    brand != CURVED_GLASSES_FILTER &&
                    brand != MEMBRANE_FILTER
                ) {
                    put(
                        brand,
                        activeModelsForCounts.count { it.brand == brand }
                    )
                }
            }
        }

        MainUiState(
            isLinkDialogVisible = linkDialogVisible,
            generatedCode = codeGenerated,
            isGeneratingCode = generatingCode,
            linkError = linkErr,
            isLinked = linked,
            syncsRemaining = remainingSyncs,
            isPairingBusy = pairingBusy,
            searchQuery = rawQuery,
            searchSuggestions = searchSuggestions,
            selectedBrand = safeSelectedBrand,
            selectedBoxInput = selectedBoxRawInput,
            availableBoxes = availableBoxes,
            showDeleted = includeDeleted,
            totalAvailableModels = totalAvailableCount,
            filterCounts = filterCounts,
            favoriteModels = favorites,
            isFavoritesExpanded = favoritesExpanded,
            models = filtered,
            brands = brandItems,
            isAddDialogVisible = addDialogVisible,
            editTargetModel = editTarget,
            passwordTargetModel = passwordTarget,
            passwordMode = passwordMode,
            passwordError = passError,
            pendingExport = pendingExportRequest,
            isInternetEnabled = internetEnabled,
            useScreenLockForProtectedActions = useScreenLock,
            isSyncing = syncing,
            isSyncRequired = syncRequired,
            syncStatusMessage = syncMessage,
            accountEmail = signedInEmail,
            isAuthDialogVisible = authDialogVisible,
            isRegisterMode = registerMode,
            authError = authErr,
            isAddBrandDialogVisible = addBrandDialogVisible,
            addBrandError = addBrandErr,
            isManageBrandsDialogVisible = manageBrandsVisible,
            brandToEdit = editingBrand,
            editBrandError = editBrandErr,
            allBrandsList = knownBrandsList
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainUiState()
    )

    fun onSearchQueryChange(value: String) {
        searchQuery.value = value
    }

    fun onBrandSelected(brand: String) {
        selectedBrand.value = brand
    }

    fun onBoxFilterInputChange(value: String) {
        selectedBoxInput.value = value.filter { it.isDigit() }.take(4)
    }

    fun onShowDeletedChange(show: Boolean) {
        showDeleted.value = show
    }

    fun onFavoritesExpandedChange(expanded: Boolean) {
        isFavoritesExpanded.value = expanded
    }

    fun onToggleFavorite(model: ModelListItemUi) {
        if (model.isDeleted) return

        viewModelScope.launch {
            repository.setFavorite(model.id, !model.isFavorite)
        }
    }

    fun onAddModelClick() {
        if (isSyncRequired.value) {
            syncStatusMessage.value = "Sync required before further edits"
            return
        }
        if (!isPasswordTrusted()) {
            protectedAction.value = ProtectedActionRequest.Add
            passwordError.value = null
            return
        }
        editTargetModel.value = null
        protectedAction.value = null
        isAddDialogVisible.value = true
        passwordError.value = null
    }

    fun dismissAddDialog() {
        isAddDialogVisible.value = false
        editTargetModel.value = null
    }

    fun onDeleteRequested(model: ModelListItemUi) {
        if (isSyncRequired.value) {
            syncStatusMessage.value = "Sync required before further edits"
            return
        }
        if (model.isDeleted) return

        if (isPasswordTrusted()) {
            viewModelScope.launch {
                repository.softDeleteModel(model.id)
                markSyncRequired()
            }
            return
        }

        protectedAction.value = ProtectedActionRequest.Delete(model)
        passwordError.value = null
    }

    fun onEditRequested(model: ModelListItemUi) {
        if (isSyncRequired.value) {
            syncStatusMessage.value = "Sync required before further edits"
            return
        }
        if (model.isDeleted) return

        if (model.isUserAdded) {
            openEditDialog(model)
        } else {
            if (isPasswordTrusted()) {
                openEditDialog(model)
                return
            }
            protectedAction.value = ProtectedActionRequest.Edit(model)
            passwordError.value = null
        }
    }

    fun onPermanentDeleteRequested(model: ModelListItemUi) {
        if (isSyncRequired.value) {
            syncStatusMessage.value = "Sync required before further edits"
            return
        }
        if (!model.isDeleted) return

        if (isPasswordTrusted()) {
            viewModelScope.launch {
                repository.permanentlyDeleteModel(model.id)
                markSyncRequired()
            }
            return
        }

        protectedAction.value = ProtectedActionRequest.PermanentDelete(model)
        passwordError.value = null
    }

    fun confirmProtectedAction(password: String) {
        val action = protectedAction.value ?: return

        if (password == DELETE_PASSWORD) {
            lastPasswordVerifiedAtMs = System.currentTimeMillis()
            when (action) {
                is ProtectedActionRequest.Add -> {
                    editTargetModel.value = null
                    isAddDialogVisible.value = true
                }

                is ProtectedActionRequest.Delete -> {
                    viewModelScope.launch {
                        repository.softDeleteModel(action.model.id)
                        markSyncRequired()
                    }
                }

                is ProtectedActionRequest.Edit -> {
                    openEditDialog(action.model)
                }

                is ProtectedActionRequest.PermanentDelete -> {
                    viewModelScope.launch {
                        repository.permanentlyDeleteModel(action.model.id)
                        markSyncRequired()
                    }
                }
            }

            protectedAction.value = null
            passwordError.value = null
        } else {
            passwordError.value = "Incorrect password"
        }
    }

    fun dismissPasswordDialog() {
        protectedAction.value = null
        passwordError.value = null
    }

    fun restoreModel(modelId: Long) {
        if (isSyncRequired.value) {
            syncStatusMessage.value = "Sync required before further edits"
            return
        }
        viewModelScope.launch {
            repository.restoreModel(modelId)
            markSyncRequired()
        }
    }

    fun markSyncRequired() {
        isSyncRequired.value = true
        syncStatusMessage.value = "Sync required before further edits"
    }

    fun onExportCsvRequested() {
        requestExport(format = ExportFormat.CSV)
    }

    fun onExportPdfRequested() {
        requestExport(format = ExportFormat.PDF)
    }

    fun onPendingExportHandled() {
        pendingExport.value = null
    }

    fun onInternetEnabledChange(enabled: Boolean) {
        repository.setInternetEnabled(enabled)
        isInternetEnabled.value = enabled
        if (!enabled) {
            syncStatusMessage.value = "Internet sync disabled"
        }
    }

    fun openSignInDialog(registerMode: Boolean) {
        isRegisterMode.value = registerMode
        authError.value = null
        isAuthDialogVisible.value = true
    }

    fun setUseScreenLockForProtectedActions(enabled: Boolean) {
        repository.setUseScreenLockEnabled(enabled)
        useScreenLockForProtectedActions.value = enabled
    }

    fun confirmProtectedActionWithSystemAuth() {
        val action = protectedAction.value ?: return
        when (action) {
            is ProtectedActionRequest.Add -> {
                editTargetModel.value = null
                isAddDialogVisible.value = true
            }
            is ProtectedActionRequest.Delete -> {
                viewModelScope.launch {
                    repository.softDeleteModel(action.model.id)
                    markSyncRequired()
                }
            }
            is ProtectedActionRequest.Edit -> openEditDialog(action.model)
            is ProtectedActionRequest.PermanentDelete -> {
                viewModelScope.launch {
                    repository.permanentlyDeleteModel(action.model.id)
                    markSyncRequired()
                }
            }
        }
        protectedAction.value = null
        passwordError.value = null
    }

    fun dismissAuthDialog() {
        isAuthDialogVisible.value = false
        authError.value = null
    }

    fun openAddBrandDialog() {
        addBrandError.value = null
        isAddBrandDialogVisible.value = true
    }

    fun dismissAddBrandDialog() {
        addBrandError.value = null
        isAddBrandDialogVisible.value = false
    }

    fun createBrand(name: String) {
        viewModelScope.launch {
            when (val result = brandRepository.addBrand(name)) {
                is AddBrandResult.Success -> {
                    addBrandError.value = null
                    isAddBrandDialogVisible.value = false
                    syncStatusMessage.value = "Brand '${result.brandName}' added"
                }
                is AddBrandResult.AlreadyExists -> {
                    addBrandError.value = "Brand '${result.brandName}' already exists"
                }
                is AddBrandResult.Invalid -> {
                    addBrandError.value = result.reason
                }
            }
        }
    }

    fun openManageBrandsDialog() {
        isManageBrandsDialogVisible.value = true
        brandToEdit.value = null
        editBrandError.value = null
    }

    fun dismissManageBrandsDialog() {
        isManageBrandsDialogVisible.value = false
        brandToEdit.value = null
        editBrandError.value = null
    }

    fun openEditBrandDialog(brandName: String) {
        brandToEdit.value = brandName
        editBrandError.value = null
    }

    fun dismissEditBrandDialog() {
        brandToEdit.value = null
        editBrandError.value = null
    }

    fun updateBrand(oldName: String, newName: String) {
        viewModelScope.launch {
            when (val result = brandRepository.updateBrand(oldName, newName)) {
                is UpdateBrandResult.Success -> {
                    editBrandError.value = null
                    brandToEdit.value = null
                    val msg = if (result.updatedModelsCount > 0) {
                        "Brand renamed to '${result.newBrandName}' (${result.updatedModelsCount} models updated)"
                    } else {
                        "Brand renamed to '${result.newBrandName}'"
                    }
                    syncStatusMessage.value = msg
                    markSyncRequired()
                }
                is UpdateBrandResult.NoChange -> {
                    brandToEdit.value = null
                    editBrandError.value = null
                }
                is UpdateBrandResult.AlreadyExists -> {
                    editBrandError.value = "Brand '${result.brandName}' already exists"
                }
                is UpdateBrandResult.Invalid -> {
                    editBrandError.value = result.reason
                }
            }
        }
    }

    fun submitAuth(email: String, password: String, register: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            authError.value = "Email and password are required"
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                if (register) repository.registerWithEmailPassword(email, password)
                else repository.signInWithEmailPassword(email, password)
            }

            if (result.isSuccess) {
                accountEmail.value = repository.currentUserEmail()
                isAuthDialogVisible.value = false
                authError.value = null
                syncStatusMessage.value = "Signed in: ${accountEmail.value ?: email}"
            } else {
                authError.value = result.exceptionOrNull()?.message ?: "Authentication failed"
            }
        }
    }

    fun signOutAccount() {
        repository.signOut()
        accountEmail.value = null
        syncStatusMessage.value = "Signed out"
    }

    fun openLinkDialog() {
        isLinkDialogVisible.value = true
        generatedCode.value = null
        linkError.value = null
        isPairingBusy.value = false
        refreshPairingState()
    }

    fun dismissLinkDialog() {
        isLinkDialogVisible.value = false
    }

    fun generateLinkCode() {
        if (isGeneratingCode.value) return
        if (!repository.isSignedInWithSharedAccount()) {
            linkError.value = "Sign in to generate a link code"
            return
        }
        viewModelScope.launch {
            isGeneratingCode.value = true
            linkError.value = null
            generatedCode.value = runCatching {
                repository.generateLinkCode()
            }.onFailure {
                linkError.value = it.message ?: "Failed to generate code"
            }.getOrNull()
            isGeneratingCode.value = false
        }
    }

    fun enterLinkCode(code: String) {
        if (isPairingBusy.value) return
        if (!repository.isSignedInWithSharedAccount()) {
            linkError.value = "Sign in to link another device"
            return
        }
        if (code.isBlank()) {
            linkError.value = "Enter the 6-character code"
            return
        }

        viewModelScope.launch {
            isPairingBusy.value = true
            linkError.value = null
            runCatching {
                val pairing = repository.consumeLinkCode(code)
                repository.copyOwnerModelsIntoLocal(pairing.ownerUid)
                caseRepository.copyOwnerCaseModelsIntoLocal(pairing.ownerUid)
                isLinked.value = true
                syncsRemaining.value = pairing.syncsRemaining
                syncStatusMessage.value = "Devices linked! Shared syncs remaining: ${pairing.syncsRemaining}"
            }.onFailure {
                linkError.value = it.message ?: "Failed to link device"
            }
            isPairingBusy.value = false
        }
    }

    fun refreshPairingState() {
        viewModelScope.launch {
            val pairing = repository.getCurrentPairing()
            if (pairing != null) {
                isLinked.value = pairing.active
                syncsRemaining.value = pairing.syncsRemaining
            } else {
                isLinked.value = false
                syncsRemaining.value = null
            }
        }
    }

    fun consumeSharedSyncSlot() {
        viewModelScope.launch {
            val pairing = repository.consumePairingSyncSlot()
            if (pairing != null) {
                syncsRemaining.value = pairing.syncsRemaining
                isLinked.value = pairing.active
            } else {
                syncsRemaining.value = 0
                isLinked.value = false
            }
        }
    }

    fun syncNow() {
        if (isSyncing.value) return
        if (!isInternetEnabled.value) {
            repository.setInternetEnabled(true)
            isInternetEnabled.value = true
        }
        if (!repository.isSignedInWithSharedAccount()) {
            syncStatusMessage.value = "Sign in on both devices to sync across devices"
            return
        }

        viewModelScope.launch {
            isSyncing.value = true
            syncStatusMessage.value = "Syncing..."
            val result = runCatching {
                withTimeout(30_000) {
                    val modelReport = repository.syncNowWithReport().getOrThrow()
                    val caseReport = caseRepository.syncNowWithReport().getOrThrow()
                    "Sync report: user=${modelReport.email ?: "unknown"}, uid=${modelReport.uid.take(8)}..., " +
                        "models[pending=${modelReport.pendingCount}, failedPush=${modelReport.failedPushCount}, pulled=${modelReport.pulledCount}, applied=${modelReport.appliedCount}], " +
                        "cases[pending=${caseReport.pendingCount}, pulled=${caseReport.pulledCount}, applied=${caseReport.appliedCount}]"
                }
            }
            syncStatusMessage.value = if (result.isSuccess) {
                isSyncRequired.value = false
                result.getOrNull()
            } else {
                "Sync failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            isSyncing.value = false
        }
    }

    fun triggerMandatorySyncInBackground() {
        if (isSyncing.value) {
            isSyncRequired.value = false
            return
        }

        // Dismiss mandatory prompt immediately so user can continue editing.
        isSyncRequired.value = false

        if (!isInternetEnabled.value) {
            repository.setInternetEnabled(true)
            isInternetEnabled.value = true
        }

        if (!repository.isSignedInWithSharedAccount()) {
            syncStatusMessage.value = "Sign in on both devices to sync across devices"
            isSyncRequired.value = true
            return
        }

        viewModelScope.launch {
            isSyncing.value = true
            syncStatusMessage.value = "Syncing in background..."
            val result = runCatching {
                withTimeout(30_000) {
                    val modelReport = repository.syncNowWithReport().getOrThrow()
                    val caseReport = caseRepository.syncNowWithReport().getOrThrow()
                    "Sync report: user=${modelReport.email ?: "unknown"}, uid=${modelReport.uid.take(8)}..., " +
                        "models[pending=${modelReport.pendingCount}, failedPush=${modelReport.failedPushCount}, pulled=${modelReport.pulledCount}, applied=${modelReport.appliedCount}], " +
                        "cases[pending=${caseReport.pendingCount}, pulled=${caseReport.pulledCount}, applied=${caseReport.appliedCount}]"
                }
            }

            if (result.isSuccess) {
                syncStatusMessage.value = result.getOrNull()
            } else {
                syncStatusMessage.value = "Sync failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                isSyncRequired.value = true
            }
            isSyncing.value = false
        }
    }

    private fun requestExport(format: ExportFormat) {
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.Default) {
                    val allEntities = repository.getAllModelsSnapshot()
                    val activeEntities = repository.getActiveModelsSnapshot()

                    val payload = buildExportPayload(
                        allEntities = allEntities,
                        activeEntities = activeEntities
                    )

                    when (format) {
                        ExportFormat.CSV -> buildExportCsvBytes(payload)
                        ExportFormat.PDF -> buildExportPdfBytes(payload)
                    }
                }
            }.getOrNull() ?: return@launch

            pendingExport.value = PendingExport(
                requestId = ++exportRequestCounter,
                format = format,
                suggestedFileName = buildSuggestedExportFileName(format),
                contentBytes = bytes
            )
        }
    }

    private fun buildExportPayload(
        allEntities: List<ModelEntity>,
        activeEntities: List<ModelEntity>
    ): ExportPayload {
        val allItems = allEntities.map { it.toUi() }
        val activeItems = activeEntities.map { it.toUi() }

        val dynamicBrands = activeItems
            .map { it.brand }
            .filterNot {
                it == ALL_BRANDS ||
                    it == USER_ADDED_FILTER ||
                    it == CURVED_GLASSES_FILTER ||
                    it == MEMBRANE_FILTER
            }
            .distinct()
            .sorted()

        val filterRows = buildList {
            add(ExportFilterRow(ALL_BRANDS, activeItems.size))
            add(ExportFilterRow(USER_ADDED_FILTER, activeItems.count { it.isUserAdded }))
            add(ExportFilterRow(CURVED_GLASSES_FILTER, activeItems.count { it.type == ModelType.CURVED }))
            add(ExportFilterRow(MEMBRANE_FILTER, activeItems.count { it.type == ModelType.MEMBRANE }))

            dynamicBrands.forEach { brand ->
                add(
                    ExportFilterRow(
                        filterName = brand,
                        count = activeItems.count { it.brand == brand }
                    )
                )
            }
        }

        val modelRows = allItems
            .sortedWith(
                compareBy<ModelListItemUi> { it.brand.lowercase() }
                    .thenComparator(::compareModelNamesNaturally)
                    .thenBy { it.id }
            )
            .map { item ->
                ExportModelRow(
                    modelName = item.modelName,
                    brand = item.brand,
                    boxNumber = item.boxNumber,
                    type = item.type.displayName,
                    isUserAdded = item.isUserAdded,
                    isDeleted = item.isDeleted,
                    isFavorite = item.isFavorite,
                    additionalCompatible = item.additionalCompatible
                )
            }

        return ExportPayload(
            exportedAtMillis = System.currentTimeMillis(),
            filters = filterRows,
            models = modelRows
        )
    }

    private fun buildSuggestedExportFileName(format: ExportFormat): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "tempglass_export_${timestamp}.${format.fileExtension}"
    }

    private fun openEditDialog(model: ModelListItemUi) {
        editTargetModel.value = model
        isAddDialogVisible.value = true
    }

    private fun isPasswordTrusted(): Boolean {
        val now = System.currentTimeMillis()
        return lastPasswordVerifiedAtMs > 0L && (now - lastPasswordVerifiedAtMs) <= PASSWORD_TRUST_WINDOW_MS
    }

    private fun compareModelNamesNaturally(a: ModelListItemUi, b: ModelListItemUi): Int {
        return compareNaturalStrings(a.modelName, b.modelName)
    }

    private fun compareNaturalStrings(leftRaw: String, rightRaw: String): Int {
        val left = leftRaw.lowercase()
        val right = rightRaw.lowercase()

        var i = 0
        var j = 0

        while (i < left.length && j < right.length) {
            val leftChar = left[i]
            val rightChar = right[j]
            val leftIsDigit = leftChar.isDigit()
            val rightIsDigit = rightChar.isDigit()

            if (leftIsDigit && rightIsDigit) {
                val leftStart = i
                val rightStart = j

                while (i < left.length && left[i].isDigit()) i++
                while (j < right.length && right[j].isDigit()) j++

                val leftNumberRaw = left.substring(leftStart, i)
                val rightNumberRaw = right.substring(rightStart, j)

                val leftNumber = leftNumberRaw.trimStart('0').ifEmpty { "0" }
                val rightNumber = rightNumberRaw.trimStart('0').ifEmpty { "0" }

                if (leftNumber.length != rightNumber.length) {
                    return leftNumber.length.compareTo(rightNumber.length)
                }

                val numberCompare = leftNumber.compareTo(rightNumber)
                if (numberCompare != 0) return numberCompare

                if (leftNumberRaw.length != rightNumberRaw.length) {
                    return leftNumberRaw.length.compareTo(rightNumberRaw.length)
                }
            } else {
                if (leftChar != rightChar) {
                    return leftChar.compareTo(rightChar)
                }
                i++
                j++
            }
        }

        return (left.length - i).compareTo(right.length - j)
    }

    private fun ModelEntity.toUi(): ModelListItemUi {
        return ModelListItemUi(
            id = id,
            modelName = modelName,
            boxNumber = boxNumber,
            additionalCompatible = CompatibleModelsCodec.decode(additionalCompatible),
            type = ModelType.fromStorage(type),
            isUserAdded = isUserAdded,
            isDeleted = isDeleted,
            isFavorite = isFavorite,
            brand = BrandDetector.detectBrand(modelName)
        )
    }

    class Factory(
        private val repository: ModelRepository,
        private val caseRepository: CaseRepository,
        private val brandRepository: BrandRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, caseRepository, brandRepository) as T
        }
    }
}
