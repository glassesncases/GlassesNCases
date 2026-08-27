package com.neha.tempglass.sync

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    private val _internetEnabled = MutableStateFlow(prefs.getBoolean(KEY_INTERNET_ENABLED, false))
    val internetEnabled: Flow<Boolean> = _internetEnabled.asStateFlow()
    private val _useScreenLock = MutableStateFlow(prefs.getBoolean(KEY_USE_SCREEN_LOCK, false))
    val useScreenLock: Flow<Boolean> = _useScreenLock.asStateFlow()
    private val _pairId = MutableStateFlow(prefs.getString(KEY_PAIR_ID, null))
    val pairId: Flow<String?> = _pairId.asStateFlow()
    private val _otherUid = MutableStateFlow(prefs.getString(KEY_OTHER_UID, null))
    val otherUid: Flow<String?> = _otherUid.asStateFlow()

    fun isInternetEnabled(): Boolean = _internetEnabled.value

    fun setInternetEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERNET_ENABLED, enabled).apply()
        _internetEnabled.value = enabled
    }

    fun isUseScreenLockEnabled(): Boolean = _useScreenLock.value

    fun setUseScreenLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SCREEN_LOCK, enabled).apply()
        _useScreenLock.value = enabled
    }

    fun currentPairId(): String? = _pairId.value

    fun currentOtherUid(): String? = _otherUid.value

    fun setPairing(pairId: String?, otherUid: String?) {
        prefs.edit()
            .putString(KEY_PAIR_ID, pairId)
            .putString(KEY_OTHER_UID, otherUid)
            .apply()
        _pairId.value = pairId
        _otherUid.value = otherUid
    }

    companion object {
        private const val KEY_INTERNET_ENABLED = "internet_enabled"
        private const val KEY_USE_SCREEN_LOCK = "use_screen_lock"
        private const val KEY_PAIR_ID = "pair_id"
        private const val KEY_OTHER_UID = "other_uid"
    }
}
