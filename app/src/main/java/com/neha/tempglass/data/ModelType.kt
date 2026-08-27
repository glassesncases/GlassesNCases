package com.neha.tempglass.data

enum class ModelType(
    val storageValue: String,
    val displayName: String
) {
    FLAT("Flat", "Flat"),
    CURVED("Curved", "Curved"),
    MEMBRANE("Membrane", "Membrane");

    companion object {
        fun fromStorage(value: String?): ModelType {
            return entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: FLAT
        }
    }
}
