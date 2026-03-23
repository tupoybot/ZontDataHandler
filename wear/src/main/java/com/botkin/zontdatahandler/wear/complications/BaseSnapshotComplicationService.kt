package com.botkin.zontdatahandler.wear.complications

import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.botkin.zontdatahandler.shared.SnapshotMetric
import com.botkin.zontdatahandler.shared.combinedContentDescription
import com.botkin.zontdatahandler.shared.combinedLongText
import com.botkin.zontdatahandler.shared.combinedShortText
import com.botkin.zontdatahandler.shared.combinedShortTitle
import com.botkin.zontdatahandler.shared.metricPairPresentation
import com.botkin.zontdatahandler.shared.metricComplicationContentDescription
import com.botkin.zontdatahandler.shared.metricComplicationText
import com.botkin.zontdatahandler.shared.withComputedStaleness
import com.botkin.zontdatahandler.shared.ZontSnapshot
import com.botkin.zontdatahandler.wear.R
import com.botkin.zontdatahandler.wear.data.WearSnapshotStore

abstract class BaseSnapshotComplicationService : SuspendingComplicationDataSourceService() {
    private val snapshotStore by lazy { WearSnapshotStore(this) }
    private val overviewLegendImageRenderer by lazy { OverviewLegendImageRenderer(this) }

    override suspend fun onComplicationRequest(
        request: ComplicationRequest,
    ): ComplicationData? {
        val snapshot = snapshotStore.readSnapshot()?.withComputedStaleness(currentEpochSeconds())
        return buildComplicationData(snapshot, request.complicationType)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return buildComplicationData(previewSnapshot, type)
    }

    protected abstract fun buildComplicationData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData?

