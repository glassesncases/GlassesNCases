package com.neha.tempglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neha.tempglass.data.CaseModelEntity
import com.neha.tempglass.export.ExportFilterRow
import com.neha.tempglass.export.ExportFormat
import com.neha.tempglass.export.ExportModelRow
import com.neha.tempglass.export.ExportPayload
import com.neha.tempglass.export.buildExportCsvBytes
import com.neha.tempglass.export.buildExportPdfBytes
import com.neha.tempglass.repository.AddCaseModelResult
import com.neha.tempglass.repository.CaseRepository
import com.neha.tempglass.repository.SellCartResult
import com.neha.tempglass.repository.UpdateCaseModelResult
import com.neha.tempglass.util.BrandDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALL_CASE_BRANDS = "All"
private const val CASE_DELETE_PASSWORD = "DigitalGarage"
private const val CASE_PASSWORD_TRUST_WINDOW_MS = 10 * 60 * 1000L
private val REQUIRED_CASE_BRANDS = listOf("Infinix", "Itel")
private val EXTRA_CASE_SEARCH_KEYWORDS = listOf(
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

enum class CaseProtectedActionMode {
    ADD,
    EDIT,
    DELETE,
    PERMANENT_DELETE
}

private sealed interface CaseProtectedActionRequest {
    data object Add : CaseProtectedActionRequest
    data class Edit(val model: CaseModelListItemUi) : CaseProtectedActionRequest
    data class Delete(val model: CaseModelListItemUi) : CaseProtectedActionRequest
    data class PermanentDelete(val model: CaseModelListItemUi) : CaseProtectedActionRequest
}

data class CaseModelListItemUi(
    val id: Long,
    val modelName: String,
    val brand: String,
    val quantity: Int,
    val isUserAdded: Boolean,
    val isDeleted: Boolean,
    val brandSerialNumber: Int = 0
)

data class CaseCartItemUi(
    val modelId: Long,
    val modelName: String,
    val quantityInCart: Int,
    val availableQuantity: Int
)

data class AddCaseModelUiState(
    val isVisible: Boolean = false,
    val editingModelId: Long? = null,
    val brandInput: String = "",
    val modelInput: String = "",
    val quantityInput: String = "1",
    val selectedSerialNumber: Int? = null,
    val availableGapSerialNumbers: List<Int> = emptyList(),
    val nextSerialNumber: Int = 1,
    val brandSuggestions: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

data class CasesUiState(
    val searchQuery: String = "",
    val searchSuggestions: List<String> = emptyList(),
    val selectedBrand: String = ALL_CASE_BRANDS,
    val showStockOutOnly: Boolean = false,
    val showDeleted: Boolean = false,
    val totalModels: Int = 0,
    val totalQuantity: Int = 0,
    val models: List<CaseModelListItemUi> = emptyList(),
    val brands: List<String> = listOf(ALL_CASE_BRANDS),
    val cartItems: List<CaseCartItemUi> = emptyList(),
    val cartItemCount: Int = 0,
    val cartTotalUnits: Int = 0,
    val sellStatusMessage: String? = null,
    val pendingExport: PendingExport? = null,
    val passwordTargetModel: CaseModelListItemUi? = null,
    val passwordMode: CaseProtectedActionMode? = null,
    val passwordError: String? = null,
    val addCaseModelUiState: AddCaseModelUiState = AddCaseModelUiState()
)

class CasesViewModel(
    private val repository: CaseRepository
) : ViewModel() {

    private var lastPasswordVerifiedAtMs: Long = 0L

    private val searchQuery = MutableStateFlow("")
    private val selectedBrand = MutableStateFlow(ALL_CASE_BRANDS)
    private val showStockOutOnly = MutableStateFlow(false)
    private val showDeleted = MutableStateFlow(false)
    private val cartQuantities = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val sellStatusMessage = MutableStateFlow<String?>(null)

    private val isAddDialogVisible = MutableStateFlow(false)
    private val editingModelId = MutableStateFlow<Long?>(null)
    private val addBrandInput = MutableStateFlow("")
    private val addModelInput = MutableStateFlow("")
    private val addQuantityInput = MutableStateFlow("1")
    private val addErrorMessage = MutableStateFlow<String?>(null)
    private val isAddSaving = MutableStateFlow(false)
    private val selectedSerialNumber = MutableStateFlow<Int?>(null)
    private val pendingExport = MutableStateFlow<PendingExport?>(null)
    private val protectedAction = MutableStateFlow<CaseProtectedActionRequest?>(null)
    private val passwordError = MutableStateFlow<String?>(null)
    private var exportRequestCounter: Long = 0

    private val allModels = repository.observeAllCaseModels().map { entities ->
        entities.map { it.toUi() }
    }

    private val brands = allModels.map { modelItems ->
        val dynamicBrands = modelItems
            .asSequence()
            .filter { !it.isDeleted }
            .map { it.brand }
            .plus(REQUIRED_CASE_BRANDS.asSequence())
            .distinct()
            .sorted()
            .toList()

        listOf(ALL_CASE_BRANDS) + dynamicBrands
    }

    private data class CasesFilterState(
        val rawQuery: String,
        val queryDebounced: String,
        val selectedBrand: String,
        val showStockOutOnly: Boolean,
        val showDeleted: Boolean,
        val cartQuantities: Map<Long, Int>,
        val sellStatusMessage: String?
    )

    private data class AddCaseFormState(
        val isVisible: Boolean,
        val editingModelId: Long?,
        val brandInput: String,
        val modelInput: String,
        val quantityInput: String,
        val errorMessage: String?,
        val isSaving: Boolean
    )

    @OptIn(FlowPreview::class)
    private val queryState = combine(
        searchQuery,
        searchQuery.debounce(300)
    ) { rawQuery, queryDebounced ->
        rawQuery to queryDebounced
    }

    @OptIn(FlowPreview::class)
    private val filters = combine(
        queryState,
        selectedBrand,
        showStockOutOnly,
        showDeleted,
        cartQuantities,
        sellStatusMessage
    ) { data ->
        CasesFilterState(
            rawQuery = (data[0] as Pair<String, String>).first,
            queryDebounced = (data[0] as Pair<String, String>).second,
            selectedBrand = data[1] as String,
            showStockOutOnly = data[2] as Boolean,
            showDeleted = data[3] as Boolean,
            cartQuantities = data[4] as Map<Long, Int>,
            sellStatusMessage = data[5] as String?
        )
    }

    private val addCaseFormBaseState = combine(
        isAddDialogVisible,
        editingModelId,
        addBrandInput,
        addModelInput,
        addQuantityInput,
        addErrorMessage,
        selectedSerialNumber
    ) { data ->
        AddCaseFormState(
            isVisible = data[0] as Boolean,
            editingModelId = data[1] as Long?,
            brandInput = data[2] as String,
            modelInput = data[3] as String,
            quantityInput = data[4] as String,
            errorMessage = data[5] as String?,
            isSaving = false
        ) to (data[6] as Int?)
    }

    private val addCaseFormState = combine(
        addCaseFormBaseState,
        isAddSaving
    ) { payload, isSavingValue ->
        payload.first.copy(isSaving = isSavingValue) to payload.second
    }

    private data class CasesBaseUiContext(
        val modelItems: List<CaseModelListItemUi>,
        val brandItems: List<String>,
        val filterState: CasesFilterState,
        val addFormPayload: Pair<AddCaseFormState, Int?>,
        val pendingExportRequest: PendingExport?
    )

    private val baseUiContext = combine(
        allModels,
        brands,
        filters,
        addCaseFormState,
        pendingExport
    ) { modelItems, brandItems, filterState, addFormPayload, pendingExportRequest ->
        CasesBaseUiContext(
            modelItems = modelItems,
            brandItems = brandItems,
            filterState = filterState,
            addFormPayload = addFormPayload,
            pendingExportRequest = pendingExportRequest
        )
    }

    val uiState: StateFlow<CasesUiState> = combine(
        baseUiContext,
        protectedAction,
        passwordError
    ) { base, action, passError ->
        val modelItems = base.modelItems
        val brandItems = base.brandItems
        val filterState = base.filterState
        val addFormPayload = base.addFormPayload
        val pendingExportRequest = base.pendingExportRequest
        val addFormState = addFormPayload.first
        val selectedSerial = addFormPayload.second
        val query = filterState.queryDebounced.trim()
        val safeSelectedBrand =
            filterState.selectedBrand.takeIf { it in brandItems } ?: ALL_CASE_BRANDS

        val activeModels = modelItems
            .asSequence()
            .filter { !it.isDeleted }
            .toList()

        val filteredSource = if (filterState.showDeleted) {
            modelItems.asSequence().filter { it.isDeleted }
        } else {
            modelItems.asSequence().filter { !it.isDeleted }
        }

        val activeById = activeModels.associateBy { it.id }

        val filteredBase = filteredSource
            .filter { query.isBlank() || it.modelName.contains(query, ignoreCase = true) }
            .filter { filterState.showDeleted || !filterState.showStockOutOnly || it.quantity == 0 }
            .filter { safeSelectedBrand == ALL_CASE_BRANDS || it.brand == safeSelectedBrand }
            .sortedWith(
                compareBy<CaseModelListItemUi> { it.brand.lowercase() }
                    .thenBy { if (it.brandSerialNumber > 0) it.brandSerialNumber else Int.MAX_VALUE }
                    .thenComparator(::compareModelNamesNaturally)
                    .thenBy { it.id }
            )
            .toList()

        val filtered = filteredBase

        val searchSuggestions = if (filterState.rawQuery.isBlank()) {
            emptyList()
        } else {
            (
                activeModels
                    .asSequence()
                    .map { it.brand } +
                    EXTRA_CASE_SEARCH_KEYWORDS.asSequence()
                )
                .filter { it.startsWith(filterState.rawQuery, ignoreCase = true) }
                .distinct()
                .sortedBy { it.lowercase() }
                .take(10)
                .toList()
        }

        val cartItems = filterState.cartQuantities
            .asSequence()
            .mapNotNull { (modelId, qtyInCart) ->
                val model = activeById[modelId] ?: return@mapNotNull null
                if (qtyInCart <= 0) return@mapNotNull null

                CaseCartItemUi(
                    modelId = model.id,
                    modelName = model.modelName,
                    quantityInCart = qtyInCart,
                    availableQuantity = model.quantity
                )
            }
            .sortedBy { it.modelName.lowercase() }
            .toList()

        val brandQuery = addFormState.brandInput.trim()
        val addBrandSuggestions = brandItems
            .filter { it != ALL_CASE_BRANDS }
            .filter { brandQuery.isBlank() || it.startsWith(brandQuery, ignoreCase = true) }

        val selectedBrandForSerial = addFormState.brandInput.trim()
        val serialsForBrand = if (selectedBrandForSerial.isBlank()) {
            emptyList()
        } else {
            modelItems
                .asSequence()
                .filter { !it.isDeleted }
                .filter { BrandDetector.detectBrand(it.modelName) == selectedBrandForSerial }
                .map { it.brandSerialNumber }
                .filter { it > 0 }
                .distinct()
                .sorted()
                .toList()
        }
        val nextSerial = (serialsForBrand.maxOrNull() ?: 0) + 1
        val gapSerials = if (serialsForBrand.isEmpty()) {
            emptyList()
        } else {
            val occupied = serialsForBrand.toSet()
            (1..serialsForBrand.last()).filter { it !in occupied }
        }
        val serialOptions = (gapSerials + nextSerial).distinct().sorted()
        val finalSelectedSerial = selectedSerial?.takeIf { it in serialOptions }
            ?: serialOptions.firstOrNull()

        val passwordTarget = when (action) {
            is CaseProtectedActionRequest.Add -> null
            is CaseProtectedActionRequest.Edit -> action.model
            is CaseProtectedActionRequest.Delete -> action.model
            is CaseProtectedActionRequest.PermanentDelete -> action.model
            null -> null
        }

        val passwordMode = when (action) {
            is CaseProtectedActionRequest.Add -> CaseProtectedActionMode.ADD
            is CaseProtectedActionRequest.Edit -> CaseProtectedActionMode.EDIT
            is CaseProtectedActionRequest.Delete -> CaseProtectedActionMode.DELETE
            is CaseProtectedActionRequest.PermanentDelete -> CaseProtectedActionMode.PERMANENT_DELETE
            null -> null
        }

        CasesUiState(
            searchQuery = filterState.rawQuery,
            searchSuggestions = searchSuggestions,
            selectedBrand = safeSelectedBrand,
            showStockOutOnly = filterState.showStockOutOnly,
            showDeleted = filterState.showDeleted,
            totalModels = activeModels.size,
            totalQuantity = activeModels.sumOf { it.quantity },
            models = filtered,
            brands = brandItems,
            cartItems = cartItems,
            cartItemCount = cartItems.size,
            cartTotalUnits = cartItems.sumOf { it.quantityInCart },
            sellStatusMessage = filterState.sellStatusMessage,
            pendingExport = pendingExportRequest,
            passwordTargetModel = passwordTarget,
            passwordMode = passwordMode,
            passwordError = passError,
            addCaseModelUiState = AddCaseModelUiState(
                isVisible = addFormState.isVisible,
                editingModelId = addFormState.editingModelId,
                brandInput = addFormState.brandInput,
                modelInput = addFormState.modelInput,
                quantityInput = addFormState.quantityInput,
                selectedSerialNumber = finalSelectedSerial,
                availableGapSerialNumbers = gapSerials,
                nextSerialNumber = nextSerial,
                brandSuggestions = addBrandSuggestions,
                errorMessage = addFormState.errorMessage,
                isSaving = addFormState.isSaving
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CasesUiState()
    )

    fun onSearchQueryChange(value: String) {
        searchQuery.value = value
    }

    fun onBrandSelected(brand: String) {
        selectedBrand.value = brand
        showStockOutOnly.value = false
        showDeleted.value = false
    }

    fun onShowStockOutOnlyChange(showOnly: Boolean) {
        showStockOutOnly.value = showOnly
        if (showOnly) {
            selectedBrand.value = ALL_CASE_BRANDS
            showDeleted.value = false
        }
    }

    fun onShowDeletedChange(show: Boolean) {
        showDeleted.value = show
        if (show) {
            showStockOutOnly.value = false
        }
    }

    fun openAddCaseModelDialog() {
        if (!isPasswordTrusted()) {
            protectedAction.value = CaseProtectedActionRequest.Add
            passwordError.value = null
            return
        }
        isAddDialogVisible.value = true
        editingModelId.value = null
        addBrandInput.value = ""
        addModelInput.value = ""
        addQuantityInput.value = "1"
        selectedSerialNumber.value = null
        addErrorMessage.value = null
        isAddSaving.value = false
    }

    fun dismissAddCaseModelDialog() {
        isAddDialogVisible.value = false
        editingModelId.value = null
        addErrorMessage.value = null
        isAddSaving.value = false
    }

    fun onSelectedSerialNumberChange(value: Int) {
        selectedSerialNumber.value = value
        addErrorMessage.value = null
    }

    fun onAddCaseBrandInputChange(value: String) {
        addBrandInput.value = value
        addErrorMessage.value = null
    }

    fun onAddCaseModelInputChange(value: String) {
        addModelInput.value = value
        addErrorMessage.value = null
    }

    fun onAddCaseQuantityInputChange(value: String) {
        addQuantityInput.value = value.filter { it.isDigit() }.take(4)
        addErrorMessage.value = null
    }

    fun saveAddCaseModel() {
        if (isAddSaving.value) return

        val brand = addBrandInput.value.trim()
        val model = addModelInput.value.trim()
        val quantity = addQuantityInput.value.trim().toIntOrNull()

        when {
            brand.isBlank() -> {
                addErrorMessage.value = "Please select or enter a brand"
                return
            }

            model.isBlank() -> {
                addErrorMessage.value = "Model name is required"
                return
            }

            quantity == null -> {
                addErrorMessage.value = "Enter a valid quantity"
                return
            }
        }

        val fullModelName = "$brand $model".trim()

        val editId = editingModelId.value

        viewModelScope.launch {
            isAddSaving.value = true
            addErrorMessage.value = null

            if (editId == null) {
                when (repository.addCaseModel(fullModelName, quantity, selectedSerialNumber.value)) {
                    AddCaseModelResult.Success -> {
                        isAddDialogVisible.value = false
                        editingModelId.value = null
                        addBrandInput.value = ""
                        addModelInput.value = ""
                        addQuantityInput.value = "1"
                        selectedSerialNumber.value = null
                    }

                    AddCaseModelResult.DuplicateModelName -> {
                        addErrorMessage.value = "Model already exists"
                    }
                }
            } else {
                when (repository.updateCaseModel(editId, fullModelName, quantity)) {
                    UpdateCaseModelResult.Success -> {
                        isAddDialogVisible.value = false
                        editingModelId.value = null
                        addBrandInput.value = ""
                        addModelInput.value = ""
                        addQuantityInput.value = "1"
                        selectedSerialNumber.value = null
                    }

                    UpdateCaseModelResult.NotFound -> {
                        addErrorMessage.value = "Model no longer exists"
                    }

                    UpdateCaseModelResult.DuplicateModelName -> {
                        addErrorMessage.value = "Model already exists"
                    }
                }
            }

            isAddSaving.value = false
        }
    }

    fun increaseQuantity(modelId: Long) {
        viewModelScope.launch {
            repository.adjustQuantity(modelId, 1)
        }
    }

    fun decreaseQuantity(modelId: Long) {
        viewModelScope.launch {
            repository.adjustQuantity(modelId, -1)
        }
    }

    fun addToCart(modelId: Long) {
        val availableQuantity = getAvailableQuantity(modelId) ?: return
        if (availableQuantity <= 0) return

        cartQuantities.update { current ->
            val existing = current[modelId] ?: 0
            val next = (existing + 1).coerceAtMost(availableQuantity)
            current + (modelId to next)
        }
    }

    fun increaseCartQuantity(modelId: Long) {
        val availableQuantity = getAvailableQuantity(modelId) ?: return
        if (availableQuantity <= 0) return

        cartQuantities.update { current ->
            val existing = current[modelId] ?: return@update current
            val next = (existing + 1).coerceAtMost(availableQuantity)
            current + (modelId to next)
        }
    }

    fun decreaseCartQuantity(modelId: Long) {
        cartQuantities.update { current ->
            val existing = current[modelId] ?: return@update current
            val next = existing - 1

            if (next <= 0) {
                current - modelId
            } else {
                current + (modelId to next)
            }
        }
    }

    fun removeFromCart(modelId: Long) {
        cartQuantities.update { it - modelId }
    }

    fun sellCart() {
        val cart = cartQuantities.value.filterValues { it > 0 }
        if (cart.isEmpty()) {
            sellStatusMessage.value = "Cart is empty"
            return
        }

        viewModelScope.launch {
            when (val result = repository.sellCart(cart)) {
                SellCartResult.Success -> {
                    cartQuantities.value = emptyMap()
                    sellStatusMessage.value = "Sale completed"
                }

                is SellCartResult.InsufficientStock -> {
                    if (result.modelIds.isNotEmpty()) {
                        cartQuantities.update { it - result.modelIds.toSet() }

                        val failedNames = uiState.value.cartItems
                            .filter { it.modelId in result.modelIds }
                            .map { it.modelName }

                        sellStatusMessage.value = if (failedNames.isNotEmpty()) {
                            "Insufficient stock: ${failedNames.joinToString()}"
                        } else {
                            "Insufficient stock for selected item(s)"
                        }
                    } else {
                        sellStatusMessage.value = "Could not complete sale due to stock mismatch"
                    }
                }
            }
        }
    }

    fun clearSellStatusMessage() {
        sellStatusMessage.value = null
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

    fun onEditRequested(model: CaseModelListItemUi) {
        if (model.isDeleted) return

        if (!isPasswordTrusted()) {
            protectedAction.value = CaseProtectedActionRequest.Edit(model)
            passwordError.value = null
            return
        }
        openEditDialogInternal(model)
    }

    fun onDeleteRequested(model: CaseModelListItemUi) {
        if (model.isDeleted) return
        if (!isPasswordTrusted()) {
            protectedAction.value = CaseProtectedActionRequest.Delete(model)
            passwordError.value = null
            return
        }
        viewModelScope.launch {
            repository.softDeleteCaseModel(model.id)
        }
    }

    fun onRestoreRequested(model: CaseModelListItemUi) {
        if (!model.isDeleted) return
        viewModelScope.launch {
            repository.restoreCaseModel(model.id)
        }
    }

    fun onPermanentDeleteRequested(model: CaseModelListItemUi) {
        if (!model.isDeleted) return
        if (!isPasswordTrusted()) {
            protectedAction.value = CaseProtectedActionRequest.PermanentDelete(model)
            passwordError.value = null
            return
        }
        viewModelScope.launch {
            repository.permanentlyDeleteCaseModel(model.id)
        }
    }

    fun confirmProtectedAction(password: String) {
        val action = protectedAction.value ?: return

        if (password != CASE_DELETE_PASSWORD) {
            passwordError.value = "Incorrect password"
            return
        }

        lastPasswordVerifiedAtMs = System.currentTimeMillis()
        when (action) {
            is CaseProtectedActionRequest.Add -> openAddCaseModelDialogInternal()
            is CaseProtectedActionRequest.Edit -> openEditDialogInternal(action.model)
            is CaseProtectedActionRequest.Delete -> {
                viewModelScope.launch { repository.softDeleteCaseModel(action.model.id) }
            }
            is CaseProtectedActionRequest.PermanentDelete -> {
                viewModelScope.launch { repository.permanentlyDeleteCaseModel(action.model.id) }
            }
        }

        protectedAction.value = null
        passwordError.value = null
    }

    fun dismissPasswordDialog() {
        protectedAction.value = null
        passwordError.value = null
    }

    fun confirmProtectedActionWithSystemAuth() {
        val action = protectedAction.value ?: return
        when (action) {
            is CaseProtectedActionRequest.Add -> openAddCaseModelDialogInternal()
            is CaseProtectedActionRequest.Edit -> openEditDialogInternal(action.model)
            is CaseProtectedActionRequest.Delete -> {
                viewModelScope.launch { repository.softDeleteCaseModel(action.model.id) }
            }
            is CaseProtectedActionRequest.PermanentDelete -> {
                viewModelScope.launch { repository.permanentlyDeleteCaseModel(action.model.id) }
            }
        }
        protectedAction.value = null
        passwordError.value = null
    }

    private fun openAddCaseModelDialogInternal() {
        isAddDialogVisible.value = true
        editingModelId.value = null
        addBrandInput.value = ""
        addModelInput.value = ""
        addQuantityInput.value = "1"
        selectedSerialNumber.value = null
        addErrorMessage.value = null
        isAddSaving.value = false
    }

    private fun openEditDialogInternal(model: CaseModelListItemUi) {
        val inferredBrand = BrandDetector.detectBrand(model.modelName)
        val inferredModel = model.modelName
            .removePrefix(inferredBrand)
            .trim()
            .ifBlank { model.modelName }

        editingModelId.value = model.id
        isAddDialogVisible.value = true
        addBrandInput.value = inferredBrand
        addModelInput.value = inferredModel
        addQuantityInput.value = model.quantity.toString()
        addErrorMessage.value = null
        isAddSaving.value = false
    }

    private fun isPasswordTrusted(): Boolean {
        val now = System.currentTimeMillis()
        return lastPasswordVerifiedAtMs > 0L && (now - lastPasswordVerifiedAtMs) <= CASE_PASSWORD_TRUST_WINDOW_MS
    }

    private fun getAvailableQuantity(modelId: Long): Int? {
        uiState.value.cartItems.firstOrNull { it.modelId == modelId }?.let {
            return it.availableQuantity
        }

        return uiState.value.models.firstOrNull { it.id == modelId }?.quantity
    }

    private fun compareModelNamesNaturally(
        left: CaseModelListItemUi,
        right: CaseModelListItemUi
    ): Int {
        return compareNaturalStrings(left.modelName, right.modelName)
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

    private fun CaseModelEntity.toUi(): CaseModelListItemUi {
        return CaseModelListItemUi(
            id = id,
            modelName = modelName,
            brand = BrandDetector.detectBrand(modelName),
            quantity = quantity,
            isUserAdded = isUserAdded,
            isDeleted = isDeleted,
            brandSerialNumber = brandSerialNumber
        )
    }

    private fun requestExport(format: ExportFormat) {
        viewModelScope.launch {
            val snapshotModels = uiState.value.models
            val bytes = runCatching {
                withContext(Dispatchers.Default) {
                    val payload = buildExportPayload(models = snapshotModels)
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

    private fun buildExportPayload(models: List<CaseModelListItemUi>): ExportPayload {
        val activeModels = models.filter { !it.isDeleted }
        val dynamicBrands = activeModels.map { it.brand }.distinct().sorted()

        val filterRows = buildList {
            add(ExportFilterRow(ALL_CASE_BRANDS, activeModels.size))
            dynamicBrands.forEach { brand ->
                add(ExportFilterRow(brand, activeModels.count { it.brand == brand }))
            }
        }

        val modelRows = models
            .sortedWith(
                compareBy<CaseModelListItemUi> { it.brand.lowercase() }
                    .thenBy { if (it.brandSerialNumber > 0) it.brandSerialNumber else Int.MAX_VALUE }
                    .thenComparator(::compareModelNamesNaturally)
                    .thenBy { it.id }
            )
            .map { item ->
                ExportModelRow(
                    modelName = item.modelName,
                    brand = item.brand,
                    boxNumber = item.brandSerialNumber,
                    type = "Mobile Case",
                    isUserAdded = item.isUserAdded,
                    isDeleted = item.isDeleted,
                    isFavorite = false,
                    additionalCompatible = listOf("Quantity: ${item.quantity}")
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
        return "mobile_cases_export_${timestamp}.${format.fileExtension}"
    }

    class Factory(
        private val repository: CaseRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CasesViewModel(repository) as T
        }
    }
}
