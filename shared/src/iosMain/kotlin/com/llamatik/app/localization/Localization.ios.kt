package com.llamatik.app.localization

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
    val mainLanguage = languageDefaults.objectEnumerator().allObjects.first()?.toString() ?: return AvailableLanguages.EN
    val langCode = mainLanguage.substringBefore('-').lowercase()
    return AvailableLanguages.entries.firstOrNull { it.name.equals(langCode, ignoreCase = true) }
        ?: AvailableLanguages.EN
}

@OptIn(BetaInteropApi::class)
@Composable
actual fun SetLanguage(language: AvailableLanguages) {
    val languagesArray = NSArray.create(listOf(language.name.lowercase()))
    NSUserDefaults.standardUserDefaults.setObject(languagesArray, forKey = LANGUAGE_KEY)
    NSUserDefaults.standardUserDefaults.synchronize()
}
