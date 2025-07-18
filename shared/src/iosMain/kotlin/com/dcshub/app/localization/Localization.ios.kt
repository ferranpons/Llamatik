package com.dcshub.app.localization

import androidx.compose.runtime.Composable
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSArray
import platform.Foundation.NSUserDefaults
import platform.Foundation.allObjects
import platform.Foundation.create
import platform.Foundation.objectEnumerator

private const val LANGUAGE_KEY = "AppleLanguages"

actual fun getCurrentLanguage(): AvailableLanguages {
    val languageDefaults = NSUserDefaults.standardUserDefaults.objectForKey(LANGUAGE_KEY) as NSArray
    val mainLanguage = languageDefaults.objectEnumerator().allObjects.first()
    println("Current Language: $mainLanguage")
    return when (mainLanguage) {
        AvailableLanguages.ES.name.lowercase() -> AvailableLanguages.ES
        AvailableLanguages.EN.name.lowercase() -> AvailableLanguages.EN
        AvailableLanguages.IT.name.lowercase() -> AvailableLanguages.IT
        AvailableLanguages.FR.name.lowercase() -> AvailableLanguages.FR
        AvailableLanguages.DE.name.lowercase() -> AvailableLanguages.DE
        AvailableLanguages.CN.name.lowercase() -> AvailableLanguages.CN
        AvailableLanguages.RU.name.lowercase() -> AvailableLanguages.RU
        "es-ES" -> AvailableLanguages.ES
        "de-DE" -> AvailableLanguages.DE
        else -> AvailableLanguages.EN
    }
}

@OptIn(BetaInteropApi::class)
@Composable
actual fun SetLanguage(language: AvailableLanguages) {
    val languagesArray = NSArray.create(listOf(language.name.lowercase()))
    NSUserDefaults.standardUserDefaults.setObject(languagesArray, forKey = LANGUAGE_KEY)
    NSUserDefaults.standardUserDefaults.synchronize()
}
