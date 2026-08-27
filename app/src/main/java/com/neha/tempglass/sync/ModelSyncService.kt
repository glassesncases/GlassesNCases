package com.neha.tempglass.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

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

    // ===== Device linking =====

    suspend fun generateLinkCode(ownerUid: String, ttlMillis: Long = 15 * 60 * 1000L): String {
        val code = generateShortCode()
        val expiresAt = System.currentTimeMillis() + ttlMillis
        firestore.collection("link_codes")
            .document(code)
            .set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "expiresAt" to expiresAt,
                    "active" to true
                )
            )
            .await()
        return code
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
            "syncsRemaining" to 5,
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
            syncsRemaining = 5,
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
            val syncsRemaining = snapshot.getLong("syncsRemaining")?.toInt() ?: 0

            if (!active || syncsRemaining <= 0) {
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

    private fun generateShortCode(): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { charset.random() }
            .joinToString("")
    }
}
