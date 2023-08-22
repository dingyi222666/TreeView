package com.dingyi.treeview

import android.app.Application
import com.google.android.material.color.DynamicColors




class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        // This is all you need.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}