package com.neha.tempglass

import com.neha.tempglass.sync.DevicePairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingTest {

    @Test
    fun testUnlimitedPairing() {
        val pairing = DevicePairing(
            pairId = "test-pair",
            ownerUid = "uid1",
            linkedUid = "uid1",
            syncsRemaining = DevicePairing.UNLIMITED_SYNCS,
            active = true
        )

        assertTrue(pairing.isUnlimited)
        assertEquals("uid1", pairing.otherUid("uid1"))
    }

    @Test
    fun testCrossDeviceSameAccountPairing() {
        val pairing = DevicePairing(
            pairId = "test-pair-2",
            ownerUid = "primary_account",
            linkedUid = "primary_account",
            syncsRemaining = -1,
            active = true
        )

        assertTrue(pairing.isUnlimited)
        assertEquals("primary_account", pairing.otherUid("primary_account"))
    }

    @Test
    fun testCrossDeviceDifferentAccountPairing() {
        val pairing = DevicePairing(
            pairId = "test-pair-3",
            ownerUid = "userA",
            linkedUid = "userB",
            syncsRemaining = -1,
            active = true
        )

        assertTrue(pairing.isUnlimited)
        assertEquals("userB", pairing.otherUid("userA"))
        assertEquals("userA", pairing.otherUid("userB"))
    }
}
