package com.neha.tempglass.sync

data class ModelRemoteDto(
    val modelName: String,
    val boxNumber: Int,
    val additionalCompatible: String,
    val type: String,
    val isUserAdded: Boolean,
    val isDeleted: Boolean,
    val isFavorite: Boolean,
    val updatedAtRemote: Long
)
