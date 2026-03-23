package com.botkin.zontdatahandler.wear.data

import com.botkin.zontdatahandler.shared.SnapshotJson
import com.botkin.zontdatahandler.shared.SnapshotTransport
import com.botkin.zontdatahandler.wear.complications.ComplicationUpdater
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class ZontWearListenerService : WearableListenerService() {
    private val snapshotStore by lazy { WearSnapshotStore(this) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) {
                return@forEach
            }
            if (event.dataItem.uri.path != SnapshotTransport.dataPath) {
                return@forEach
            }

            val rawJson = DataMapItem.fromDataItem(event.dataItem)
                .dataMap
                .getString(SnapshotTransport.snapshotJsonKey)
            val snapshot = SnapshotJson.decodeOrNull(rawJson) ?: return@forEach
            snapshotStore.writeSnapshot(snapshot)
            ComplicationUpdater.requestAll(this)
        }
    }
}
