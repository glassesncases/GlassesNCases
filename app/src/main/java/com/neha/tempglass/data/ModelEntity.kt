package com.neha.tempglass.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "models",
    indices = [Index(value = ["modelName"], unique = true)]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelName: String,
    val boxNumber: Int,
    val additionalCompatible: String = "[]",
    val type: String = ModelType.FLAT.storageValue,
    val isUserAdded: Boolean = false,
    val isDeleted: Boolean = false,
    val isFavorite: Boolean = false,
    val syncState: String = "SYNCED",
    val updatedAtLocal: Long = 0L,
    val updatedAtRemote: Long? = null,
    val lastSyncedAt: Long? = null,
    val deviceId: String = "",
    val needsHardDeleteRemote: Boolean = false
)
