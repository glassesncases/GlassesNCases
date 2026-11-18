package com.neha.tempglass.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "brands")
data class BrandEntity(
    @PrimaryKey
    val name: String,
    val isUserAdded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
