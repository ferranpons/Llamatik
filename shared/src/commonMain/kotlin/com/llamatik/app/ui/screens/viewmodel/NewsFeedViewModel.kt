package com.llamatik.app.ui.screens.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import com.llamatik.app.data.repositories.FeedItem
import com.llamatik.app.data.usecases.GetAllNewsUseCase
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.ui.screens.FeedItemDetailScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class NewsFeedViewModel(
    private var navigator: Navigator,
    private val rootNavigatorRepository: RootNavigatorRepository,
    private val getAllNewsUseCase: GetAllNewsUseCase
) : ScreenModel {
    private val _state = MutableStateFlow(NewsScreenState())
    val state = _state.asStateFlow()
    private val _sideEffects = Channel<NewsSideEffects>()
    val sideEffects: Flow<NewsSideEffects> = _sideEffects.receiveAsFlow()

    fun onStarted(navigator: Navigator) {
        this.navigator = navigator
        screenModelScope.launch {
            getAllNewsUseCase.invoke()
                .onSuccess {
                    _state.value = _state.value.copy(news = it)
                }
                .onFailure { error ->
                    error.message?.let { message ->
                        Logger.e(message)
                    }
                }
        }
    }

    fun onOpenFeedItemDetail(link: String) {
        rootNavigatorRepository.navigator.push(FeedItemDetailScreen(link))
    }
}

data class NewsScreenState(
    val news: List<FeedItem> = emptyList()
)

sealed class NewsSideEffects {
    data object Initial : NewsSideEffects()
    data object OnSignedUp : NewsSideEffects()
    data object OnSignUpError : NewsSideEffects()
}
