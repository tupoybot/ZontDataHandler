package com.botkin.zontdatahandler.shared

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class SnapshotMetric(
    val shortTitle: String,
    val longTitle: String,
) {
    ROOM_TEMPERATURE(shortTitle = "Room", longTitle = "Room temperature"),
    ROOM_SETPOINT_TEMPERATURE(shortTitle = "Air setpoint", longTitle = "Room air setpoint"),
    BURNER_MODULATION(shortTitle = "Burner", longTitle = "Burner modulation"),
    TARGET_TEMPERATURE(shortTitle = "Setpoint", longTitle = "Water setpoint"),
    COOLANT_TEMPERATURE(shortTitle = "Coolant", longTitle = "Coolant temperature"),
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
    val thresholdSeconds = refreshIntervalMinutes
        .coerceAtLeast(ZontSnapshot.MIN_REFRESH_INTERVAL_MINUTES) * 60L * 2L
    return nowEpochSeconds - updatedAtEpochSeconds >= thresholdSeconds
}

fun ZontSnapshot.metricPresentation(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): MetricPresentation {
    val current = withComputedStaleness(nowEpochSeconds)
    val stale = current.isStale
    val value = current.metricValue(metric)
    val title = metric.shortTitle
    val valueText = current.metricComplicationText(metric, nowEpochSeconds)
    val visibleValue = current.metricValueText(metric)
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
    val body = buildString {
        append(current.combinedPrimaryLine())
        append('\n')
        append(current.combinedSecondaryLine())
    }
    return current.withLeadingStaleMarker(body)
}

fun ZontSnapshot.combinedShortText(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = current.combinedPrimaryLine()
    return current.withInlineStaleMarker(body)
}

fun ZontSnapshot.combinedShortTitle(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    return current.combinedSecondaryLine()
}

fun ZontSnapshot.combinedContentDescription(nowEpochSeconds: Long): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = complicationMetrics.joinToString(", ") { metric ->
        "${metric.longTitle}: ${current.metricValueText(metric)}"
    }
    return if (current.isStale) "Stale. $body" else body
}

fun ZontSnapshot.metricComplicationText(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    val current = withComputedStaleness(nowEpochSeconds)
    return current.withInlineStaleMarker(current.metricValueText(metric), current.metricValue(metric) == null)
}

fun ZontSnapshot.metricComplicationContentDescription(
    metric: SnapshotMetric,
    nowEpochSeconds: Long,
): String {
    val current = withComputedStaleness(nowEpochSeconds)
    val body = "${metric.longTitle}: ${current.metricValueText(metric)}"
    return if (current.isStale) "Stale. $body" else body
}

fun ZontSnapshot.metricPairPresentation(
    primaryMetric: SnapshotMetric,
    secondaryMetric: SnapshotMetric,
    nowEpochSeconds: Long,
): MetricPairPresentation {
    val current = withComputedStaleness(nowEpochSeconds)
    val primaryValue = current.metricValue(primaryMetric)
    val secondaryValue = current.metricValue(secondaryMetric)
    val primaryText = current.withInlineStaleMarker(
        text = current.metricValueText(primaryMetric),
        isPlaceholder = primaryValue == null,
    )
    val secondaryText = current.withInlineStaleMarker(
        text = "${pairSecondaryPrefix}${current.metricValueText(secondaryMetric)}",
        isPlaceholder = secondaryValue == null,
        forceMarker = primaryValue == null,
    )
    val body = listOf(
        "${primaryMetric.longTitle}: ${current.metricValueText(primaryMetric)}",
        "${secondaryMetric.longTitle}: ${current.metricValueText(secondaryMetric)}",
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

private fun ZontSnapshot.metricValueText(metric: SnapshotMetric): String {
    return metricValue(metric)?.let { formatMetric(metric, it) } ?: SnapshotTransport.missingPlaceholder
}

private fun ZontSnapshot.withLeadingStaleMarker(text: String): String {
    return if (isStale) "$staleMarker $text" else text
}

private fun ZontSnapshot.withInlineStaleMarker(
    text: String,
    isPlaceholder: Boolean = false,
    forceMarker: Boolean = false,
): String {
    return if (isStale && (!isPlaceholder || forceMarker)) "$staleMarker$text" else text
}

private fun ZontSnapshot.combinedPrimaryLine(): String {
    return combinedPrimaryMetrics.joinToString(shortSeparator) { metric ->
        metricValueText(metric)
    }
}

private fun ZontSnapshot.combinedSecondaryLine(): String {
    return combinedSecondaryMetrics.joinToString(shortSeparator) { metric ->
        metricValueText(metric)
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

private const val staleMarker = "!"
private const val pairSecondaryPrefix = "→"
private const val shortSeparator = "·"
