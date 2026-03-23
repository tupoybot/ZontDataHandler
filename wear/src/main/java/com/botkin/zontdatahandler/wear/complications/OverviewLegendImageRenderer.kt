package com.botkin.zontdatahandler.wear.complications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.botkin.zontdatahandler.shared.SnapshotMetric
import com.botkin.zontdatahandler.shared.ZontSnapshot
import com.botkin.zontdatahandler.shared.metricComplicationText
import com.botkin.zontdatahandler.wear.R

internal class OverviewLegendImageRenderer(
    private val context: Context,
) {
    fun render(snapshot: ZontSnapshot?, nowEpochSeconds: Long): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRoundRect(
            RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat()),
            cardCornerRadius,
            cardCornerRadius,
            backgroundPaint,
        )
        canvas.drawRoundRect(
            RectF(0.5f, 0.5f, canvasWidth - 0.5f, canvasHeight - 0.5f),
            cardCornerRadius,
            cardCornerRadius,
            borderPaint,
        )

        val valueTexts = metrics.map { metric ->
            snapshot?.metricComplicationText(metric.metric, nowEpochSeconds) ?: placeholderValue
        }
        val iconTop = iconBaseline - iconSize / 2

        metrics.forEachIndexed { index, metric ->
            val columnCenter = horizontalPadding + columnWidth * index + columnWidth / 2f
            canvas.drawText(valueTexts[index], columnCenter, valueBaseline, valuePaint)
            loadTintedDrawable(metric.iconResId)?.let { drawable ->
                val left = (columnCenter - iconSize / 2f).toInt()
                val top = iconTop.toInt()
                drawable.setBounds(left, top, left + iconSize.toInt(), top + iconSize.toInt())
                drawable.draw(canvas)
            }
        }

        return bitmap
    }

    private fun loadTintedDrawable(@DrawableRes iconResId: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(context, iconResId)?.mutate() ?: return null
        DrawableCompat.setTint(drawable, iconTint)
        return drawable
    }

    private data class MetricLegend(
        val metric: SnapshotMetric,
        @DrawableRes val iconResId: Int,
    )

    private companion object {
        const val canvasWidth = 420
        const val canvasHeight = 176
        const val horizontalPadding = 20f
        const val columnWidth = (canvasWidth - horizontalPadding * 2f) / 4f
        const val valueBaseline = 68f
        const val iconBaseline = 124f
        const val iconSize = 32f
        const val cardCornerRadius = 24f
        const val placeholderValue = "--"
        val iconTint = Color.WHITE

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(208, 20, 20, 20)
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val metrics = listOf(
            MetricLegend(SnapshotMetric.ROOM_TEMPERATURE, R.drawable.ic_complication_room),
            MetricLegend(SnapshotMetric.BURNER_MODULATION, R.drawable.ic_complication_burner),
            MetricLegend(SnapshotMetric.TARGET_TEMPERATURE, R.drawable.ic_complication_target),
            MetricLegend(SnapshotMetric.COOLANT_TEMPERATURE, R.drawable.ic_complication_coolant),
        )
    }
}
