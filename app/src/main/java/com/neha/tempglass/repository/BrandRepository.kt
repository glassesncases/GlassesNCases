package com.neha.tempglass.repository

import com.neha.tempglass.data.BrandDao
import com.neha.tempglass.data.BrandEntity
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

class BrandRepository(
    private val dao: BrandDao
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
}
