package com.botkin.zontdatahandler.wear.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import com.botkin.zontdatahandler.shared.SnapshotMetric
import com.botkin.zontdatahandler.shared.ZontSnapshot
import com.botkin.zontdatahandler.wear.R

class CombinedComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildCombinedData(snapshot, type)
    }
}

class CombinedColorComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildCombinedColorData(snapshot, type)
    }
}

class RoomTemperatureComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricData(
            snapshot = snapshot,
            metric = SnapshotMetric.ROOM_TEMPERATURE,
            type = type,
            iconResId = R.drawable.ic_complication_room,
        )
    }
}

class BurnerModulationComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricData(
            snapshot = snapshot,
            metric = SnapshotMetric.BURNER_MODULATION,
            type = type,
            iconResId = R.drawable.ic_complication_burner,
            rangedValueBounds = 0f..100f,
        )
    }
}

class TargetTemperatureComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricData(
            snapshot = snapshot,
            metric = SnapshotMetric.TARGET_TEMPERATURE,
            type = type,
            iconResId = R.drawable.ic_complication_target,
        )
    }
}

class CoolantTemperatureComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricData(
            snapshot = snapshot,
            metric = SnapshotMetric.COOLANT_TEMPERATURE,
            type = type,
            iconResId = R.drawable.ic_complication_coolant,
        )
    }
}

class TargetCoolantComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildDualTemperatureData(snapshot, type)
    }
}

class RoomSetpointComplicationService : BaseSnapshotComplicationService() {
    override fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricPairData(
            snapshot = snapshot,
            type = type,
            primaryMetric = SnapshotMetric.ROOM_TEMPERATURE,
            secondaryMetric = SnapshotMetric.ROOM_SETPOINT_TEMPERATURE,
            iconResId = R.drawable.ic_complication_room,
            unavailableDescription = "Room temperature and room air setpoint unavailable",
        )
    }
}
