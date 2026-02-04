package com.llamatik.app.di

import com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { DefaultModelDownloadOrchestrator(get()) }
    }
)