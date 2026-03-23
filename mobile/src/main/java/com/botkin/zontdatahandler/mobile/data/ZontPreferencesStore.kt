package com.botkin.zontdatahandler.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.botkin.zontdatahandler.shared.SnapshotJson
import com.botkin.zontdatahandler.shared.ZontSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mobileDataStore: DataStore<Preferences> by preferencesDataStore(name = "zont_mobile_store")

class ZontPreferencesStore(
    private val appContext: Context,
) {
    val stateFlow: Flow<StoredMobileState> = appContext.mobileDataStore.data.map { preferences ->
        StoredMobileState(
            settings = ZontSettings(
                client = preferences[Keys.client].orEmpty(),
                token = preferences[Keys.token].orEmpty(),
                deviceId = preferences[Keys.deviceId].orEmpty(),
                zone = preferences[Keys.zone] ?: 1,
                refreshIntervalMinutes = preferences[Keys.refreshIntervalMinutes]
                    ?: ZontSnapshot.DEFAULT_REFRESH_INTERVAL_MINUTES,
            ).sanitized(),
            snapshot = SnapshotJson.decodeOrNull(preferences[Keys.snapshotJson]),
            lastSuccessfulRefreshEpochSeconds = preferences[Keys.lastSuccessfulRefreshEpochSeconds],
        )
    }

    suspend fun readState(): StoredMobileState = stateFlow.first()

    suspend fun saveSettings(settings: ZontSettings) {
        val sanitized = settings.sanitized()
        appContext.mobileDataStore.edit { preferences ->
            preferences[Keys.client] = sanitized.client
            preferences[Keys.token] = sanitized.token
            preferences[Keys.deviceId] = sanitized.deviceId
            preferences[Keys.zone] = sanitized.zone
            preferences[Keys.refreshIntervalMinutes] = sanitized.refreshIntervalMinutes
        }
    }

    suspend fun saveSnapshot(
        snapshot: ZontSnapshot,
        lastSuccessfulRefreshEpochSeconds: Long,
    ) {
        appContext.mobileDataStore.edit { preferences ->
            preferences[Keys.snapshotJson] = SnapshotJson.encode(snapshot)
            preferences[Keys.lastSuccessfulRefreshEpochSeconds] = lastSuccessfulRefreshEpochSeconds
        }
    }

    suspend fun saveFailure(
        previousSnapshot: ZontSnapshot?,
        settings: ZontSettings,
        errorMessage: String,
    ) {
        val snapshotToPersist = (previousSnapshot ?: ZontSnapshot(
            deviceId = settings.deviceId.takeIf { it.isNotBlank() },
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            isStale = true,
        )).copy(
            deviceId = previousSnapshot?.deviceId ?: settings.deviceId.takeIf { it.isNotBlank() },
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            errorMessage = errorMessage,
            isStale = true,
        )
        appContext.mobileDataStore.edit { preferences ->
            preferences[Keys.snapshotJson] = SnapshotJson.encode(snapshotToPersist)
        }
    }

    private object Keys {
        val client = stringPreferencesKey("client")
        val token = stringPreferencesKey("token")
        val deviceId = stringPreferencesKey("device_id")
        val zone = intPreferencesKey("zone")
        val refreshIntervalMinutes = intPreferencesKey("refresh_interval_minutes")
        val snapshotJson = stringPreferencesKey("snapshot_json")
        val lastSuccessfulRefreshEpochSeconds = longPreferencesKey("last_successful_refresh_epoch_seconds")
    }
}
