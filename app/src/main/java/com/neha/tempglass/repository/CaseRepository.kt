package com.neha.tempglass.repository

import android.database.sqlite.SQLiteConstraintException
import com.neha.tempglass.data.CaseModelDao
import com.neha.tempglass.data.CaseModelEntity
import com.neha.tempglass.sync.CaseModelRemoteDto
import com.neha.tempglass.sync.DevicePairing
import com.neha.tempglass.sync.ModelSyncService
import com.neha.tempglass.sync.SyncSettingsStore
import com.neha.tempglass.util.BrandDetector
import com.neha.tempglass.util.ModelNameCanonicalizer
import android.util.Log
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow

private const val CASE_SYNC_TAG = "CaseSync"

sealed interface AddCaseModelResult {
    data object Success : AddCaseModelResult
    data object DuplicateModelName : AddCaseModelResult
}

sealed interface UpdateCaseModelResult {
    data object Success : UpdateCaseModelResult
    data object NotFound : UpdateCaseModelResult
    data object DuplicateModelName : UpdateCaseModelResult
}

sealed interface SellCartResult {
    data object Success : SellCartResult
    data class InsufficientStock(val modelIds: List<Long>) : SellCartResult
}

data class CaseSyncReport(
    val pendingCount: Int,
    val pulledCount: Int,
    val appliedCount: Int,
    val failedPushCount: Int
)

