package com.llamatik.app.android

import android.app.Application
import androidx.work.Configuration
import com.llamatik.app.android.core.di.DependencyContainer

private const val MAX_SCHEDULER_LIMIT = 1

class LlamatikApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMaxSchedulerLimit(MAX_SCHEDULER_LIMIT)
            .build()

    override fun onCreate() {
        super.onCreate()
        initDependencyContainer()
    }

    private fun initDependencyContainer() {
        DependencyContainer.initialize(this)
    }
}
