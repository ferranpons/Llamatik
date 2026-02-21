package com.llamatik.app.di

import com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.platform.tts.TtsEngine
import com.llamatik.app.platform.tts.WasmTtsEngine
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { DefaultModelDownloadOrchestrator(get()) }
        single<TtsEngine> { WasmTtsEngine() }
    }
)