package com.llamatik.app.di

import com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.feature.entitlement.EntitlementRepository
import com.llamatik.app.feature.entitlement.UnlockedEntitlementRepository
import com.llamatik.app.platform.tts.TtsEngine
import com.llamatik.app.platform.tts.WasmTtsEngine
import com.llamatik.sdk.agent.action.wasmPlatformActions
import com.llamatik.sdk.agent.capability.PlatformCapabilityProvider
import com.llamatik.sdk.agent.capability.WasmCapabilityProvider
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { DefaultModelDownloadOrchestrator(get()) }
        single<TtsEngine> { WasmTtsEngine() }
        single<EntitlementRepository> { UnlockedEntitlementRepository() }
        singleOf(::Settings)

        // Agent platform components
        single<PlatformCapabilityProvider> { WasmCapabilityProvider() }
        single(named("platformActions")) { wasmPlatformActions() }
    }
)