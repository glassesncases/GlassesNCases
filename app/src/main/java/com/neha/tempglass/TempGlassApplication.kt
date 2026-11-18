package com.neha.tempglass

import android.app.Application
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.neha.tempglass.data.AppDatabase
import com.neha.tempglass.data.InitialCaseDataParser
import com.neha.tempglass.data.InitialDataParser
import com.neha.tempglass.repository.BrandRepository
import com.neha.tempglass.repository.CaseRepository
import com.neha.tempglass.repository.ModelRepository
import com.neha.tempglass.sync.ModelSyncService
import com.neha.tempglass.sync.SyncSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val CURATED_USER_ADDED_MODELS = listOf(
    "Oppo A16" to 46,
    "Oppo A17" to 46,
    "Oppo F17 Pro" to 48,
    "Realme C31" to 46,
    "Realme C35" to 46,
    "Realme C61" to 33,
    "Realme Neo7" to 52,
    "Vivo T3 5G" to 52,
    "Vivo V23" to 42,
    "Vivo Y16" to 46,
    "Vivo Y200e 5G" to 45
)

class TempGlassApplication : Application() {

    lateinit var repository: ModelRepository
        private set

    lateinit var caseRepository: CaseRepository
        private set

    lateinit var brandRepository: BrandRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(this)
        val syncSettingsStore = SyncSettingsStore(this)
        val syncService = ModelSyncService(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
        repository = ModelRepository(
            dao = database.modelDao(),
            syncService = syncService,
            syncSettingsStore = syncSettingsStore,
            deviceIdProvider = {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
            }
        )
        caseRepository = CaseRepository(
            dao = database.caseModelDao(),
            syncService = syncService,
            syncSettingsStore = syncSettingsStore,
            deviceIdProvider = {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
            }
        )
        brandRepository = BrandRepository(
            dao = database.brandDao()
        )

        applicationScope.launch {
            val initialModels = InitialDataParser.parse(this@TempGlassApplication)
            val initialCaseModels = InitialCaseDataParser.parse(this@TempGlassApplication)

            brandRepository.getAllBrands()
            repository.syncInitialModels(initialModels)
            repository.normalizeLegacyBrandNames()
            repository.ensureUserAddedModels(CURATED_USER_ADDED_MODELS)
            repository.backfillCurvedTypesForCurvedBoxRange()

            caseRepository.preloadIfEmpty(initialCaseModels)
        }
    }
}
