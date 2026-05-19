package com.llamatik.app.di

import com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.feature.entitlement.EntitlementRepository
import com.llamatik.app.feature.entitlement.MobileEntitlementRepository
import com.llamatik.app.platform.tts.AppleTtsEngine
import com.llamatik.app.platform.tts.TtsEngine
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { DefaultModelDownloadOrchestrator(get()) }
        single<TtsEngine> { AppleTtsEngine() }
        single<EntitlementRepository> { MobileEntitlementRepository(get()) }
        singleOf(::Settings)
    }
)