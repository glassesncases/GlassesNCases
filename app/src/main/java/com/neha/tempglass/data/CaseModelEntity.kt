package com.neha.tempglass.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "case_models",
    indices = [Index(value = ["modelName"], unique = true)]
)
data class CaseModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelName: String,
    val quantity: Int = 0,
    val isUserAdded: Boolean = false,
    val isDeleted: Boolean = false,
    val brandSerialNumber: Int = 0,
    val syncState: String = "SYNCED",
    val updatedAtLocal: Long = 0L,
    val updatedAtRemote: Long? = null,
    val lastSyncedAt: Long? = null,
    val deviceId: String = "",
    val needsHardDeleteRemote: Boolean = false
)
