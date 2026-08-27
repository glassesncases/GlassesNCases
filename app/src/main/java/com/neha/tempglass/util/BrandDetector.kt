package com.neha.tempglass.util

object BrandDetector {

    private val knownPrefixes = listOf(
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

    fun detectBrand(modelName: String): String {
        val canonical = ModelNameCanonicalizer.canonicalBrand(modelName)
        if (canonical != null) return canonical

        return knownPrefixes.firstOrNull { modelName.startsWith(it, ignoreCase = true) } ?: "Other"
    }
}
