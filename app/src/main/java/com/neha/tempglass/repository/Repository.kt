package com.neha.tempglass.repository

import android.database.sqlite.SQLiteConstraintException
import com.neha.tempglass.data.CompatibleModelsCodec
import com.neha.tempglass.data.ModelDao
import com.neha.tempglass.data.ModelEntity
import com.neha.tempglass.data.ModelType
import com.neha.tempglass.sync.ConsumedLinkCodeResult
import com.neha.tempglass.sync.DevicePairing
import com.neha.tempglass.sync.ModelRemoteDto
import com.neha.tempglass.sync.ModelSyncService
import com.neha.tempglass.sync.SyncSettingsStore
import com.neha.tempglass.util.ModelNameCanonicalizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow

private const val CURVED_BOX_START = 79
private const val CURVED_BOX_END = 99
private const val MODEL_SYNC_TAG = "ModelSync"

sealed interface AddModelResult {
    data object Success : AddModelResult
    data object DuplicateModelName : AddModelResult
}

sealed interface UpdateModelResult {
    data object Success : UpdateModelResult
    data object DuplicateModelName : UpdateModelResult
}

data class ModelSyncReport(
    val uid: String,
    val email: String?,
    val pendingCount: Int,
    val pulledCount: Int,
    val appliedCount: Int,
    val failedPushCount: Int
)

