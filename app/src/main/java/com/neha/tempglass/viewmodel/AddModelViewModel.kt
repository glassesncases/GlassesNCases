package com.neha.tempglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neha.tempglass.data.ModelType
import com.neha.tempglass.repository.AddBrandResult
import com.neha.tempglass.repository.AddModelResult
import com.neha.tempglass.repository.BrandRepository
import com.neha.tempglass.repository.ModelRepository
import com.neha.tempglass.repository.UpdateModelResult
import com.neha.tempglass.util.BrandDetector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddModelEvent {
    data object Saved : AddModelEvent
}

data class AddModelUiState(
    val editingModelId: Long? = null,
    val brandInput: String = "",
    val modelInput: String = "",
    val brandSuggestions: List<String> = emptyList(),
    val additionalCompatibleInput: String = "",
    val selectedType: ModelType = ModelType.FLAT,
    val selectedBoxNumber: Int? = null,
    val availableBoxNumbers: List<Int> = emptyList(),
    val availableGapBoxNumbers: List<Int> = emptyList(),
    val nextBoxNumber: Int = 1,
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

class AddModelViewModel(
    private val repository: ModelRepository,
    private val brandRepository: BrandRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddModelUiState())
    val uiState: StateFlow<AddModelUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddModelEvent>()
    val events: SharedFlow<AddModelEvent> = _events.asSharedFlow()

    init {
        refreshBoxData()
        refreshBrandSuggestions()
    }

    fun prepareForOpen() {
        _uiState.update {
            it.copy(
                editingModelId = null,
                brandInput = "",
                modelInput = "",
                additionalCompatibleInput = "",
                selectedType = ModelType.FLAT,
                selectedBoxNumber = null,
                errorMessage = null,
                isSaving = false
            )
        }
        refreshBoxData()
        refreshBrandSuggestions()
    }

    fun prepareForEdit(model: ModelListItemUi) {
        _uiState.update {
            val inferredBrand = model.brand
            val inferredModel = model.modelName
                .removePrefix(inferredBrand)
                .trim()
                .ifBlank { model.modelName }
            it.copy(
                editingModelId = model.id,
                brandInput = inferredBrand,
                modelInput = inferredModel,
                additionalCompatibleInput = model.additionalCompatible.joinToString(", "),
                selectedType = model.type,
                selectedBoxNumber = model.boxNumber,
                errorMessage = null,
                isSaving = false
            )
        }
        refreshBoxData()
        refreshBrandSuggestions()
    }

    fun onBrandInputChange(value: String) {
        _uiState.update {
            it.copy(brandInput = value, errorMessage = null)
        }
        refreshBrandSuggestions()
    }

    fun onModelInputChange(value: String) {
        _uiState.update {
            it.copy(modelInput = value, errorMessage = null)
        }
    }

    fun onAdditionalCompatibleChange(value: String) {
        _uiState.update {
            it.copy(additionalCompatibleInput = value, errorMessage = null)
        }
    }

    fun onSelectedTypeChange(value: ModelType) {
        _uiState.update {
            it.copy(selectedType = value, errorMessage = null)
        }
    }

    fun onSelectedBoxNumberChange(value: Int) {
        _uiState.update {
            it.copy(selectedBoxNumber = value, errorMessage = null)
        }
    }

    fun onBoxNumberInputChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(4)
        val parsed = digits.toIntOrNull()?.takeIf { it > 0 }

        _uiState.update {
            it.copy(selectedBoxNumber = parsed, errorMessage = null)
        }
    }

    fun saveModel() {
        val current = _uiState.value
        val editingModelId = current.editingModelId
        val brand = current.brandInput.trim()
        val model = current.modelInput.trim()
        val cleanName = "$brand $model".trim()

        if (brand.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Brand is required") }
            return
        }

        if (model.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Model name is required") }
            return
        }

        val boxNumber = current.selectedBoxNumber

        if (boxNumber == null) {
            _uiState.update { it.copy(errorMessage = "Please select an existing box or create a new one") }
            return
        }

        val additional = current.additionalCompatibleInput
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            if (editingModelId == null) {
                when (repository.addModel(cleanName, boxNumber, additional, current.selectedType)) {
                    AddModelResult.Success -> onPersistSuccess()
                    AddModelResult.DuplicateModelName -> onDuplicateModelName()
                }
            } else {
                when (
                    repository.updateModel(
                        editingModelId,
                        cleanName,
                        boxNumber,
                        additional,
                        current.selectedType
                    )
                ) {
                    UpdateModelResult.Success -> onPersistSuccess()
                    UpdateModelResult.DuplicateModelName -> onDuplicateModelName()
                }
            }
        }
    }

    private suspend fun onPersistSuccess() {
        refreshBoxData()
        _uiState.update {
            it.copy(
                editingModelId = null,
                brandInput = "",
                modelInput = "",
                additionalCompatibleInput = "",
                selectedType = ModelType.FLAT,
                selectedBoxNumber = null,
                brandSuggestions = emptyList(),
                isSaving = false,
                errorMessage = null
            )
        }
        _events.emit(AddModelEvent.Saved)
    }

    private fun onDuplicateModelName() {
        _uiState.update {
            it.copy(
                isSaving = false,
                errorMessage = "Model already exists"
            )
        }
    }

    private fun refreshBoxData() {
        viewModelScope.launch {
            val existingBoxes = repository.getExistingBoxNumbers()
            val next = repository.getNextBoxNumber()
            val gaps = computeGaps(existingBoxes)

            _uiState.update { current ->
                val availableOptions = (existingBoxes + gaps + next).distinct().sorted()
                val selected = current.selectedBoxNumber
                    ?.takeIf { it in availableOptions }
                    ?: availableOptions.firstOrNull()

                current.copy(
                    availableBoxNumbers = availableOptions,
                    availableGapBoxNumbers = gaps,
                    selectedBoxNumber = selected,
                    nextBoxNumber = next
                )
            }
        }
    }

    fun createBrand(name: String) {
        viewModelScope.launch {
            when (val result = brandRepository.addBrand(name)) {
                is AddBrandResult.Success -> {
                    _uiState.update { it.copy(brandInput = result.brandName, errorMessage = null) }
                    refreshBrandSuggestions()
                }
                is AddBrandResult.AlreadyExists -> {
                    _uiState.update { it.copy(brandInput = result.brandName, errorMessage = null) }
                    refreshBrandSuggestions()
                }
                is AddBrandResult.Invalid -> {
                    _uiState.update { it.copy(errorMessage = result.reason) }
                }
            }
        }
    }

    private fun refreshBrandSuggestions() {
        viewModelScope.launch {
            val query = _uiState.value.brandInput.trim()
            val allBrands = brandRepository.getAllBrands()

            val filtered = if (query.isBlank()) {
                allBrands.take(15)
            } else {
                allBrands.filter { it.startsWith(query, ignoreCase = true) }.take(15)
            }

            _uiState.update { it.copy(brandSuggestions = filtered) }
        }
    }

    private fun computeGaps(existing: List<Int>): List<Int> {
        val valid = existing.filter { it > 0 }.distinct().sorted()
        if (valid.isEmpty()) return emptyList()
        val set = valid.toSet()
        return (1..valid.last()).filter { it !in set }
    }

    class Factory(
        private val repository: ModelRepository,
        private val brandRepository: BrandRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddModelViewModel(repository, brandRepository) as T
        }
    }
}
