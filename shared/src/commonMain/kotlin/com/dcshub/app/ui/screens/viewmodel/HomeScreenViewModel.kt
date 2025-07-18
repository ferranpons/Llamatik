@file:OptIn(ExperimentalTime::class)

package com.dcshub.app.ui.screens.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.dcshub.app.data.repositories.FeedItem
import com.dcshub.app.data.usecases.GetAllNewsUseCase
import com.dcshub.app.feature.debugmenu.DebugMenuScreen
import com.dcshub.app.feature.debugmenu.getDebug
import com.dcshub.app.feature.debugmenu.repositories.GlobalAppSettingsRepository
import com.dcshub.app.localization.AvailableLanguages
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.RootNavigatorRepository
import com.dcshub.app.ui.screens.FeedItemDetailScreen
import com.dcshub.app.ui.screens.NewsFeedScreen
import com.dcshub.app.ui.screens.OnboardingScreen
import com.russhwolf.settings.Settings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val ONBOARDING_VIEWED_KEY = "onboarding_viewed_key"

class HomeScreenViewModel(
    private var navigator: Navigator,
    private val getAllNewsUseCase: GetAllNewsUseCase,
    private val rootNavigatorRepository: RootNavigatorRepository,
    private val globalAppSettingsRepository: GlobalAppSettingsRepository,
    private val settings: Settings
) : ScreenModel {
    private val _sideEffects = Channel<HomeScreenSideEffects>()
    val sideEffects: Flow<HomeScreenSideEffects> = _sideEffects.receiveAsFlow()
    private val _state = MutableStateFlow(
        HomeScreenState(
            greeting = "",
            header = getCurrentLocalization().welcome,
            latestNews = emptyList(),
            currentLanguage = AvailableLanguages.EN,
        )
    )
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()
    private val debugComponent = getDebug()
    private lateinit var tabNavigator: TabNavigator

    init {
        if (!settings.getBoolean(ONBOARDING_VIEWED_KEY, false)) {
            settings.putBoolean(ONBOARDING_VIEWED_KEY, true)
            rootNavigatorRepository.navigator.push(OnboardingScreen())
        }
    }

    private fun getGreeting(): String {
        val currentTime = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val localDateTime = currentTime.toLocalDateTime(timeZone)

        return when (localDateTime.hour) {
            in 6..11 -> getCurrentLocalization().greetingMorning
            in 12..17 -> getCurrentLocalization().greetingAfternoon
            in 16..21 -> getCurrentLocalization().greetingEvening
            else -> getCurrentLocalization().greetingNight
        }
    }

    fun onOpenNewsClicked() {
        rootNavigatorRepository.navigator.push(NewsFeedScreen())
    }

    fun onStarted(navigator: Navigator, tabNavigator: TabNavigator) {
        this.navigator = navigator
        this.tabNavigator = tabNavigator

        screenModelScope.launch {
            getAllNewsUseCase.invoke()
                .onSuccess {
                    _state.value = _state.value.copy(latestNews = it)
                }
            _state.value = _state.value.copy(
                greeting = getGreeting(),
                header = getCurrentLocalization().welcome,
                latestNews = _state.value.latestNews,
                currentLanguage = globalAppSettingsRepository.getCurrentLanguage(),
            )
            _sideEffects.trySend(HomeScreenSideEffects.OnLoaded)
        }
    }

    fun onDebugMenuClicked() {
        rootNavigatorRepository.navigator.push(DebugMenuScreen())
    }

    fun onOpenFeedItemDetail(link: String) {
        rootNavigatorRepository.navigator.push(FeedItemDetailScreen(link))
    }
}

data class HomeScreenState(
    val greeting: String,
    val header: String,
    val latestNews: List<FeedItem>,
    val currentLanguage: AvailableLanguages,
)

sealed class HomeScreenSideEffects {
    data object Initial : HomeScreenSideEffects()
    data object OnLoaded : HomeScreenSideEffects()
    data object OnLoadError : HomeScreenSideEffects()
}
