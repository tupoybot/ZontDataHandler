package com.botkin.zontdatahandler.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.botkin.zontdatahandler.mobile.mobileAppContainer

class AutoRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        applicationContext.mobileAppContainer.repository.refreshFromWorker()
        return Result.success()
    }
}