    protected fun buildCombinedData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
        @DrawableRes iconResId: Int = R.drawable.ic_complication_combined,
    ): ComplicationData? {
        val icon = monochromaticImage(iconResId)
        val longText = snapshot?.combinedLongText(currentEpochSeconds()) ?: combinedLongPlaceholder
        val shortText = snapshot?.combinedShortText(currentEpochSeconds()) ?: shortPairPlaceholder
        val shortTitle = snapshot?.combinedShortTitle(currentEpochSeconds()) ?: shortPairPlaceholder
        val description = snapshot?.combinedContentDescription(currentEpochSeconds())
            ?: "Combined ZONT metrics unavailable"

        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = plainText(longText),
                contentDescription = plainText(description),
            ).setMonochromaticImage(icon).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = plainText(shortText),
                contentDescription = plainText(description),
            ).setMonochromaticImage(icon)
                .setTitle(plainText(shortTitle))
                .build()

            else -> null
        }
    }

    protected fun buildCombinedLegendData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        val currentEpochSeconds = currentEpochSeconds()
        val description = snapshot?.combinedContentDescription(currentEpochSeconds)
            ?: "Combined ZONT metrics unavailable"

        return when (type) {
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                overviewLegendSmallImage(snapshot, currentEpochSeconds),
                plainText(description),
            ).build()

            ComplicationType.PHOTO_IMAGE -> PhotoImageComplicationData.Builder(
                overviewLegendBitmapIcon(snapshot, currentEpochSeconds),
                plainText(description),
            ).build()

            else -> null
        }
    }

    protected fun buildMetricData(
        snapshot: ZontSnapshot?,
        metric: SnapshotMetric,
        type: ComplicationType,
        @DrawableRes iconResId: Int,
        rangedValueBounds: ClosedFloatingPointRange<Float>? = null,
    ): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> buildShortMetricData(
                snapshot = snapshot,
                metric = metric,
                iconResId = iconResId,
            )

            ComplicationType.RANGED_VALUE -> rangedValueBounds?.let {
                buildRangedMetricData(
                    snapshot = snapshot,
                    metric = metric,
                    iconResId = iconResId,
                    bounds = it,
                )
            }

            else -> null
        }
    }

    private fun buildShortMetricData(
        snapshot: ZontSnapshot?,
        metric: SnapshotMetric,
        @DrawableRes iconResId: Int,
    ): ComplicationData {
        val icon = monochromaticImage(iconResId)
        val text = snapshot?.metricComplicationText(metric, currentEpochSeconds()) ?: placeholderText
        val description = snapshot?.metricComplicationContentDescription(metric, currentEpochSeconds())
            ?: "${metric.longTitle} unavailable"

        return ShortTextComplicationData.Builder(
            text = plainText(text),
            contentDescription = plainText(description),
        ).setMonochromaticImage(icon).build()
    }

    private fun buildRangedMetricData(
        snapshot: ZontSnapshot?,
        metric: SnapshotMetric,
        @DrawableRes iconResId: Int,
        bounds: ClosedFloatingPointRange<Float>,
    ): ComplicationData {
        val icon = monochromaticImage(iconResId)
        val currentEpochSeconds = currentEpochSeconds()
        val rawValue = snapshot?.metricValue(metric)?.toFloat()
        val rangedValue = rawValue?.coerceIn(bounds.start, bounds.endInclusive) ?: bounds.start
        val text = snapshot?.metricComplicationText(metric, currentEpochSeconds) ?: placeholderText
        val description = snapshot?.metricComplicationContentDescription(metric, currentEpochSeconds)
            ?: "${metric.longTitle} unavailable"

        return RangedValueComplicationData.Builder(
            value = rangedValue,
            min = bounds.start,
            max = bounds.endInclusive,
            contentDescription = plainText(description),
        ).setText(plainText(text))
            .setTitle(plainText(metric.shortTitle))
            .setMonochromaticImage(icon)
            .build()
    }

    protected fun buildDualTemperatureData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
    ): ComplicationData? {
        return buildMetricPairData(
            snapshot = snapshot,
            type = type,
            primaryMetric = SnapshotMetric.COOLANT_TEMPERATURE,
            secondaryMetric = SnapshotMetric.TARGET_TEMPERATURE,
            iconResId = R.drawable.ic_complication_coolant,
            unavailableDescription = "Setpoint and coolant temperatures unavailable",
        )
    }

    protected fun buildMetricPairData(
        snapshot: ZontSnapshot?,
        type: ComplicationType,
        primaryMetric: SnapshotMetric,
        secondaryMetric: SnapshotMetric,
        @DrawableRes iconResId: Int,
        unavailableDescription: String,
    ): ComplicationData? {
        val icon = monochromaticImage(iconResId)
        val presentation = snapshot?.metricPairPresentation(
            primaryMetric = primaryMetric,
            secondaryMetric = secondaryMetric,
            nowEpochSeconds = currentEpochSeconds(),
        )

        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = plainText(presentation?.primaryText ?: placeholderText),
                contentDescription = plainText(presentation?.contentDescription ?: unavailableDescription),
            ).setMonochromaticImage(icon)
                .setTitle(plainText(presentation?.secondaryText ?: placeholderText))
                .build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = plainText(presentation?.primaryText ?: placeholderText),
                contentDescription = plainText(presentation?.contentDescription ?: unavailableDescription),
            ).setMonochromaticImage(icon)
                .setTitle(plainText(presentation?.secondaryText ?: placeholderText))
                .build()

            else -> null
        }
    }

    private fun plainText(text: String): ComplicationText {
        return PlainComplicationText.Builder(text).build()
    }

    private fun overviewLegendBitmapIcon(
        snapshot: ZontSnapshot?,
        nowEpochSeconds: Long,
    ): Icon {
        return Icon.createWithBitmap(overviewLegendImageRenderer.render(snapshot, nowEpochSeconds))
    }

    private fun overviewLegendSmallImage(
        snapshot: ZontSnapshot?,
        nowEpochSeconds: Long,
    ): SmallImage {
        val icon = overviewLegendBitmapIcon(snapshot, nowEpochSeconds)
        return SmallImage.Builder(icon, SmallImageType.PHOTO)
            .setAmbientImage(icon)
            .build()
    }

    private fun ZontSnapshot.metricValue(metric: SnapshotMetric): Double? {
        return when (metric) {
            SnapshotMetric.ROOM_TEMPERATURE -> roomTemperature
            SnapshotMetric.ROOM_SETPOINT_TEMPERATURE -> roomSetpointTemperature
            SnapshotMetric.BURNER_MODULATION -> burnerModulation
            SnapshotMetric.TARGET_TEMPERATURE -> targetTemperature
            SnapshotMetric.COOLANT_TEMPERATURE -> coolantTemperature
        }
    }

    private fun monochromaticImage(@DrawableRes iconResId: Int): MonochromaticImage {
        val icon = Icon.createWithResource(this, iconResId)
        return MonochromaticImage.Builder(icon)
            .setAmbientImage(icon)
            .build()
    }

    private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

    private companion object {
        const val placeholderText = "--"
        const val shortPairPlaceholder = "--·--"
        const val combinedLongPlaceholder = "--·--\n--·--"

        val previewSnapshot = ZontSnapshot(
            roomTemperature = 21.5,
            roomSetpointTemperature = 24.0,
            burnerModulation = 42.0,
            targetTemperature = 22.0,
            coolantTemperature = 37.0,
            deviceId = "1209",
            updatedAtEpochSeconds = System.currentTimeMillis() / 1_000L,
            refreshIntervalMinutes = 5,
            isStale = false,
        )
    }
}
