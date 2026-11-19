package com.neha.tempglass.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.neha.tempglass.data.BrandEntity
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

data class ConsumedLinkCodeResult(
    val pairing: DevicePairing,
    val email: String,
    val uid: String
)

class ModelSyncService(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private fun docIdForModelName(modelName: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(modelName.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun ensureSignedIn(): String {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isAnonymous) return currentUser.uid
        error("Please sign in with a shared account")
    }

    fun currentUserEmail(): String? = auth.currentUser?.email

    fun isSignedInWithSharedAccount(): Boolean {
        val user = auth.currentUser ?: return false
        return !user.isAnonymous
    }

    suspend fun signInWithEmailPassword(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun registerWithEmailPassword(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun pushModel(uid: String, dto: ModelRemoteDto) {
        firestore.collection("users")
            .document(uid)
            .collection("models")
            .document(docIdForModelName(dto.modelName))
            .set(
                mapOf(
                    "modelName" to dto.modelName,
                    "boxNumber" to dto.boxNumber,
                    "additionalCompatible" to dto.additionalCompatible,
                    "type" to dto.type,
                    "isUserAdded" to dto.isUserAdded,
                    "isDeleted" to dto.isDeleted,
                    "isFavorite" to dto.isFavorite,
                    "updatedAtRemote" to dto.updatedAtRemote
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun pullModels(uid: String): List<ModelRemoteDto> {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("models")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val modelName = doc.getString("modelName") ?: doc.id
            val boxNumber = doc.getLong("boxNumber")?.toInt() ?: return@mapNotNull null
            val additionalCompatible = doc.getString("additionalCompatible") ?: "[]"
            val type = doc.getString("type") ?: "Flat"
            val isUserAdded = doc.getBoolean("isUserAdded") ?: false
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            val isFavorite = doc.getBoolean("isFavorite") ?: false
            val updatedAtRemote = doc.getLong("updatedAtRemote") ?: 0L

            ModelRemoteDto(
                modelName = modelName,
                boxNumber = boxNumber,
                additionalCompatible = additionalCompatible,
                type = type,
                isUserAdded = isUserAdded,
                isDeleted = isDeleted,
                isFavorite = isFavorite,
                updatedAtRemote = updatedAtRemote
            )
        }
    }

    suspend fun markAllModelDocsDeletedByName(uid: String, modelName: String, updatedAtRemote: Long) {
        val collection = firestore.collection("users")
            .document(uid)
            .collection("models")

        val matches = collection
            .whereEqualTo("modelName", modelName)
            .get()
            .await()

        matches.documents.forEach { doc ->
            doc.reference.set(
                mapOf(
                    "modelName" to modelName,
                    "isDeleted" to true,
                    "updatedAtRemote" to updatedAtRemote
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun pushCaseModel(uid: String, dto: CaseModelRemoteDto) {
        firestore.collection("users")
            .document(uid)
            .collection("case_models")
            .document(docIdForModelName(dto.modelName))
            .set(
                mapOf(
                    "modelName" to dto.modelName,
                    "quantity" to dto.quantity,
                    "isUserAdded" to dto.isUserAdded,
                    "isDeleted" to dto.isDeleted,
                    "brandSerialNumber" to dto.brandSerialNumber,
                    "updatedAtRemote" to dto.updatedAtRemote
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun pullCaseModels(uid: String): List<CaseModelRemoteDto> {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("case_models")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val modelName = doc.getString("modelName") ?: doc.id
            val quantity = doc.getLong("quantity")?.toInt() ?: return@mapNotNull null
            val isUserAdded = doc.getBoolean("isUserAdded") ?: false
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            val brandSerialNumber = doc.getLong("brandSerialNumber")?.toInt() ?: 0
            val updatedAtRemote = doc.getLong("updatedAtRemote") ?: 0L

            CaseModelRemoteDto(
                modelName = modelName,
                quantity = quantity,
                isUserAdded = isUserAdded,
                isDeleted = isDeleted,
                brandSerialNumber = brandSerialNumber,
                updatedAtRemote = updatedAtRemote
            )
        }
    }

    suspend fun markAllCaseModelDocsDeletedByName(uid: String, modelName: String, updatedAtRemote: Long) {
        val collection = firestore.collection("users")
            .document(uid)
            .collection("case_models")

        val matches = collection
            .whereEqualTo("modelName", modelName)
            .get()
            .await()

        matches.documents.forEach { doc ->
            doc.reference.set(
                mapOf(
                    "modelName" to modelName,
                    "isDeleted" to true,
                    "updatedAtRemote" to updatedAtRemote
                ),
                SetOptions.merge()
            ).await()
        }
    }

    // ===== Batch upload and counts =====

    suspend fun getCloudModelCount(uid: String): Int {
        return runCatching {
            firestore.collection("users")
                .document(uid)
                .collection("models")
                .count()
                .get(AggregateSource.SERVER)
                .await()
                .count.toInt()
        }.getOrElse {
            firestore.collection("users")
                .document(uid)
                .collection("models")
                .limit(50)
                .get()
                .await()
                .size()
        }
    }

    suspend fun getCloudCaseModelCount(uid: String): Int {
        return runCatching {
            firestore.collection("users")
                .document(uid)
                .collection("case_models")
                .count()
                .get(AggregateSource.SERVER)
                .await()
                .count.toInt()
        }.getOrElse {
            firestore.collection("users")
                .document(uid)
                .collection("case_models")
                .limit(50)
                .get()
                .await()
                .size()
        }
    }

    suspend fun pushAllModelsBatch(uid: String, dtoList: List<ModelRemoteDto>) {
        if (dtoList.isEmpty()) return
        val collection = firestore.collection("users").document(uid).collection("models")
        dtoList.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { dto ->
                val docRef = collection.document(docIdForModelName(dto.modelName))
                batch.set(
                    docRef,
                    mapOf(
                        "modelName" to dto.modelName,
                        "boxNumber" to dto.boxNumber,
                        "additionalCompatible" to dto.additionalCompatible,
                        "type" to dto.type,
                        "isUserAdded" to dto.isUserAdded,
                        "isDeleted" to dto.isDeleted,
                        "isFavorite" to dto.isFavorite,
                        "updatedAtRemote" to dto.updatedAtRemote
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
        }
    }

    suspend fun pushAllCaseModelsBatch(uid: String, dtoList: List<CaseModelRemoteDto>) {
        if (dtoList.isEmpty()) return
        val collection = firestore.collection("users").document(uid).collection("case_models")
        dtoList.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { dto ->
                val docRef = collection.document(docIdForModelName(dto.modelName))
                batch.set(
                    docRef,
                    mapOf(
                        "modelName" to dto.modelName,
                        "quantity" to dto.quantity,
                        "isUserAdded" to dto.isUserAdded,
                        "isDeleted" to dto.isDeleted,
                        "brandSerialNumber" to dto.brandSerialNumber,
                        "updatedAtRemote" to dto.updatedAtRemote
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()
        }
    }

    // ===== Device linking =====

    suspend fun generateLinkCode(
        ownerUid: String,
        email: String,
        authSecret: String,
        ttlMillis: Long = 15 * 60 * 1000L
    ): String {
        val code = generateShortCode()
        val expiresAt = System.currentTimeMillis() + ttlMillis
        firestore.collection("link_codes")
            .document(code)
            .set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "email" to email,
                    "authSecret" to authSecret,
                    "expiresAt" to expiresAt,
                    "active" to true
                )
            )
            .await()
        return code
    }

    suspend fun generateLinkCode(ownerUid: String, ttlMillis: Long = 15 * 60 * 1000L): String {
        val email = currentUserEmail() ?: ""
        return generateLinkCode(ownerUid, email, "", ttlMillis)
    }

    suspend fun consumeLinkCodeWithAuth(code: String): ConsumedLinkCodeResult {
        val normalized = code.trim().uppercase()
        val docRef = firestore.collection("link_codes").document(normalized)
        val doc = docRef.get().await()

        if (!doc.exists()) {
            error("Invalid link code. Please check the code and try again.")
        }

        val ownerUid = doc.getString("ownerUid")
            ?: error("Invalid link code: missing owner UID.")
        val email = doc.getString("email")
            ?: error("Link code missing account email.")
        val authSecret = doc.getString("authSecret")
            ?: error("Link code missing account credentials.")
        val expiresAt = doc.getLong("expiresAt") ?: 0L
        val active = doc.getBoolean("active") ?: false

        if (!active || System.currentTimeMillis() > expiresAt) {
            error("Link code has expired. Please generate a new code on the primary device.")
        }

        // Authenticate secondary device with the primary user's account credentials
        val authResult = auth.signInWithEmailAndPassword(email, authSecret).await()
        val signedInUid = authResult.user?.uid ?: ownerUid

        val pairId = UUID.randomUUID().toString()
        val pairingRef = firestore.collection("pairings").document(pairId)
        val newPairing = mapOf(
            "ownerUid" to ownerUid,
            "linkedUid" to signedInUid,
            "syncsRemaining" to DevicePairing.UNLIMITED_SYNCS,
            "active" to true
        )

        firestore.runTransaction { transaction ->
            transaction.set(pairingRef, newPairing)
            transaction.update(docRef, "active", false)
        }.await()

        val pairing = DevicePairing(
            pairId = pairId,
            ownerUid = ownerUid,
            linkedUid = signedInUid,
            syncsRemaining = DevicePairing.UNLIMITED_SYNCS,
            active = true
        )

        return ConsumedLinkCodeResult(
            pairing = pairing,
            email = email,
            uid = signedInUid
        )
    }

    suspend fun consumeLinkCode(code: String, linkedUid: String): DevicePairing {
        val normalized = code.trim().uppercase()
        val docRef = firestore.collection("link_codes").document(normalized)
        val doc = docRef.get().await()

        if (!doc.exists()) {
            error("Invalid link code")
        }

        val ownerUid = doc.getString("ownerUid")
            ?: error("Invalid link code")
        val expiresAt = doc.getLong("expiresAt") ?: 0L
        val active = doc.getBoolean("active") ?: false

        if (!active || System.currentTimeMillis() > expiresAt) {
            error("Link code has expired")
        }

        val pairId = UUID.randomUUID().toString()
        val pairingRef = firestore.collection("pairings").document(pairId)
        val newPairing = mapOf(
            "ownerUid" to ownerUid,
            "linkedUid" to linkedUid,
            "syncsRemaining" to DevicePairing.UNLIMITED_SYNCS,
            "active" to true
        )

        firestore.runTransaction { transaction ->
            transaction.set(pairingRef, newPairing)
            transaction.update(docRef, "active", false)
        }.await()

        return DevicePairing(
            pairId = pairId,
            ownerUid = ownerUid,
            linkedUid = linkedUid,
            syncsRemaining = DevicePairing.UNLIMITED_SYNCS,
            active = true
        )
    }

    suspend fun getPairing(pairId: String): DevicePairing? {
        val doc = firestore.collection("pairings").document(pairId).get().await()
        return if (doc.exists()) DevicePairing.fromDocument(pairId, doc) else null
    }

    /**
     * Atomically decrements syncsRemaining for the given pairing while it is active.
     * Returns the pairing after decrement, or null if the pairing is exhausted/inactive.
     * For unlimited pairings (syncsRemaining < 0), remains active without decrementing.
     */
    suspend fun consumeSyncSlot(pairId: String): DevicePairing? {
        val pairingRef = firestore.collection("pairings").document(pairId)
        var result: DevicePairing? = null

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(pairingRef)
            if (!snapshot.exists()) {
                return@runTransaction result
            }

            val active = snapshot.getBoolean("active") ?: false
            val syncsRemaining = snapshot.getLong("syncsRemaining")?.toInt() ?: DevicePairing.UNLIMITED_SYNCS

            if (!active) {
                return@runTransaction result
            }

            // Unlimited pairing
            if (syncsRemaining < 0) {
                result = DevicePairing(
                    pairId = pairId,
                    ownerUid = snapshot.getString("ownerUid") ?: "",
                    linkedUid = snapshot.getString("linkedUid") ?: "",
                    syncsRemaining = syncsRemaining,
                    active = true
                )
                return@runTransaction result
            }

            if (syncsRemaining == 0) {
                return@runTransaction result
            }

            val newRemaining = syncsRemaining - 1
            transaction.update(pairingRef, "syncsRemaining", newRemaining)
            if (newRemaining == 0) {
                transaction.update(pairingRef, "active", false)
            }

            result = DevicePairing(
                pairId = pairId,
                ownerUid = snapshot.getString("ownerUid") ?: "",
                linkedUid = snapshot.getString("linkedUid") ?: "",
                syncsRemaining = newRemaining,
                active = newRemaining > 0
            )
            result
        }.await()

        return result
    }

    // ===== Brand sync =====

    suspend fun pushBrand(uid: String, brand: BrandEntity) {
        val docId = docIdForModelName(brand.name)
        firestore.collection("users")
            .document(uid)
            .collection("brands")
            .document(docId)
            .set(
                mapOf(
                    "name" to brand.name,
                    "isUserAdded" to brand.isUserAdded,
                    "createdAt" to brand.createdAt
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun pullBrands(uid: String): List<BrandEntity> {
        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("brands")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val isUserAdded = doc.getBoolean("isUserAdded") ?: true
            val createdAt = doc.getLong("createdAt") ?: 0L
            BrandEntity(name = name, isUserAdded = isUserAdded, createdAt = createdAt)
        }
    }

    private fun generateShortCode(): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { charset.random() }
            .joinToString("")
    }
}
