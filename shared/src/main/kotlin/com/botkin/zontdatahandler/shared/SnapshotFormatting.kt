package com.botkin.zontdatahandler.shared

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class SnapshotMetric(
    val shortTitle: String,
    val longTitle: String,
    val compactLabel: String,
    val colorLabel: String,
) {
    ROOM_TEMPERATURE(
        shortTitle = "Room",
        longTitle = "Room temperature",
        compactLabel = "\uD83C\uDF21\uFE0E",
        colorLabel = "\uD83C\uDFE0",
    ),
    ROOM_SETPOINT_TEMPERATURE(
        shortTitle = "Air setpoint",
        longTitle = "Room air setpoint",
        compactLabel = "\uD83C\uDF21\uFE0E",
        colorLabel = "\uD83C\uDF21\uFE0F",
    ),
    BURNER_MODULATION(
        shortTitle = "Burner",
        longTitle = "Burner modulation",
        compactLabel = "\uD83D\uDD25\uFE0E",
        colorLabel = "\uD83D\uDD25",
    ),
    TARGET_TEMPERATURE(
        shortTitle = "Setpoint",
        longTitle = "Water setpoint",
        compactLabel = "\uD83C\uDF21\uFE0E",
        colorLabel = "\uD83C\uDF21\uFE0F",
    ),
    COOLANT_TEMPERATURE(
        shortTitle = "Coolant",
        longTitle = "Coolant temperature",
        compactLabel = "\u25A5",
        colorLabel = "\u2668\uFE0F",
    ),
}

data class MetricPresentation(
    val valueText: String,
    val titleText: String,
    val contentDescription: String,
    val isPlaceholder: Boolean,
    val isStale: Boolean,
)

data class MetricPairPresentation(
    val primaryText: String,
    val secondaryText: String,
    val contentDescription: String,
)

object SnapshotTransport {
    const val dataPath = "/zont/latest_snapshot"
    const val snapshotJsonKey = "snapshot_json"
    const val syncedAtKey = "synced_at_epoch_millis"
    const val missingPlaceholder = "--"
}

object SnapshotJson {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(snapshot: ZontSnapshot): String = json.encodeToString(ZontSnapshot.serializer(), snapshot)

    fun decodeOrNull(raw: String?): ZontSnapshot? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString(ZontSnapshot.serializer(), raw)
        }.getOrNull()
    }
}

fun ZontSnapshot.withComputedStaleness(nowEpochSeconds: Long): ZontSnapshot {
    return copy(isStale = isStale(nowEpochSeconds))
}

fun ZontSnapshot.isStale(nowEpochSeconds: Long): Boolean {
    if (updatedAtEpochSeconds <= 0L) {
        return true
    }
    val thresholdSeconds = refreshIntervalSeconds() * staleThresholdPeriods
    return nowEpochSeconds - updatedAtEpochSeconds >= thresholdSeconds
}

fun ZontSnapshot.metricPresentation(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): MetricPresentation {
    val current = withComputedStaleness(nowEpochSeconds)
    val stale = current.isStale
    val value = current.metricDisplayValue(metric, nowEpochSeconds)
    val title = metric.shortTitle
    val valueText = current.metricComplicationText(metric, nowEpochSeconds)
    val visibleValue = current.metricValueText(metric, nowEpochSeconds)
    val descriptionPrefix = buildString {
        if (stale) {
            append("Stale ")
        }
        append(metric.longTitle.lowercase(Locale.US))
    }
    return MetricPresentation(
        valueText = valueText,
        titleText = title,
        contentDescription = "$descriptionPrefix $visibleValue",
        isPlaceholder = value == null,
        isStale = stale,
    )
}

fun ZontSnapshot.combinedLongText(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryHasValue = current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds)
    val secondaryHasValue = current.hasAnyMetricValue(combinedSecondaryMetrics, nowEpochSeconds)
    return buildString {
        append(
            current.withInlineStaleMarker(
                text = current.combinedPrimaryLine(nowEpochSeconds),
                isPlaceholder = !primaryHasValue,
            ),
        )
        append(overviewSegmentSeparator)
        append(
            current.withInlineStaleMarker(
                text = current.combinedSecondaryLine(nowEpochSeconds),
                isPlaceholder = !secondaryHasValue,
                forceMarker = !primaryHasValue && secondaryHasValue,
            ),
        )
    }
}

fun ZontSnapshot.combinedShortText(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = current.combinedPrimaryLine(nowEpochSeconds)
    return current.withInlineStaleMarker(
        text = body,
        isPlaceholder = !current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds),
    )
}

fun ZontSnapshot.combinedShortTitle(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryHasValue = current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds)
    val secondaryHasValue = current.hasAnyMetricValue(combinedSecondaryMetrics, nowEpochSeconds)
    return current.withInlineStaleMarker(
        text = current.combinedSecondaryLine(nowEpochSeconds),
        isPlaceholder = !secondaryHasValue,
        forceMarker = !primaryHasValue && secondaryHasValue,
    )
}

