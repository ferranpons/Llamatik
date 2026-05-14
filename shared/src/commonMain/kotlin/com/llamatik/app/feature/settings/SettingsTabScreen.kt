package com.llamatik.app.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ModelSettingsBottomSheet
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.debugmenu.DebugMenuScreen
import com.llamatik.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.llamatik.app.ui.theme.LlamatikTheme
import org.koin.core.parameter.ParametersHolder

class SettingsTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val chatViewModel = koinScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )
        val state by chatViewModel.state.collectAsState()
        val showModelSettings = remember { mutableStateOf(false) }

        val debugScreen = remember { DebugMenuScreen() }
        val debugViewModel = koinScreenModel<DebugMenuViewModel>()
        val snackbarHostState = remember { SnackbarHostState() }

        LlamatikTheme {
            debugScreen.DebugMenuView(
                viewModel = debugViewModel,
                snackbarHostState = snackbarHostState,
                onClose = { navigator.pop() },
                onOpenModelSettings = { showModelSettings.value = true },
            )

            if (showModelSettings.value) {
                ModelSettingsBottomSheet(
                    current = state.generateSettings,
                    onApply = { chatViewModel.onGenerateSettingsApplied(it) },
                    onDismiss = { showModelSettings.value = false }
                )
            }
        }
    }
}
