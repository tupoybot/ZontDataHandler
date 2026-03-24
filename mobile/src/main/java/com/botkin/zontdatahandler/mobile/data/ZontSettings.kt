package com.botkin.zontdatahandler.mobile.data

import com.botkin.zontdatahandler.shared.ZontSnapshot

data class ZontSettings(
    val client: String = "",
    val token: String = "",
    val deviceId: String = "",
    val zone: Int = 1,
    val refreshIntervalMinutes: Int = ZontSnapshot.DEFAULT_REFRESH_INTERVAL_MINUTES,
) {
    val isReadyForRefresh: Boolean
        get() = client.isNotBlank() && token.isNotBlank() && deviceId.isNotBlank()

    fun sanitized(): ZontSettings {
        return copy(
            client = client.trim(),
            token = token.trim(),
            deviceId = deviceId.trim(),
            zone = zone.coerceAtLeast(1),
            refreshIntervalMinutes = refreshIntervalMinutes.coerceAtLeast(ZontSnapshot.MIN_REFRESH_INTERVAL_MINUTES),
        )
    }
}

data class StoredMobileState(
    val settings: ZontSettings = ZontSettings(),
    val snapshot: com.botkin.zontdatahandler.shared.ZontSnapshot? = null,
    val lastSuccessfulRefreshEpochSeconds: Long? = null,
    val autoRefreshPaused: Boolean = false,
)
