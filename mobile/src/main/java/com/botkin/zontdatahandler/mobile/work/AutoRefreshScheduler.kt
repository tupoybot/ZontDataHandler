package com.botkin.zontdatahandler.mobile.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.botkin.zontdatahandler.mobile.data.StoredMobileState
import com.botkin.zontdatahandler.mobile.data.ZontSettings
import java.util.concurrent.TimeUnit

class AutoRefreshScheduler(
    private val workManager: WorkManager,
) {
    fun ensureScheduled(settings: ZontSettings) {
        enqueue(settings, ExistingWorkPolicy.KEEP)
    }

    fun scheduleFromSettings(settings: ZontSettings) {
        enqueue(settings, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleNextAfterWorker(settings: ZontSettings) {
        enqueue(settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun syncWithStoredState(state: StoredMobileState) {
        if (state.autoRefreshPaused || !state.settings.isReadyForRefresh) {
            cancel()
            return
        }

        ensureScheduled(state.settings)
    }

    private fun enqueue(
        settings: ZontSettings,
        policy: ExistingWorkPolicy,
    ) {
        if (!settings.isReadyForRefresh) {
            cancel()
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
            policy,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "zont_auto_refresh"
    }
}