class CaseRepository(
    private val dao: CaseModelDao,
    private val syncService: ModelSyncService,
    private val syncSettingsStore: SyncSettingsStore,
    private val deviceIdProvider: () -> String
) {

    fun observeAllCaseModels(): Flow<List<CaseModelEntity>> = dao.observeAllCaseModels()

    fun observeActiveCaseModels(): Flow<List<CaseModelEntity>> = dao.observeActiveCaseModels()

    fun currentPairId(): String? = syncSettingsStore.currentPairId()

    fun currentOtherUid(): String? = syncSettingsStore.currentOtherUid()

    suspend fun getCurrentPairing(): DevicePairing? {
        val pairId = syncSettingsStore.currentPairId() ?: return null
        return syncService.getPairing(pairId)
    }

    /** Copies the owner's case models (only those not present locally) into the local DB. */
    suspend fun copyOwnerCaseModelsIntoLocal(ownerUid: String) {
        val now = System.currentTimeMillis()
        val remoteItems = syncService.pullCaseModels(ownerUid)
        remoteItems.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)
            if (local == null) {
                if (remote.isDeleted) return@forEach
                val insertedId = dao.insert(
                    CaseModelEntity(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        syncState = "SYNCED",
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                )
                if (insertedId != -1L) {
                    dao.markSyncedById(insertedId, remote.updatedAtRemote, now)
                }
            }
        }
    }

    suspend fun preloadIfEmpty(initialModels: List<CaseModelEntity>) {
        if (dao.countAll() == 0 && initialModels.isNotEmpty()) {
            val serialByBrand = mutableMapOf<String, Int>()
            val modelsWithSerials = initialModels.map { model ->
                val brand = BrandDetector.detectBrand(model.modelName)
                val nextSerial = (serialByBrand[brand] ?: 0) + 1
                serialByBrand[brand] = nextSerial

                model.copy(brandSerialNumber = nextSerial)
            }

            dao.insertAll(modelsWithSerials)
        }
    }

    suspend fun addCaseModel(
        modelName: String,
        quantity: Int,
        preferredSerialNumber: Int? = null
    ): AddCaseModelResult {
        val canonicalName = ModelNameCanonicalizer.canonicalizeName(modelName)
        if (canonicalName.isBlank()) return AddCaseModelResult.DuplicateModelName

        val safeQuantity = quantity.coerceAtLeast(0)

        val brand = BrandDetector.detectBrand(canonicalName)
        val serialToUse = preferredSerialNumber?.takeIf { it > 0 } ?: nextBrandSerialNumber(brand)

        val insertedId = dao.insert(
            CaseModelEntity(
                modelName = canonicalName,
                quantity = safeQuantity,
                isUserAdded = true,
                isDeleted = false,
                brandSerialNumber = serialToUse
            )
        )

        return if (insertedId == -1L) {
            AddCaseModelResult.DuplicateModelName
        } else {
            dao.markPendingUpload(insertedId, System.currentTimeMillis(), deviceIdProvider())
            AddCaseModelResult.Success
        }
    }

    suspend fun ensureUserAddedCaseModels(models: List<Pair<String, Int>>) {
        val normalized = linkedMapOf<String, Int>()

        models.forEach { (rawName, qty) ->
            val canonicalName = ModelNameCanonicalizer.canonicalizeName(rawName)
            if (canonicalName.isBlank()) return@forEach
            normalized[canonicalName] = qty.coerceAtLeast(0)
        }

        normalized.forEach { (modelName, qty) ->
            val brand = BrandDetector.detectBrand(modelName)
            val insertedId = dao.insert(
                CaseModelEntity(
                    modelName = modelName,
                    quantity = qty,
                    isUserAdded = true,
                    isDeleted = false,
                    brandSerialNumber = nextBrandSerialNumber(brand)
                )
            )

            if (insertedId == -1L) {
                dao.markAsUserAddedAndUpdateQuantity(modelName, qty)
            }
        }
    }

    suspend fun adjustQuantity(id: Long, delta: Int) {
        dao.adjustQuantity(id, delta)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun setQuantity(id: Long, quantity: Int) {
        dao.setQuantity(id, quantity.coerceAtLeast(0))
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun updateCaseModel(id: Long, modelName: String, quantity: Int): UpdateCaseModelResult {
        val existing = dao.getById(id) ?: return UpdateCaseModelResult.NotFound
        val canonicalName = ModelNameCanonicalizer.canonicalizeName(modelName)
        if (canonicalName.isBlank()) return UpdateCaseModelResult.DuplicateModelName

        val safeQuantity = quantity.coerceAtLeast(0)
        val oldBrand = BrandDetector.detectBrand(existing.modelName)
        val newBrand = BrandDetector.detectBrand(canonicalName)

        val serialToPersist = if (oldBrand == newBrand) {
            existing.brandSerialNumber.takeIf { it > 0 } ?: nextBrandSerialNumber(newBrand)
        } else {
            nextBrandSerialNumber(newBrand)
        }

        return try {
            dao.updateCaseModel(
                id = id,
                modelName = canonicalName,
                quantity = safeQuantity,
                brandSerialNumber = serialToPersist
            )
            dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
            UpdateCaseModelResult.Success
        } catch (_: SQLiteConstraintException) {
            UpdateCaseModelResult.DuplicateModelName
        }
    }

    suspend fun sellCart(cart: Map<Long, Int>): SellCartResult {
        val safeCart = cart.filterValues { it > 0 }
        if (safeCart.isEmpty()) return SellCartResult.Success

        val snapshotById = dao.getByIds(safeCart.keys.toList()).associateBy { it.id }

        val insufficientBeforeSell = safeCart
            .filter { (modelId, sellQty) ->
                val model = snapshotById[modelId]
                model == null || model.isDeleted || model.quantity < sellQty
            }
            .keys
            .toList()

        if (insufficientBeforeSell.isNotEmpty()) {
            return SellCartResult.InsufficientStock(insufficientBeforeSell)
        }

        val sellResult = runCatching {
            dao.sellCartOrThrow(safeCart)
        }

        return if (sellResult.isSuccess) {
            safeCart.keys.forEach { modelId ->
                dao.markPendingUpload(modelId, System.currentTimeMillis(), deviceIdProvider())
            }
            SellCartResult.Success
        } else {
            val failedModelId = sellResult.exceptionOrNull()
                ?.message
                ?.substringAfter("INSUFFICIENT_STOCK:", missingDelimiterValue = "")
                ?.toLongOrNull()

            if (failedModelId != null) {
                SellCartResult.InsufficientStock(listOf(failedModelId))
            } else {
                SellCartResult.InsufficientStock(emptyList())
            }
        }
    }

    suspend fun softDeleteCaseModel(id: Long) {
        dao.softDelete(id)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun restoreCaseModel(id: Long) {
        dao.restore(id)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun syncNow(): Result<Unit> = runCatching {
        if (!syncSettingsStore.isInternetEnabled()) {
            error("Internet sync is disabled")
        }

        val uid = syncService.ensureSignedIn()
        val now = System.currentTimeMillis()
        Log.d(CASE_SYNC_TAG, "syncNow start uid=$uid email=${syncService.currentUserEmail()}")

        val pending = dao.getPendingUploadCaseModels()
        Log.d(CASE_SYNC_TAG, "pendingUpload count=${pending.size}")
        pending.forEach { local ->
            runCatching {
                val remote = CaseModelRemoteDto(
                    modelName = local.modelName,
                    quantity = local.quantity,
                    isUserAdded = local.isUserAdded,
                    isDeleted = local.isDeleted,
                    brandSerialNumber = local.brandSerialNumber,
                    updatedAtRemote = now
                )
                syncService.pushCaseModel(uid, remote)
                if (local.needsHardDeleteRemote) {
                    syncService.markAllCaseModelDocsDeletedByName(uid, local.modelName, now)
                    dao.hardDelete(local.id)
                } else {
                    dao.markSyncedById(local.id, updatedAtRemote = now, lastSyncedAt = now)
                }
            }
        }

        val remoteItems = syncService.pullCaseModels(uid)
        Log.d(CASE_SYNC_TAG, "pulledRemote count=${remoteItems.size}")
        var applied = 0
        remoteItems.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)

            if (local == null) {
                if (remote.isDeleted) {
                    return@forEach
                }
                val insertedId = dao.insert(
                    CaseModelEntity(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        syncState = "SYNCED",
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                )

                if (insertedId != -1L) {
                    dao.markSyncedById(insertedId, remote.updatedAtRemote, now)
                    applied++
                }
            } else {
                val shouldApplyRemote = (maxOf(local.updatedAtLocal, local.updatedAtRemote ?: 0L) <= remote.updatedAtRemote) ||
                    local.syncState != "PENDING_UPLOAD"

                if (shouldApplyRemote) {
                    dao.applyRemoteByModelName(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                    applied++
                }
            }
        }
        Log.d(CASE_SYNC_TAG, "syncNow done applied=$applied")
    }

    suspend fun syncNowWithReport(): Result<CaseSyncReport> = runCatching {
        if (!syncSettingsStore.isInternetEnabled()) {
            error("Internet sync is disabled")
        }

        val uid = syncService.ensureSignedIn()
        val now = System.currentTimeMillis()

        // Determine all UIDs to sync with (own + paired UID if an active pairing exists).
        val targetUids = buildList {
            add(uid)
            val pairing = getCurrentPairing()
            if (pairing != null && pairing.active && pairing.syncsRemaining > 0) {
                pairing.otherUid(uid)?.let { add(it) }
            }
        }

        val pending = dao.getPendingUploadCaseModels()
        var failedPush = 0
        pending.forEach { local ->
            try {
                withTimeout(20_000) {
                    val remote = CaseModelRemoteDto(
                        modelName = local.modelName,
                        quantity = local.quantity,
                        isUserAdded = local.isUserAdded,
                        isDeleted = local.isDeleted,
                        brandSerialNumber = local.brandSerialNumber,
                        updatedAtRemote = now
                    )
                    // Push to own UID, and to the paired UID if active.
                    targetUids.forEach { targetUid ->
                        syncService.pushCaseModel(targetUid, remote)
                        if (local.needsHardDeleteRemote) {
                            syncService.markAllCaseModelDocsDeletedByName(targetUid, local.modelName, now)
                        }
                    }
                    if (local.needsHardDeleteRemote) {
                        dao.hardDelete(local.id)
                    } else {
                        dao.markSyncedById(local.id, updatedAtRemote = now, lastSyncedAt = now)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                failedPush++
            }
        }

        val remoteItems = try {
            withTimeout(20_000) {
                targetUids.flatMap { targetUid -> syncService.pullCaseModels(targetUid) }
                    .distinctBy { it.modelName }
            }
        } catch (e: TimeoutCancellationException) {
            error("Case pull timeout. Check internet or Firestore rules")
        }
        var applied = 0
        remoteItems.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)
            if (local == null) {
                if (remote.isDeleted) {
                    return@forEach
                }
                val insertedId = dao.insert(
                    CaseModelEntity(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        syncState = "SYNCED",
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                )
                if (insertedId != -1L) {
                    dao.markSyncedById(insertedId, remote.updatedAtRemote, now)
                    applied++
                }
            } else {
                val shouldApplyRemote = (maxOf(local.updatedAtLocal, local.updatedAtRemote ?: 0L) <= remote.updatedAtRemote) ||
                    local.syncState != "PENDING_UPLOAD"
                if (shouldApplyRemote) {
                    dao.applyRemoteByModelName(
                        modelName = remote.modelName,
                        quantity = remote.quantity,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        brandSerialNumber = remote.brandSerialNumber,
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                    applied++
                }
            }
        }

        CaseSyncReport(
            pendingCount = pending.size,
            pulledCount = remoteItems.size,
            appliedCount = applied,
            failedPushCount = failedPush
        )
    }

    suspend fun permanentlyDeleteCaseModel(id: Long) {
        dao.markPendingHardDelete(id, System.currentTimeMillis(), deviceIdProvider())
    }

    private suspend fun nextBrandSerialNumber(brand: String): Int {
        val maxSerial = dao.getAllSnapshot()
            .asSequence()
            .filter { BrandDetector.detectBrand(it.modelName) == brand }
            .map { it.brandSerialNumber }
            .maxOrNull() ?: 0

        return maxSerial + 1
    }
}
