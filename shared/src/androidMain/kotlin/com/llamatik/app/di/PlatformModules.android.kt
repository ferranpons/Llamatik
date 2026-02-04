package com.llamatik.app.di

import android.content.Context
import com.llamatik.app.feature.chatbot.download.AndroidWorkManagerModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { AndroidWorkManagerModelDownloadOrchestrator(get<Context>()) }
    }
)