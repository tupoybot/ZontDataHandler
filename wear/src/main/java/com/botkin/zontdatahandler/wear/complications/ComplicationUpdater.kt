package com.botkin.zontdatahandler.wear.complications

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

object ComplicationUpdater {
    private val providerClasses = listOf(
        CombinedComplicationService::class.java,
        CombinedColorComplicationService::class.java,
        RoomTemperatureComplicationService::class.java,
        BurnerModulationComplicationService::class.java,
        TargetTemperatureComplicationService::class.java,
        CoolantTemperatureComplicationService::class.java,
        TargetCoolantComplicationService::class.java,
        RoomSetpointComplicationService::class.java,
    )

    fun requestAll(context: Context) {
        providerClasses.forEach { providerClass ->
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(context, providerClass),
                )
                .requestUpdateAll()
        }
    }
}
