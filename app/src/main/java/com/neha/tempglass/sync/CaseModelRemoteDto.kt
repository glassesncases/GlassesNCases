package com.neha.tempglass.sync

data class CaseModelRemoteDto(
    val modelName: String,
    val quantity: Int,
    val isUserAdded: Boolean,
    val isDeleted: Boolean,
    val brandSerialNumber: Int,
    val updatedAtRemote: Long
)
