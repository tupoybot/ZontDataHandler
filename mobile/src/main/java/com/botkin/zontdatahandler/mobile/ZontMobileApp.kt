package com.botkin.zontdatahandler.mobile

import android.app.Application
import android.content.Context

class ZontMobileApp : Application() {
    val appContainer: MobileAppContainer by lazy {
        MobileAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        appContainer.bootstrap()
    }
}

val Context.mobileAppContainer: MobileAppContainer
    get() = (applicationContext as ZontMobileApp).appContainer
