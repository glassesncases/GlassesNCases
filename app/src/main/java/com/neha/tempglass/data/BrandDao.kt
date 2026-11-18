package com.neha.tempglass.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {

    @Query("SELECT * FROM brands ORDER BY LOWER(name) ASC")
    fun observeAllBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands ORDER BY LOWER(name) ASC")
    suspend fun getAllBrands(): List<BrandEntity>

    @Query("SELECT * FROM brands WHERE LOWER(name) = LOWER(:brandName) LIMIT 1")
    suspend fun getBrandByName(brandName: String): BrandEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrand(brand: BrandEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrands(brands: List<BrandEntity>)

    @Query("DELETE FROM brands WHERE LOWER(name) = LOWER(:brandName)")
    suspend fun deleteBrand(brandName: String)

    @Transaction
    suspend fun renameBrand(oldName: String, newName: String, isUserAdded: Boolean) {
        deleteBrand(oldName)
        insertBrand(BrandEntity(name = newName, isUserAdded = isUserAdded))
    }
}
