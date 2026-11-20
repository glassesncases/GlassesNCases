package com.neha.tempglass.repository

import com.neha.tempglass.data.BrandDao
import com.neha.tempglass.data.BrandEntity
import com.neha.tempglass.data.CaseModelDao
import com.neha.tempglass.data.ModelDao
import com.neha.tempglass.sync.ModelSyncService
import com.neha.tempglass.util.BrandDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface AddBrandResult {
    data class Success(val brandName: String) : AddBrandResult
    data class AlreadyExists(val brandName: String) : AddBrandResult
    data class Invalid(val reason: String) : AddBrandResult
}

sealed interface UpdateBrandResult {
    data class Success(val oldBrandName: String, val newBrandName: String, val updatedModelsCount: Int) : UpdateBrandResult
    data object NoChange : UpdateBrandResult
    data class AlreadyExists(val brandName: String) : UpdateBrandResult
    data class Invalid(val reason: String) : UpdateBrandResult
}

class BrandRepository(
    private val dao: BrandDao,
    private val modelDao: ModelDao? = null,
    private val caseModelDao: CaseModelDao? = null,
    private val syncService: ModelSyncService? = null,
    private val deviceIdProvider: () -> String = { "" }
) {

    fun observeAllBrands(): Flow<List<String>> {
        return dao.observeAllBrands().map { entities ->
            val customNames = entities.map { it.name }
            BrandDetector.setCustomBrands(customNames)
            BrandDetector.getAllKnownBrands()
        }
    }

    suspend fun getAllBrands(): List<String> = withContext(Dispatchers.IO) {
        val customNames = dao.getAllBrands().map { it.name }
        BrandDetector.setCustomBrands(customNames)
        BrandDetector.getAllKnownBrands()
    }

    suspend fun ensureDefaultBrandsSeeded() = withContext(Dispatchers.IO) {
        val existing = dao.getAllBrands().map { it.name.lowercase() }.toSet()
        val toInsert = BrandDetector.BUILTIN_BRANDS
            .filter { it.lowercase() !in existing }
            .map { BrandEntity(name = it, isUserAdded = false) }
        if (toInsert.isNotEmpty()) {
            dao.insertBrands(toInsert)
        }
        val all = dao.getAllBrands().map { it.name }
        BrandDetector.setCustomBrands(all)
    }

    suspend fun syncBrands(uid: String) = withContext(Dispatchers.IO) {
        if (syncService == null) return@withContext
        val localBrands = dao.getAllBrands()
        val userBrands = localBrands.filter { it.isUserAdded }
        if (userBrands.isNotEmpty()) {
            runCatching {
                syncService.pushAllBrandsBatch(uid, userBrands)
            }
        }

        runCatching {
            val remoteBrands = syncService.pullBrands(uid)
            val existingSet = dao.getAllBrands().map { it.name.lowercase() }.toSet()
            val toInsert = remoteBrands.filter { it.name.lowercase() !in existingSet }
            if (toInsert.isNotEmpty()) {
                dao.insertBrands(toInsert)
            }
            val all = dao.getAllBrands().map { it.name }
            BrandDetector.setCustomBrands(all)
        }
    }

    suspend fun addBrand(rawName: String): AddBrandResult = withContext(Dispatchers.IO) {
        val trimmed = rawName.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank()) {
            return@withContext AddBrandResult.Invalid("Brand name cannot be empty")
        }
        if (trimmed.length < 2) {
            return@withContext AddBrandResult.Invalid("Brand name must be at least 2 characters")
        }

        // Format casing: capitalize first letter of each word
        val formatted = trimmed.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // Check if brand already exists (case-insensitive)
        val allKnown = getAllBrands()
        val existing = allKnown.firstOrNull { it.equals(formatted, ignoreCase = true) }
        if (existing != null) {
            return@withContext AddBrandResult.AlreadyExists(existing)
        }

        dao.insertBrand(BrandEntity(name = formatted, isUserAdded = true))
        BrandDetector.addCustomBrand(formatted)
        AddBrandResult.Success(formatted)
    }

    suspend fun updateBrand(oldName: String, rawNewName: String): UpdateBrandResult = withContext(Dispatchers.IO) {
        val trimmedOld = oldName.trim()
        val trimmedNew = rawNewName.trim().replace(Regex("\\s+"), " ")

        if (trimmedNew.isBlank()) {
            return@withContext UpdateBrandResult.Invalid("Brand name cannot be empty")
        }
        if (trimmedNew.length < 2) {
            return@withContext UpdateBrandResult.Invalid("Brand name must be at least 2 characters")
        }

        val formatted = trimmedNew.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        if (formatted == trimmedOld) {
            return@withContext UpdateBrandResult.NoChange
        }

        val allKnown = getAllBrands()
        val conflict = allKnown.firstOrNull {
            it.equals(formatted, ignoreCase = true) && !it.equals(trimmedOld, ignoreCase = true)
        }
        if (conflict != null) {
            return@withContext UpdateBrandResult.AlreadyExists(conflict)
        }

        val existingEntity = dao.getBrandByName(trimmedOld)
        val isUserAdded = existingEntity?.isUserAdded ?: true

        dao.renameBrand(trimmedOld, formatted, isUserAdded)
        BrandDetector.renameBrand(trimmedOld, formatted)

        var affectedModelsCount = 0
        val now = System.currentTimeMillis()
        val deviceId = deviceIdProvider()

        // Cascade rename to glass models
        if (modelDao != null) {
            val glassModels = modelDao.getAllModelsSnapshot()
            for (model in glassModels) {
                if (model.modelName.equals(trimmedOld, ignoreCase = true)) {
                    modelDao.updateModelName(model.id, formatted, now, deviceId)
                    affectedModelsCount++
                } else if (model.modelName.startsWith("$trimmedOld ", ignoreCase = true)) {
                    val rest = model.modelName.substring(trimmedOld.length + 1)
                    modelDao.updateModelName(model.id, "$formatted $rest", now, deviceId)
                    affectedModelsCount++
                }
            }
        }

        // Cascade rename to mobile case models
        if (caseModelDao != null) {
            val caseModels = caseModelDao.getAllSnapshot()
            for (caseModel in caseModels) {
                if (caseModel.modelName.equals(trimmedOld, ignoreCase = true)) {
                    caseModelDao.updateModelName(caseModel.id, formatted, now, deviceId)
                    affectedModelsCount++
                } else if (caseModel.modelName.startsWith("$trimmedOld ", ignoreCase = true)) {
                    val rest = caseModel.modelName.substring(trimmedOld.length + 1)
                    caseModelDao.updateModelName(caseModel.id, "$formatted $rest", now, deviceId)
                    affectedModelsCount++
                }
            }
        }

        UpdateBrandResult.Success(trimmedOld, formatted, affectedModelsCount)
    }
}
