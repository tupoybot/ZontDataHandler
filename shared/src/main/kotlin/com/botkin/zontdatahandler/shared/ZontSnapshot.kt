package com.botkin.zontdatahandler.shared

import kotlinx.serialization.Serializable

@Serializable
data class ZontSnapshot(
    val roomTemperature: Double? = null,
    val roomSetpointTemperature: Double? = null,
    val burnerModulation: Double? = null,
    val targetTemperature: Double? = null,
    val coolantTemperature: Double? = null,
    val deviceId: String? = null,
    val updatedAtEpochSeconds: Long = 0L,
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_INTERVAL_MINUTES,
    val roomTemperatureMissingStreak: Int = 0,
    val roomSetpointTemperatureMissingStreak: Int = 0,
    val burnerModulationMissingStreak: Int = 0,
    val targetTemperatureMissingStreak: Int = 0,
    val coolantTemperatureMissingStreak: Int = 0,
    val isStale: Boolean = true,
    val errorMessage: String? = null,
    val sourceSummary: String? = null,
) {
    val hasAnyMetric: Boolean
        get() = roomTemperature != null ||
            roomSetpointTemperature != null ||
            burnerModulation != null ||
            targetTemperature != null ||
            coolantTemperature != null

    companion object {
        const val MIN_REFRESH_INTERVAL_MINUTES = 1
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 5
    }
}