fun ZontSnapshot.combinedColorLongText(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryHasValue = current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds)
    val secondaryHasValue = current.hasAnyMetricValue(combinedSecondaryMetrics, nowEpochSeconds)
    return buildString {
        append(
            current.withInlineStaleMarker(
                text = current.combinedColorPrimaryLine(nowEpochSeconds),
                isPlaceholder = !primaryHasValue,
            ),
        )
        append('\n')
        append(
            current.withInlineStaleMarker(
                text = current.combinedColorSecondaryLine(nowEpochSeconds),
                isPlaceholder = !secondaryHasValue,
                forceMarker = !primaryHasValue && secondaryHasValue,
            ),
        )
    }
}

fun ZontSnapshot.combinedColorShortText(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    return current.withInlineStaleMarker(
        text = current.combinedColorPrimaryLine(nowEpochSeconds),
        isPlaceholder = !current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds),
    )
}

fun ZontSnapshot.combinedColorShortTitle(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryHasValue = current.hasAnyMetricValue(combinedPrimaryMetrics, nowEpochSeconds)
    val secondaryHasValue = current.hasAnyMetricValue(combinedSecondaryMetrics, nowEpochSeconds)
    return current.withInlineStaleMarker(
        text = current.combinedColorSecondaryLine(nowEpochSeconds),
        isPlaceholder = !secondaryHasValue,
        forceMarker = !primaryHasValue && secondaryHasValue,
    )
}

fun ZontSnapshot.combinedContentDescription(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = complicationMetrics.joinToString(", ") { metric ->
        "${metric.longTitle}: ${current.metricValueText(metric, nowEpochSeconds)}"
    }
    return if (current.isStale) "Stale. $body" else body
}

fun ZontSnapshot.metricComplicationText(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    val current = withComputedStaleness(nowEpochSeconds)
    return current.withInlineStaleMarker(
        text = current.metricValueText(metric, nowEpochSeconds),
        isPlaceholder = current.metricDisplayValue(metric, nowEpochSeconds) == null,
    )
}

fun ZontSnapshot.metricComplicationContentDescription(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = "${metric.longTitle}: ${current.metricValueText(metric, nowEpochSeconds)}"
    return if (current.isStale) "Stale. $body" else body
}

