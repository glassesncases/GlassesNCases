package com.neha.tempglass.util

import java.util.concurrent.CopyOnWriteArraySet

object BrandDetector {

    val BUILTIN_BRANDS = listOf(
        "Apple",
        "Samsung",
        "Google",
        "OnePlus",
        "Xiaomi",
        "Redmi",
        "Poco",
        "Oppo",
        "Vivo",
        "Realme",
        "Nothing",
        "Motorola",
        "iQOO",
        "Narzo",
        "Infinix",
        "Itel",
        "Tecno",
        "Nokia",
        "CMF",
        "Mi"
    )

    private val customBrands = CopyOnWriteArraySet<String>()

    fun setCustomBrands(brands: Collection<String>) {
        customBrands.clear()
        customBrands.addAll(brands.filter { it.isNotBlank() })
    }

    fun addCustomBrand(brand: String) {
        val trimmed = brand.trim()
        if (trimmed.isNotBlank()) {
            customBrands.add(trimmed)
        }
    }

    fun renameBrand(oldName: String, newName: String) {
        val trimmedOld = oldName.trim()
        val trimmedNew = newName.trim()
        customBrands.removeIf { it.equals(trimmedOld, ignoreCase = true) }
        if (trimmedNew.isNotBlank()) {
            customBrands.add(trimmedNew)
        }
    }

    fun getAllKnownBrands(): List<String> {
        val combined = (BUILTIN_BRANDS + customBrands)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
        return combined
    }

    fun detectBrand(modelName: String): String {
        val canonical = ModelNameCanonicalizer.canonicalBrand(modelName)
        if (canonical != null) return canonical

        // Sort prefixes by length descending so longer matching prefixes take precedence
        val allPrefixes = (BUILTIN_BRANDS + customBrands)
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }

        return allPrefixes.firstOrNull { modelName.startsWith(it, ignoreCase = true) } ?: "Other"
    }
}
