package com.llamatik.app.di

import android.content.Context
import com.llamatik.app.feature.chatbot.download.AndroidWorkManagerModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.feature.entitlement.EntitlementRepository
import com.llamatik.app.feature.entitlement.MobileEntitlementRepository
import com.llamatik.app.platform.tts.AndroidTtsEngine
import com.llamatik.app.platform.tts.TtsEngine
import com.llamatik.sdk.agent.action.androidPlatformActions
import com.llamatik.sdk.agent.capability.AndroidCapabilityProvider
import com.llamatik.sdk.agent.capability.PlatformCapabilityProvider
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { AndroidWorkManagerModelDownloadOrchestrator(get<Context>()) }
        single<TtsEngine> { AndroidTtsEngine(get<Context>()) }
        single<EntitlementRepository> { MobileEntitlementRepository(get()) }
        singleOf(::Settings)

        // Agent platform components
        single<PlatformCapabilityProvider> { AndroidCapabilityProvider() }
        single(named("platformActions")) { androidPlatformActions(get<Context>()) }
    }
)