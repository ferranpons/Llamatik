package com.llamatik.app.localization

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual fun getCurrentLanguage(): AvailableLanguages {
    val lang = Locale.getDefault().language
    return AvailableLanguages.entries.firstOrNull { it.name.equals(lang, ignoreCase = true) }
        ?: AvailableLanguages.EN
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
