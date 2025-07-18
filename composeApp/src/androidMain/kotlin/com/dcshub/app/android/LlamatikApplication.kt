package com.dcshub.app.android

import android.app.Application
import com.dcshub.app.android.core.di.DependencyContainer

class LlamatikApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initDependencyContainer()
    }

    private fun initDependencyContainer() {
        DependencyContainer.initialize(this)
    }
}
