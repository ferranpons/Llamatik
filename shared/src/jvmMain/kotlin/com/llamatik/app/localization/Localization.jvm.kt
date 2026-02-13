package com.llamatik.app.localization

import androidx.compose.runtime.Composable
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
    Locale.setDefault(Locale(language.name.lowercase()))
}
