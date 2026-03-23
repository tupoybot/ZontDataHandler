package com.botkin.zontdatahandler.mobile.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.botkin.zontdatahandler.mobile.data.ZontPreferencesStore
import com.botkin.zontdatahandler.mobile.data.ZontSettings
import java.util.concurrent.TimeUnit

class AutoRefreshScheduler(
    context: Context,
    private val workManager: WorkManager,
) {
    private val appContext = context.applicationContext

    fun syncWithSettings(settings: ZontSettings) {
        if (!settings.isReadyForRefresh) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val request = OneTimeWorkRequestBuilder<AutoRefreshWorker>()
            .setInitialDelay(settings.refreshIntervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(UNIQUE_WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun syncWithStoredSettings(preferencesStore: ZontPreferencesStore) {
        syncWithSettings(preferencesStore.readState().settings)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "zont_auto_refresh"
    }
}
