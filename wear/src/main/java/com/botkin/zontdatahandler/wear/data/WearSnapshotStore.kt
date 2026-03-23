package com.botkin.zontdatahandler.wear.data

import android.content.Context
import com.botkin.zontdatahandler.shared.SnapshotJson
import com.botkin.zontdatahandler.shared.ZontSnapshot

class WearSnapshotStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readSnapshot(): ZontSnapshot? {
        return SnapshotJson.decodeOrNull(preferences.getString(KEY_SNAPSHOT_JSON, null))
    }

    fun writeSnapshot(snapshot: ZontSnapshot) {
        preferences.edit()
            .putString(KEY_SNAPSHOT_JSON, SnapshotJson.encode(snapshot))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "zont_wear_snapshot_store"
        private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    }
}
