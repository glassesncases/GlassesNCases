package com.neha.tempglass.sync

import com.google.firebase.firestore.DocumentSnapshot

data class DevicePairing(
    val pairId: String,
    val ownerUid: String,
    val linkedUid: String,
    val syncsRemaining: Int,
    val active: Boolean
) {
    fun otherUid(myUid: String): String? = when (myUid) {
        ownerUid -> linkedUid
        linkedUid -> ownerUid
        else -> null
    }

    companion object {
        fun fromDocument(pairId: String, doc: DocumentSnapshot): DevicePairing? {
            val ownerUid = doc.getString("ownerUid") ?: return null
            val linkedUid = doc.getString("linkedUid") ?: return null
            val syncsRemaining = doc.getLong("syncsRemaining")?.toInt() ?: 0
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