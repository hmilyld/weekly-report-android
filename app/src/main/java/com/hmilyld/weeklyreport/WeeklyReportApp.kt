package com.hmilyld.weeklyreport

import android.app.Application

class WeeklyReportApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
