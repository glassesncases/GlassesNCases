package com.neha.tempglass.sync

import com.google.firebase.firestore.DocumentSnapshot

data class DevicePairing(
    val pairId: String,
    val ownerUid: String,
    val linkedUid: String,
    val syncsRemaining: Int,
    val active: Boolean
) {
    fun otherUid(myUid: String): String? = when {
        ownerUid == linkedUid -> ownerUid
        myUid == ownerUid -> linkedUid
        myUid == linkedUid -> ownerUid
        else -> null
    }

    val isUnlimited: Boolean get() = syncsRemaining < 0 || syncsRemaining >= 9999

    companion object {
        const val UNLIMITED_SYNCS = -1

        fun fromDocument(pairId: String, doc: DocumentSnapshot): DevicePairing? {
            val ownerUid = doc.getString("ownerUid") ?: return null
            val linkedUid = doc.getString("linkedUid") ?: return null
            val syncsRemaining = doc.getLong("syncsRemaining")?.toInt() ?: UNLIMITED_SYNCS
            val active = doc.getBoolean("active") ?: true
            return DevicePairing(
                pairId = pairId,
                ownerUid = ownerUid,
                linkedUid = linkedUid,
                syncsRemaining = syncsRemaining,
                active = active
            )
        }
    }
}
