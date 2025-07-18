package com.dcshub.app.localization

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual fun getCurrentLanguage(): AvailableLanguages {
    return when (Locale.getDefault().language) {
        AvailableLanguages.ES.name.lowercase() -> AvailableLanguages.ES
        AvailableLanguages.EN.name.lowercase() -> AvailableLanguages.EN
        AvailableLanguages.IT.name.lowercase() -> AvailableLanguages.IT
        AvailableLanguages.FR.name.lowercase() -> AvailableLanguages.FR
        AvailableLanguages.DE.name.lowercase() -> AvailableLanguages.DE
        AvailableLanguages.CN.name.lowercase() -> AvailableLanguages.CN
        AvailableLanguages.RU.name.lowercase() -> AvailableLanguages.RU
        else -> AvailableLanguages.EN
    }
}

@Composable
actual fun SetLanguage(language: AvailableLanguages) {
    val context = LocalContext.current
    val locale = Locale(language.name.lowercase())
    Locale.setDefault(locale)
    val config: Configuration = context.resources.configuration
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
