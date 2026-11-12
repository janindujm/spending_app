package com.spendplan.app

import android.app.Application
import com.spendplan.app.work.WorkScheduler

class SpendPlanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannel()
        WorkScheduler.scheduleDaily(this)
        // Catch up immediately on launch in case the device skipped scheduled runs.
        WorkScheduler.runNow(this)
    }
}
