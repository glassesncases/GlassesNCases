package com.neha.tempglass.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseModelDao {

    @Query("SELECT * FROM case_models ORDER BY isDeleted ASC, modelName COLLATE NOCASE ASC")
    fun observeAllCaseModels(): Flow<List<CaseModelEntity>>

    @Query("SELECT * FROM case_models WHERE isDeleted = 0 ORDER BY modelName COLLATE NOCASE ASC")
    fun observeActiveCaseModels(): Flow<List<CaseModelEntity>>

    @Query("SELECT * FROM case_models WHERE syncState = 'PENDING_UPLOAD'")
    suspend fun getPendingUploadCaseModels(): List<CaseModelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(model: CaseModelEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(models: List<CaseModelEntity>)

    @Query("SELECT COUNT(*) FROM case_models")
    suspend fun countAll(): Int

    @Query("SELECT * FROM case_models")
    suspend fun getAllSnapshot(): List<CaseModelEntity>

    @Query("SELECT * FROM case_models WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CaseModelEntity>

    @Query("SELECT * FROM case_models WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CaseModelEntity?

    @Query("SELECT * FROM case_models WHERE modelName = :modelName LIMIT 1")
    suspend fun getByModelName(modelName: String): CaseModelEntity?

    @Query(
        """
        UPDATE case_models
        SET quantity = :quantity,
            isUserAdded = 1,
            isDeleted = 0
        WHERE modelName = :modelName
        """
    )
    suspend fun markAsUserAddedAndUpdateQuantity(modelName: String, quantity: Int): Int

    @Query("UPDATE case_models SET quantity = :quantity WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Int)

    @Query(
        """
        UPDATE case_models
        SET modelName = :modelName,
            quantity = :quantity,
            brandSerialNumber = :brandSerialNumber
        WHERE id = :id
        """
    )
    suspend fun updateCaseModel(id: Long, modelName: String, quantity: Int, brandSerialNumber: Int)

    @Query(
        """
        UPDATE case_models
        SET modelName = :newModelName,
            syncState = 'PENDING_UPLOAD',
            updatedAtLocal = :updatedAtLocal,
            deviceId = :deviceId
        WHERE id = :id
        """
    )
    suspend fun updateModelName(
        id: Long,
        newModelName: String,
        updatedAtLocal: Long,
        deviceId: String
    ): Int

    @Query("UPDATE case_models SET brandSerialNumber = :serialNumber WHERE id = :id")
    suspend fun setBrandSerialNumber(id: Long, serialNumber: Int)

    @Query("UPDATE case_models SET quantity = MAX(quantity + :delta, 0) WHERE id = :id")
    suspend fun adjustQuantity(id: Long, delta: Int)

    @Query("UPDATE case_models SET quantity = quantity - :sellQty WHERE id = :id AND quantity >= :sellQty")
    suspend fun decrementQuantityIfEnough(id: Long, sellQty: Int): Int

    @Transaction
    suspend fun sellCartOrThrow(cart: Map<Long, Int>) {
        cart.forEach { (id, sellQty) ->
            if (sellQty <= 0) return@forEach

            val updated = decrementQuantityIfEnough(id, sellQty)
            if (updated == 0) {
                throw IllegalStateException("INSUFFICIENT_STOCK:$id")
            }
        }
    }

    @Query("UPDATE case_models SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE case_models SET isDeleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM case_models WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query(
        """
        UPDATE case_models
        SET syncState = 'PENDING_UPLOAD',
            updatedAtLocal = :updatedAtLocal,
            deviceId = :deviceId
        WHERE id = :id
        """
    )
    suspend fun markPendingUpload(id: Long, updatedAtLocal: Long, deviceId: String)

    @Query(
        """
        UPDATE case_models
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
        UPDATE case_models
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
        UPDATE case_models
        SET quantity = :quantity,
            isUserAdded = :isUserAdded,
            isDeleted = :isDeleted,
            brandSerialNumber = :brandSerialNumber,
            syncState = 'SYNCED',
            updatedAtRemote = :updatedAtRemote,
            lastSyncedAt = :lastSyncedAt,
            needsHardDeleteRemote = 0
        WHERE modelName = :modelName
        """
    )
    suspend fun applyRemoteByModelName(
        modelName: String,
        quantity: Int,
        isUserAdded: Boolean,
        isDeleted: Boolean,
        brandSerialNumber: Int,
        updatedAtRemote: Long,
        lastSyncedAt: Long
    ): Int

    @Query(
        """
        UPDATE case_models
        SET syncState = 'SYNCED',
            updatedAtRemote = :updatedAtRemote,
            lastSyncedAt = :lastSyncedAt,
            needsHardDeleteRemote = 0
        """
    )
    suspend fun markAllSynced(updatedAtRemote: Long, lastSyncedAt: Long)

    @Query(
        """
        UPDATE case_models
        SET syncState = 'SYNCED',
            updatedAtRemote = :now,
            lastSyncedAt = :now,
            needsHardDeleteRemote = 0
        WHERE id IN (:ids)
        """
    )
    suspend fun markSyncedByIds(ids: List<Long>, now: Long)

    @Transaction
    suspend fun applyRemoteCaseModels(remoteModels: List<CaseModelEntity>, now: Long): Int {
        var applied = 0
        for (remote in remoteModels) {
            val local = getByModelName(remote.modelName)
            if (local == null) {
                if (!remote.isDeleted) {
                    val insertedId = insert(remote)
                    if (insertedId != -1L) {
                        markSyncedById(insertedId, remote.updatedAtRemote ?: now, now)
                        applied++
                    }
                }
            } else {
                val shouldApplyRemote = (maxOf(local.updatedAtLocal, local.updatedAtRemote ?: 0L) <= (remote.updatedAtRemote ?: 0L)) ||
                    local.syncState != "PENDING_UPLOAD"

                if (shouldApplyRemote) {
                    applyRemoteByModelName(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        updatedAtRemote = remote.updatedAtRemote ?: now,
                        lastSyncedAt = now
                    )
                    applied++
                }
            }
        }
        return applied
    }
}
