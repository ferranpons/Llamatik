package com.dcshub.app.feature.debugmenu.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import com.dcshub.app.feature.debugmenu.repositories.GlobalAppSettingsRepository
import com.dcshub.app.localization.AvailableLanguages
import com.dcshub.app.platform.Environment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class DebugMenuViewModel(
    private val globalAppSettingsRepository: GlobalAppSettingsRepository
) : ScreenModel {
    private val _state = MutableStateFlow(
        DebugMenuState(
            currentEnvironment = globalAppSettingsRepository.getCurrentEnvironment(),
            currentLanguage = globalAppSettingsRepository.getCurrentLanguage(),
            isMockedContentChecked = globalAppSettingsRepository.isMockedContentEnabled(),
            isMockedUserChecked = globalAppSettingsRepository.isMockedUserEnabled()
        )
    )
    val state = _state.asStateFlow()
    private val _sideEffects = Channel<DebugMenuSideEffects>()
    val sideEffects: Flow<DebugMenuSideEffects> = _sideEffects.receiveAsFlow()

    fun onSelectedEnvironment(environment: Environment) {
        _state.value = _state.value.copy(currentEnvironment = environment)
        globalAppSettingsRepository.setSelectedEnvironment(environment)
    }

    fun onSelectedLanguage(language: AvailableLanguages) {
        _state.value = _state.value.copy(currentLanguage = language)
        globalAppSettingsRepository.setSelectedLanguage(language)
    }

    fun onMockedContentCheckChanged(checked: Boolean) {
        _state.value = _state.value.copy(isMockedContentChecked = checked)
        globalAppSettingsRepository.setMockedContentCheckStatus(checked)
    }
}

data class DebugMenuState(
    val currentEnvironment: Environment,
    val currentLanguage: AvailableLanguages,
    val isMockedContentChecked: Boolean,
    val isMockedUserChecked: Boolean
)

sealed class DebugMenuSideEffects {
    data object Initial : DebugMenuSideEffects()
}
