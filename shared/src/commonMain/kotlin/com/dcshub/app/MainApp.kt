package com.dcshub.app

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScaleTransition
import com.dcshub.app.ui.screens.MainScreen

@Composable
fun MainApp() {
    Navigator(MainScreen()) {
        ScaleTransition(it)
    }
}
