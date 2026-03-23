package com.botkin.zontdatahandler.mobile.data

import android.content.Context
import com.botkin.zontdatahandler.shared.SnapshotJson
import com.botkin.zontdatahandler.shared.SnapshotTransport
import com.botkin.zontdatahandler.shared.ZontSnapshot
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZontDataLayerSync(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun pushSnapshot(snapshot: ZontSnapshot) = withContext(Dispatchers.IO) {
        val request = PutDataMapRequest.create(SnapshotTransport.dataPath).apply {
            dataMap.putString(SnapshotTransport.snapshotJsonKey, SnapshotJson.encode(snapshot))
            dataMap.putLong(SnapshotTransport.syncedAtKey, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Tasks.await(Wearable.getDataClient(appContext).putDataItem(request))
    }
}
