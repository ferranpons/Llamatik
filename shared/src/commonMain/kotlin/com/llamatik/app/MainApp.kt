package com.llamatik.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScaleTransition
import com.llamatik.app.localization.AvailableLanguages
import com.llamatik.app.localization.getCurrentLanguage
import com.llamatik.app.ui.screens.MainScreen

@Composable
fun MainApp() {

    val isRtl = getCurrentLanguage() == AvailableLanguages.FA

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Navigator(MainScreen()) {
            ScaleTransition(it)
        }
    }
}