class ModelRepository(
    private val dao: ModelDao,
    private val syncService: ModelSyncService,
    private val syncSettingsStore: SyncSettingsStore,
    private val deviceIdProvider: () -> String
) {

    fun isSignedInWithSharedAccount(): Boolean = syncService.isSignedInWithSharedAccount()

    fun currentUserEmail(): String? = syncService.currentUserEmail()

    suspend fun currentUid(): String? = runCatching { syncService.ensureSignedIn() }.getOrNull()

    fun currentPairId(): String? = syncSettingsStore.currentPairId()

    fun currentOtherUid(): String? = syncSettingsStore.currentOtherUid()

    fun setPairing(pairId: String?, otherUid: String?) {
        syncSettingsStore.setPairing(pairId, otherUid)
    }

    fun isPrimaryDevice(): Boolean = syncSettingsStore.isPrimaryDevice()
    fun setPrimaryDevice(isPrimary: Boolean) = syncSettingsStore.setPrimaryDevice(isPrimary)
    fun saveAuthCredentials(email: String, pass: String) = syncSettingsStore.saveAuthCredentials(email, pass)
    fun getSavedAuthEmail(): String? = syncSettingsStore.getSavedAuthEmail()
    fun getSavedAuthPassword(): String? = syncSettingsStore.getSavedAuthPassword()
    fun clearAuthCredentials() = syncSettingsStore.clearAuthCredentials()

    suspend fun generateLinkCode(): String {
        val uid = syncService.ensureSignedIn()
        val email = syncService.currentUserEmail() ?: error("Account email not found")
        val password = syncSettingsStore.getSavedAuthPassword()
            ?: error("Account password required to generate link code")
        ensureAllModelsUploadedToCloud(uid)
        return syncService.generateLinkCode(uid, email, password)
    }

    suspend fun consumeLinkCode(code: String): DevicePairing {
        val myUid = syncService.ensureSignedIn()
        val pairing = syncService.consumeLinkCode(code, myUid)
        syncSettingsStore.setPairing(pairing.pairId, pairing.otherUid(myUid))
        return pairing
    }

    suspend fun consumeLinkCodeWithAuth(code: String): ConsumedLinkCodeResult {
        val result = syncService.consumeLinkCodeWithAuth(code)
        syncSettingsStore.setPairing(result.pairing.pairId, result.pairing.otherUid(result.uid))
        syncSettingsStore.setPrimaryDevice(false)
        return result
    }

    suspend fun getCurrentPairing(): DevicePairing? {
        val pairId = syncSettingsStore.currentPairId() ?: return null
        return syncService.getPairing(pairId)
    }

    /** Atomically consumes one shared sync slot if an active pairing exists. */
    suspend fun consumePairingSyncSlot(): DevicePairing? {
        val pairing = getCurrentPairing() ?: return null
        if (pairing.isUnlimited) {
            return pairing
        }
        val pairId = syncSettingsStore.currentPairId() ?: return null
        return syncService.consumeSyncSlot(pairId)
    }

    suspend fun ensureAllModelsUploadedToCloud(uid: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val allLocal = dao.getAllModelsSnapshot()
        if (allLocal.isEmpty()) return@withContext

        val dtoList = allLocal.map { local ->
            ModelRemoteDto(
                modelName = local.modelName,
                boxNumber = local.boxNumber,
                additionalCompatible = local.additionalCompatible,
                type = local.type,
                isUserAdded = local.isUserAdded,
                isDeleted = local.isDeleted,
                isFavorite = local.isFavorite,
                updatedAtRemote = now
            )
        }
        syncService.pushAllModelsBatch(uid, dtoList)
        dao.markAllSynced(updatedAtRemote = now, lastSyncedAt = now)
    }

    /** Copies the owner's models into the local DB, applying updates to existing models as well. */
    suspend fun copyOwnerModelsIntoLocal(ownerUid: String) {
        val now = System.currentTimeMillis()
        val remoteModels = syncService.pullModels(ownerUid)
        remoteModels.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)
            if (local == null) {
                if (remote.isDeleted) return@forEach
                val insertedId = dao.insert(
                    ModelEntity(
                        modelName = remote.modelName,
                        boxNumber = remote.boxNumber,
                        additionalCompatible = remote.additionalCompatible,
                        type = remote.type,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        isFavorite = remote.isFavorite,
                        syncState = "SYNCED",
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                )
                if (insertedId != -1L) {
                    dao.markSyncedById(insertedId, remote.updatedAtRemote, now)
                }
            } else {
                dao.applyRemoteByModelName(
                    modelName = remote.modelName,
                    boxNumber = remote.boxNumber,
                    additionalCompatible = remote.additionalCompatible,
                    type = remote.type,
                    isUserAdded = remote.isUserAdded,
                    isDeleted = remote.isDeleted,
                    isFavorite = remote.isFavorite,
                    updatedAtRemote = remote.updatedAtRemote,
                    lastSyncedAt = now
                )
            }
        }
    }

    suspend fun signInWithEmailPassword(email: String, password: String) {
        syncService.signInWithEmailPassword(email, password)
    }

    suspend fun registerWithEmailPassword(email: String, password: String) {
        syncService.registerWithEmailPassword(email, password)
    }

    fun signOut() {
        syncService.signOut()
        syncSettingsStore.setPairing(null, null)
        syncSettingsStore.clearAuthCredentials()
        syncSettingsStore.setPrimaryDevice(true)
    }

    fun isInternetEnabled(): Boolean = syncSettingsStore.isInternetEnabled()

    fun setInternetEnabled(enabled: Boolean) {
        syncSettingsStore.setInternetEnabled(enabled)
    }

    fun isUseScreenLockEnabled(): Boolean = syncSettingsStore.isUseScreenLockEnabled()

    fun setUseScreenLockEnabled(enabled: Boolean) {
        syncSettingsStore.setUseScreenLockEnabled(enabled)
    }

    fun observeAllModels(): Flow<List<ModelEntity>> = dao.observeAllModels()

    fun observeActiveModels(): Flow<List<ModelEntity>> = dao.observeActiveModels()

    suspend fun preloadIfEmpty(initialModels: List<ModelEntity>) {
        if (dao.countAll() == 0 && initialModels.isNotEmpty()) {
            dao.insertAll(initialModels)
        }
    }

    suspend fun syncInitialModels(initialModels: List<ModelEntity>) {
        if (initialModels.isNotEmpty()) {
            dao.insertAll(initialModels)
        }
    }

    suspend fun normalizeLegacyBrandNames() {
        dao.getAllModelsSnapshot().forEach { model ->
            val canonicalName = ModelNameCanonicalizer.canonicalizeName(model.modelName)

            if (canonicalName == model.modelName) return@forEach

            runCatching {
                dao.updateModel(
                    id = model.id,
                    modelName = canonicalName,
                    boxNumber = model.boxNumber,
                    additionalCompatible = model.additionalCompatible,
                    type = model.type
                )
            }
        }
    }

    suspend fun ensureUserAddedModels(models: List<Pair<String, Int>>) {
        val normalizedModels = linkedMapOf<String, Int>()

        models.forEach { (rawName, boxNumber) ->
            val canonicalName = ModelNameCanonicalizer.canonicalizeName(rawName)

            if (canonicalName.isBlank() || boxNumber <= 0) return@forEach

            normalizedModels[canonicalName] = boxNumber
        }

        normalizedModels.forEach { (modelName, boxNumber) ->
            val insertedId = dao.insert(
                ModelEntity(
                    modelName = modelName,
                    boxNumber = boxNumber,
                    type = ModelType.FLAT.storageValue,
                    isUserAdded = true,
                    isDeleted = false
                )
            )

            if (insertedId == -1L) {
                dao.markAsUserAddedAndUpdateBox(modelName, boxNumber)
            }
        }
    }

    suspend fun addModel(
        modelName: String,
        boxNumber: Int,
        additionalCompatible: List<String>,
        type: ModelType
    ): AddModelResult {
        val canonicalName = ModelNameCanonicalizer.canonicalizeName(modelName)

        val insertedId = dao.insert(
            ModelEntity(
                modelName = canonicalName,
                boxNumber = boxNumber,
                additionalCompatible = CompatibleModelsCodec.encode(additionalCompatible),
                type = type.storageValue,
                isUserAdded = true,
                isDeleted = false
            )
        )

        return if (insertedId == -1L) {
            AddModelResult.DuplicateModelName
        } else {
            dao.markPendingUpload(insertedId, System.currentTimeMillis(), deviceIdProvider())
            AddModelResult.Success
        }
    }

    suspend fun updateModel(
        id: Long,
        modelName: String,
        boxNumber: Int,
        additionalCompatible: List<String>,
        type: ModelType
    ): UpdateModelResult {
        val canonicalName = ModelNameCanonicalizer.canonicalizeName(modelName)
        val encodedAdditional = CompatibleModelsCodec.encode(additionalCompatible)

        return try {
            dao.updateModel(
                id = id,
                modelName = canonicalName,
                boxNumber = boxNumber,
                additionalCompatible = encodedAdditional,
                type = type.storageValue
            )
            dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
            UpdateModelResult.Success
        } catch (_: SQLiteConstraintException) {
            UpdateModelResult.DuplicateModelName
        }
    }

    suspend fun softDeleteModel(id: Long) {
        dao.softDelete(id)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun restoreModel(id: Long) {
        dao.restore(id)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun permanentlyDeleteModel(id: Long) {
        dao.markPendingHardDelete(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean) {
        dao.setFavorite(id, isFavorite)
        dao.markPendingUpload(id, System.currentTimeMillis(), deviceIdProvider())
    }

    suspend fun syncNow(): Result<Unit> = runCatching {
        if (!syncSettingsStore.isInternetEnabled()) {
            error("Internet sync is disabled")
        }

        val uid = syncService.ensureSignedIn()
        val now = System.currentTimeMillis()
        Log.d(MODEL_SYNC_TAG, "syncNow start uid=$uid email=${syncService.currentUserEmail()}")

        val pending = dao.getPendingUploadModels()
        Log.d(MODEL_SYNC_TAG, "pendingUpload count=${pending.size}")
        pending.forEach { local ->
            runCatching {
                val remote = ModelRemoteDto(
                    modelName = local.modelName,
                    boxNumber = local.boxNumber,
                    additionalCompatible = local.additionalCompatible,
                    type = local.type,
                    isUserAdded = local.isUserAdded,
                    isDeleted = local.isDeleted,
                    isFavorite = local.isFavorite,
                    updatedAtRemote = now
                )
                syncService.pushModel(uid, remote)
                if (local.needsHardDeleteRemote) {
                    syncService.markAllModelDocsDeletedByName(uid, local.modelName, now)
                    dao.hardDelete(local.id)
                } else {
                    dao.markSyncedById(local.id, updatedAtRemote = now, lastSyncedAt = now)
                }
            }
        }

        val remoteModels = syncService.pullModels(uid)
        Log.d(MODEL_SYNC_TAG, "pulledRemote count=${remoteModels.size}")
        var applied = 0
        remoteModels.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)

            if (local == null) {
                if (remote.isDeleted) {
                    return@forEach
                }
                val insertedId = dao.insert(
                    ModelEntity(
                        modelName = remote.modelName,
                        boxNumber = remote.boxNumber,
                        additionalCompatible = remote.additionalCompatible,
                        type = remote.type,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        isFavorite = remote.isFavorite,
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
                        boxNumber = remote.boxNumber,
                        additionalCompatible = remote.additionalCompatible,
                        type = remote.type,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        isFavorite = remote.isFavorite,
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                    applied++
                }
            }
        }
        Log.d(MODEL_SYNC_TAG, "syncNow done applied=$applied")
    }

    suspend fun syncNowWithReport(): Result<ModelSyncReport> = runCatching {
        if (!syncSettingsStore.isInternetEnabled()) {
            error("Internet sync is disabled")
        }

        val uid = syncService.ensureSignedIn()
        val email = syncService.currentUserEmail()
        val now = System.currentTimeMillis()

        // Determine all UIDs to sync with (own + paired UID if an active pairing exists).
        val targetUids = buildList {
            add(uid)
            val pairing = getCurrentPairing()
            if (pairing != null && pairing.active && (pairing.syncsRemaining > 0 || pairing.isUnlimited)) {
                pairing.otherUid(uid)?.let { add(it) }
            }
        }.distinct()

        val cloudCount = syncService.getCloudModelCount(uid)
        val localCount = dao.countAll()
        if (cloudCount < localCount) {
            ensureAllModelsUploadedToCloud(uid)
        }

        val pending = dao.getPendingUploadModels()
        var failedPush = 0
        pending.forEach { local ->
            try {
                withTimeout(20_000) {
                    val remote = ModelRemoteDto(
                        modelName = local.modelName,
                        boxNumber = local.boxNumber,
                        additionalCompatible = local.additionalCompatible,
                        type = local.type,
                        isUserAdded = local.isUserAdded,
                        isDeleted = local.isDeleted,
                        isFavorite = local.isFavorite,
                        updatedAtRemote = now
                    )
                    // Push to own UID, and to the paired UID if active.
                    targetUids.forEach { targetUid ->
                        syncService.pushModel(targetUid, remote)
                        if (local.needsHardDeleteRemote) {
                            syncService.markAllModelDocsDeletedByName(targetUid, local.modelName, now)
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

        // Pull from all target UIDs and merge into local.
        val remoteModels = try {
            withTimeout(20_000) {
                targetUids.flatMap { targetUid -> syncService.pullModels(targetUid) }
                    .distinctBy { it.modelName }
            }
        } catch (e: TimeoutCancellationException) {
            error("Model pull timeout. Check internet or Firestore rules")
        }
        var applied = 0
        remoteModels.forEach { remote ->
            val local = dao.getByModelName(remote.modelName)
            if (local == null) {
                if (remote.isDeleted) {
                    return@forEach
                }
                val insertedId = dao.insert(
                    ModelEntity(
                        modelName = remote.modelName,
                        boxNumber = remote.boxNumber,
                        additionalCompatible = remote.additionalCompatible,
                        type = remote.type,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        isFavorite = remote.isFavorite,
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
                        boxNumber = remote.boxNumber,
                        additionalCompatible = remote.additionalCompatible,
                        type = remote.type,
                        isUserAdded = remote.isUserAdded,
                        isDeleted = remote.isDeleted,
                        isFavorite = remote.isFavorite,
                        updatedAtRemote = remote.updatedAtRemote,
                        lastSyncedAt = now
                    )
                    applied++
                }
            }
        }

        ModelSyncReport(
            uid = uid,
            email = email,
            pendingCount = pending.size,
            pulledCount = remoteModels.size,
            appliedCount = applied,
            failedPushCount = failedPush
        )
    }

    suspend fun getAllModelsSnapshot(): List<ModelEntity> = dao.getAllModelsSnapshot()

    suspend fun getActiveModelsSnapshot(): List<ModelEntity> = dao.getActiveModelsSnapshot()

    suspend fun getExistingBoxNumbers(): List<Int> = dao.getExistingBoxNumbers()

    suspend fun getNextBoxNumber(): Int = (dao.getMaxBoxNumber() ?: 0) + 1

    suspend fun backfillCurvedTypesForCurvedBoxRange() {
        dao.markTypeCurvedByBoxRange(
            startBox = CURVED_BOX_START,
            endBox = CURVED_BOX_END
        )
    }
}
