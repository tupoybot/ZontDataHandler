package com.botkin.zontdatahandler.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotFormattingTest {
    @Test
    fun `single metric placeholder stays clean when snapshot is stale and expired`() {
        val snapshot = staleSnapshot(
            targetTemperature = 22.0,
            updatedAtEpochSeconds = nowEpochSeconds - 601L,
            refreshIntervalMinutes = 1,
        )

        assertEquals("--", snapshot.metricComplicationText(SnapshotMetric.TARGET_TEMPERATURE, nowEpochSeconds))
    }

    @Test
    fun `single metric keeps stale marker before expiration threshold`() {
        val snapshot = staleSnapshot(
            targetTemperature = 22.0,
            updatedAtEpochSeconds = nowEpochSeconds - 180L,
            refreshIntervalMinutes = 1,
        )

        assertEquals("!22°", snapshot.metricComplicationText(SnapshotMetric.TARGET_TEMPERATURE, nowEpochSeconds))
    }

    @Test
    fun `five minute refresh keeps stale value visible after ten minutes and hides it after fifty`() {
        val staleButVisibleSnapshot = staleSnapshot(
            targetTemperature = 22.0,
            updatedAtEpochSeconds = nowEpochSeconds - 601L,
            refreshIntervalMinutes = 5,
        )
        val expiredSnapshot = staleSnapshot(
            targetTemperature = 22.0,
            updatedAtEpochSeconds = nowEpochSeconds - 3_001L,
            refreshIntervalMinutes = 5,
        )

        assertEquals("!22°", staleButVisibleSnapshot.metricComplicationText(SnapshotMetric.TARGET_TEMPERATURE, nowEpochSeconds))
        assertEquals("--", expiredSnapshot.metricComplicationText(SnapshotMetric.TARGET_TEMPERATURE, nowEpochSeconds))
    }

    @Test
    fun `metric pair moves stale marker to secondary value when primary is missing and value is not expired`() {
        val snapshot = staleSnapshot(
            roomSetpointTemperature = 23.0,
            updatedAtEpochSeconds = nowEpochSeconds - 180L,
            refreshIntervalMinutes = 1,
        )

        val presentation = snapshot.metricPairPresentation(
            primaryMetric = SnapshotMetric.ROOM_TEMPERATURE,
            secondaryMetric = SnapshotMetric.ROOM_SETPOINT_TEMPERATURE,
            nowEpochSeconds = nowEpochSeconds,
        )

        assertEquals("--", presentation.primaryText)
        assertEquals("!→23°", presentation.secondaryText)
    }

    @Test
    fun `metric pair keeps both placeholders without stale marker when values are missing`() {
        val snapshot = staleSnapshot(
            updatedAtEpochSeconds = nowEpochSeconds - 180L,
            refreshIntervalMinutes = 1,
        )

        val presentation = snapshot.metricPairPresentation(
            primaryMetric = SnapshotMetric.ROOM_TEMPERATURE,
            secondaryMetric = SnapshotMetric.ROOM_SETPOINT_TEMPERATURE,
            nowEpochSeconds = nowEpochSeconds,
        )

        assertEquals("--", presentation.primaryText)
        assertEquals("→--", presentation.secondaryText)
    }

    @Test
    fun `combined long text marks the first non-placeholder line before expiration threshold`() {
        val snapshot = staleSnapshot(
            targetTemperature = 22.0,
            coolantTemperature = 37.0,
            updatedAtEpochSeconds = nowEpochSeconds - 180L,
            refreshIntervalMinutes = 1,
        )

        assertEquals("--·--\n!22°·37°", snapshot.combinedLongText(nowEpochSeconds))
    }

    @Test
    fun `combined short presentation keeps placeholder text clean and marks secondary title when needed`() {
        val snapshot = staleSnapshot(
            targetTemperature = 22.0,
            coolantTemperature = 37.0,
            updatedAtEpochSeconds = nowEpochSeconds - 180L,
            refreshIntervalMinutes = 1,
        )

        assertEquals("--·--", snapshot.combinedShortText(nowEpochSeconds))
        assertEquals("!22°·37°", snapshot.combinedShortTitle(nowEpochSeconds))
    }

    @Test
    fun `combined presentation collapses expired stale values to placeholders`() {
        val snapshot = staleSnapshot(
            roomTemperature = 21.0,
            burnerModulation = 42.0,
            targetTemperature = 22.0,
            coolantTemperature = 37.0,
            updatedAtEpochSeconds = nowEpochSeconds - 601L,
            refreshIntervalMinutes = 1,
        )

        assertEquals("--·--\n--·--", snapshot.combinedLongText(nowEpochSeconds))
        assertEquals("--·--", snapshot.combinedShortText(nowEpochSeconds))
        assertEquals("--·--", snapshot.combinedShortTitle(nowEpochSeconds))
    }

    private fun staleSnapshot(
        roomTemperature: Double? = null,
        roomSetpointTemperature: Double? = null,
        burnerModulation: Double? = null,
        targetTemperature: Double? = null,
        coolantTemperature: Double? = null,
        updatedAtEpochSeconds: Long = 0L,
        refreshIntervalMinutes: Int = 5,
    ): ZontSnapshot {
        return ZontSnapshot(
            roomTemperature = roomTemperature,
            roomSetpointTemperature = roomSetpointTemperature,
            burnerModulation = burnerModulation,
            targetTemperature = targetTemperature,
            coolantTemperature = coolantTemperature,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            refreshIntervalMinutes = refreshIntervalMinutes,
            isStale = false,
        )
    }

    private companion object {
        const val nowEpochSeconds = 1_000L
    }
}
