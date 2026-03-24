package com.botkin.zontdatahandler.mobile

import android.content.Context
import androidx.work.WorkManager
import com.botkin.zontdatahandler.mobile.data.ZontApiClient
import com.botkin.zontdatahandler.mobile.data.ZontDataLayerSync
import com.botkin.zontdatahandler.mobile.data.ZontPreferencesStore
import com.botkin.zontdatahandler.mobile.data.ZontSnapshotRepository
import com.botkin.zontdatahandler.mobile.work.AutoRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MobileAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferencesStore = ZontPreferencesStore(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val autoRefreshScheduler = AutoRefreshScheduler(workManager)
    private val apiClient = ZontApiClient()
    private val dataLayerSync = ZontDataLayerSync(appContext)

    val repository = ZontSnapshotRepository(
        preferencesStore = preferencesStore,
        apiClient = apiClient,
        dataLayerSync = dataLayerSync,
        autoRefreshScheduler = autoRefreshScheduler,
    )

    fun bootstrap() {
        appScope.launch {
            autoRefreshScheduler.syncWithStoredState(preferencesStore.readState())
        }
    }
}
