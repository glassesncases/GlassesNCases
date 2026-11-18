package com.neha.tempglass.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {

    @Query("SELECT * FROM brands ORDER BY LOWER(name) ASC")
    fun observeAllBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands ORDER BY LOWER(name) ASC")
    suspend fun getAllBrands(): List<BrandEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrand(brand: BrandEntity): Long

    @Query("DELETE FROM brands WHERE LOWER(name) = LOWER(:brandName)")
    suspend fun deleteBrand(brandName: String)
}
