package com.llamatik.app.di

import com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.platform.tts.JvmTtsEngine
import com.llamatik.app.platform.tts.TtsEngine
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.Properties

actual fun platformModules(): List<Module> = listOf(
    module {
        single<ModelDownloadOrchestrator> { DefaultModelDownloadOrchestrator(get()) }
        single<TtsEngine> { JvmTtsEngine() }
        single<Settings> {
            val propsFile = File(System.getProperty("user.home"), ".llamatik/settings.properties")
            propsFile.parentFile.mkdirs()
            val props = Properties()
            if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
            PropertiesSettings(props) { propsFile.outputStream().use { out -> props.store(out, null) } }
        }
    }
)