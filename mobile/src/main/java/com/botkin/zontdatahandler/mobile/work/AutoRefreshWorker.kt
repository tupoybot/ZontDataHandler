package com.botkin.zontdatahandler.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.botkin.zontdatahandler.mobile.data.RefreshOutcome
import com.botkin.zontdatahandler.mobile.mobileAppContainer

class AutoRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return when (val outcome = applicationContext.mobileAppContainer.repository.refreshFromWorker()) {
            is RefreshOutcome.Success -> Result.success()
            is RefreshOutcome.Failure -> if (outcome.shouldRetry) Result.retry() else Result.failure()
        }
    }
}
