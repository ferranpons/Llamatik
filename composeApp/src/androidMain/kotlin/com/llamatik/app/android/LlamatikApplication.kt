package com.llamatik.app.android

import android.app.Application
import com.llamatik.app.android.core.di.DependencyContainer

class LlamatikApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initDependencyContainer()
    }

    private fun initDependencyContainer() {
        DependencyContainer.initialize(this)
    }
}
