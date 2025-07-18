package com.dcshub.app.platform

import com.dcshub.app.di.appModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(appModule())
    }
}
