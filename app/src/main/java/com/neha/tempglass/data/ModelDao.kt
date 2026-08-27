package com.neha.tempglass.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY isDeleted ASC, modelName COLLATE NOCASE ASC")
    fun observeAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isDeleted = 0 ORDER BY modelName COLLATE NOCASE ASC")
    fun observeActiveModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE syncState = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadModels(): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(model: ModelEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(models: List<ModelEntity>)

    @Query(
        """
        UPDATE models
        SET boxNumber = :boxNumber,
            isUserAdded = 1,
            isDeleted = 0
        WHERE modelName = :modelName
        """
    )
    suspend fun markAsUserAddedAndUpdateBox(modelName: String, boxNumber: Int): Int

    @Query("SELECT COUNT(*) FROM models")
    suspend fun countAll(): Int

    @Query("SELECT * FROM models")
    suspend fun getAllModelsSnapshot(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByModelName(modelName: String): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ModelEntity?

    @Query("SELECT * FROM models WHERE isDeleted = 0")
    suspend fun getActiveModelsSnapshot(): List<ModelEntity>

    @Query("UPDATE models SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE models SET isDeleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE models SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query(
        """
        UPDATE models
        SET modelName = :modelName,
            boxNumber = :boxNumber,
            additionalCompatible = :additionalCompatible,
            type = :type
        WHERE id = :id
        """
    )
    suspend fun updateModel(
        id: Long,
        modelName: String,
        boxNumber: Int,
        additionalCompatible: String,
        type: String
    ): Int

    @Query("SELECT MAX(boxNumber) FROM models")
    suspend fun getMaxBoxNumber(): Int?

    @Query("SELECT DISTINCT boxNumber FROM models WHERE isDeleted = 0 ORDER BY boxNumber ASC")
    suspend fun getExistingBoxNumbers(): List<Int>

    @Query(
        """
        UPDATE models
        SET type = 'Curved'
        WHERE boxNumber BETWEEN :startBox AND :endBox
        """
    )
    suspend fun markTypeCurvedByBoxRange(startBox: Int, endBox: Int): Int

    @Query(
        """
        UPDATE models
        SET syncState = 'PENDING_UPLOAD',
            updatedAtLocal = :updatedAtLocal,
            deviceId = :deviceId
        WHERE id = :id
        """
    )
    suspend fun markPendingUpload(id: Long, updatedAtLocal: Long, deviceId: String)

    @Query(
        """
        UPDATE models
        SET isDeleted = 1,
            syncState = 'PENDING_UPLOAD',
            needsHardDeleteRemote = 1,
            updatedAtLocal = :updatedAtLocal,
            deviceId = :deviceId
        WHERE id = :id
        """
    )
    suspend fun markPendingHardDelete(id: Long, updatedAtLocal: Long, deviceId: String)

    @Query(
        """
        UPDATE models
        SET syncState = 'SYNCED',
            updatedAtRemote = :updatedAtRemote,
            lastSyncedAt = :lastSyncedAt,
            needsHardDeleteRemote = 0
        WHERE id = :id
        """
    )
    suspend fun markSyncedById(id: Long, updatedAtRemote: Long, lastSyncedAt: Long)

    @Query(
        """
        UPDATE models
        SET boxNumber = :boxNumber,
            additionalCompatible = :additionalCompatible,
            type = :type,
            isUserAdded = :isUserAdded,
            isDeleted = :isDeleted,
            isFavorite = :isFavorite,
            syncState = 'SYNCED',
            updatedAtRemote = :updatedAtRemote,
            lastSyncedAt = :lastSyncedAt,
            needsHardDeleteRemote = 0
        WHERE modelName = :modelName
        """
    )
    suspend fun applyRemoteByModelName(
        modelName: String,
        boxNumber: Int,
        additionalCompatible: String,
        type: String,
        isUserAdded: Boolean,
        isDeleted: Boolean,
        isFavorite: Boolean,
        updatedAtRemote: Long,
        lastSyncedAt: Long
    ): Int
}