fun ZontSnapshot.metricPairPresentation(
    primaryMetric: SnapshotMetric,
    secondaryMetric: SnapshotMetric,
    nowEpochSeconds: Long,
): MetricPairPresentation {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryValue = current.metricDisplayValue(primaryMetric, nowEpochSeconds)
    val secondaryValue = current.metricDisplayValue(secondaryMetric, nowEpochSeconds)
    val primaryText = current.withInlineStaleMarker(
        text = current.metricValueText(primaryMetric, nowEpochSeconds),
        isPlaceholder = primaryValue == null,
    )
    val secondaryText = current.withInlineStaleMarker(
        text = "$pairSecondaryPrefix${current.metricValueText(secondaryMetric, nowEpochSeconds)}",
        isPlaceholder = secondaryValue == null,
        forceMarker = primaryValue == null && secondaryValue != null,
    )
    val body = listOf(
        "${primaryMetric.longTitle}: ${current.metricValueText(primaryMetric, nowEpochSeconds)}",
        "${secondaryMetric.longTitle}: ${current.metricValueText(secondaryMetric, nowEpochSeconds)}",
    ).joinToString(", ")
    return MetricPairPresentation(
        primaryText = primaryText,
        secondaryText = secondaryText,
        contentDescription = if (current.isStale) "Stale. $body" else body,
    )
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

private fun ZontSnapshot.metricDisplayValue(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): Double? {
    val value = metricValue(metric) ?: return null
    return if (isMetricValueExpired(nowEpochSeconds)) null else value
}

private fun ZontSnapshot.metricValueText(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    return metricDisplayValue(metric, nowEpochSeconds)?.let { formatMetric(metric, it) }
        ?: SnapshotTransport.missingPlaceholder
}

private fun ZontSnapshot.hasAnyMetricValue(
    metrics: List<SnapshotMetric>,
    nowEpochSeconds: Long,
): Boolean {
    return metrics.any { metricDisplayValue(it, nowEpochSeconds) != null }
}

private fun ZontSnapshot.isMetricValueExpired(nowEpochSeconds: Long): Boolean {
    if (updatedAtEpochSeconds <= 0L) {
        return true
    }
    return nowEpochSeconds - updatedAtEpochSeconds >= metricValueExpirationThresholdSeconds()
}

private fun ZontSnapshot.metricValueExpirationThresholdSeconds(): Long {
    return refreshIntervalSeconds() * metricValueExpirationPeriods
}

private fun ZontSnapshot.refreshIntervalSeconds(): Long {
    return refreshIntervalMinutes
        .coerceAtLeast(ZontSnapshot.MIN_REFRESH_INTERVAL_MINUTES) * 60L
}

private fun ZontSnapshot.withInlineStaleMarker(
    text: String,
    isPlaceholder: Boolean = false,
    forceMarker: Boolean = false,
): String {
    return if (isStale && (!isPlaceholder || forceMarker)) "$staleMarker$text" else text
}

private fun ZontSnapshot.combinedPrimaryLine(nowEpochSeconds: Long): String {
    return metricLine(
        primaryMetric = SnapshotMetric.ROOM_TEMPERATURE,
        secondaryMetric = SnapshotMetric.BURNER_MODULATION,
        nowEpochSeconds = nowEpochSeconds,
        separator = overviewInlineSeparator,
    )
}

private fun ZontSnapshot.combinedSecondaryLine(nowEpochSeconds: Long): String {
    return directionalMetricLine(
        primaryMetric = SnapshotMetric.COOLANT_TEMPERATURE,
        secondaryMetric = SnapshotMetric.TARGET_TEMPERATURE,
        nowEpochSeconds = nowEpochSeconds,
    )
}

private fun ZontSnapshot.combinedColorPrimaryLine(nowEpochSeconds: Long): String {
    return colorMetricLine(
        primaryMetric = SnapshotMetric.ROOM_TEMPERATURE,
        secondaryMetric = SnapshotMetric.BURNER_MODULATION,
        nowEpochSeconds = nowEpochSeconds,
    )
}

private fun ZontSnapshot.combinedColorSecondaryLine(nowEpochSeconds: Long): String {
    return colorMetricLine(
        primaryMetric = SnapshotMetric.TARGET_TEMPERATURE,
        secondaryMetric = SnapshotMetric.COOLANT_TEMPERATURE,
        nowEpochSeconds = nowEpochSeconds,
    )
}

private fun ZontSnapshot.metricLine(
    primaryMetric: SnapshotMetric,
    secondaryMetric: SnapshotMetric,
    nowEpochSeconds: Long,
    separator: String,
): String {
    return buildString {
        append(metricValueText(primaryMetric, nowEpochSeconds))
        append(separator)
        append(metricValueText(secondaryMetric, nowEpochSeconds))
    }
}

private fun ZontSnapshot.directionalMetricLine(
    primaryMetric: SnapshotMetric,
    secondaryMetric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    return buildString {
        append(metricValueText(primaryMetric, nowEpochSeconds))
        append(pairInlineSeparator)
        append(metricValueText(secondaryMetric, nowEpochSeconds))
    }
}

private fun ZontSnapshot.colorMetricLine(
    primaryMetric: SnapshotMetric,
    secondaryMetric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    return buildString {
        append(primaryMetric.colorLabel)
        append(' ')
        append(metricValueText(primaryMetric, nowEpochSeconds))
        append(' ')
        append(secondaryMetric.colorLabel)
        append(' ')
        append(metricValueText(secondaryMetric, nowEpochSeconds))
    }
}

private fun formatMetric(metric: SnapshotMetric, value: Double): String {
    return when (metric) {
        SnapshotMetric.BURNER_MODULATION -> "${value.roundToInt()}%"
        SnapshotMetric.ROOM_TEMPERATURE,
        SnapshotMetric.ROOM_SETPOINT_TEMPERATURE,
        SnapshotMetric.TARGET_TEMPERATURE,
        SnapshotMetric.COOLANT_TEMPERATURE,
        -> "${decimalFormat.format(value)}°"
    }
}

private val decimalFormat = DecimalFormat(
    "0.#",
    DecimalFormatSymbols(Locale.US),
)

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

private val complicationMetrics = listOf(
    SnapshotMetric.ROOM_TEMPERATURE,
    SnapshotMetric.BURNER_MODULATION,
    SnapshotMetric.TARGET_TEMPERATURE,
    SnapshotMetric.COOLANT_TEMPERATURE,
)

private val combinedPrimaryMetrics = listOf(
    SnapshotMetric.ROOM_TEMPERATURE,
    SnapshotMetric.BURNER_MODULATION,
)

private val combinedSecondaryMetrics = listOf(
    SnapshotMetric.TARGET_TEMPERATURE,
    SnapshotMetric.COOLANT_TEMPERATURE,
)

private const val staleThresholdPeriods = 2L
private const val metricValueExpirationPeriods = 10L
private const val staleMarker = "!"
private const val pairSecondaryPrefix = "→"
private const val overviewInlineSeparator = " "
private const val overviewSegmentSeparator = " "
private const val pairInlineSeparator = " → "
