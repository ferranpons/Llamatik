package com.llamatik.app.platform

import com.llamatik.app.di.appModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * WASM needs to start Koin manually (like Desktop main()).
 * Safe to call multiple times.
 */
fun initKoinWasm() {
    runCatching { stopKoin() }
    startKoin {
        modules(appModule())
    }

    // If you have other post-start bindings (like ReviewEntryPoint) that are Android/iOS only,
    // do NOT call them here.
}