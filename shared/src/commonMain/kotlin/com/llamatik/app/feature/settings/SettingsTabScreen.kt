package com.llamatik.app.feature.settings

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.debugmenu.DebugMenuScreen

// The Settings tab delegates to the existing DebugMenuScreen (AppSettingsScreen) for now.
// Future: break out into a proper settings screen with agent activity log, companion config, etc.
class SettingsTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        DebugMenuScreen().Content()
    }
}
