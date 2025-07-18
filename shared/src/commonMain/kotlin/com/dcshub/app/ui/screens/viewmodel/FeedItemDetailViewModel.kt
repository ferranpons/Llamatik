package com.dcshub.app.ui.screens.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import com.dcshub.app.data.repositories.FeedItem
import com.dcshub.app.data.usecases.GetAllNewsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class FeedItemDetailViewModel(
    private var navigator: Navigator,
    private val getAllNewsUseCase: GetAllNewsUseCase
) : ScreenModel {
    private val _state = MutableStateFlow(FeedItemDetailScreenState())
    val state = _state.asStateFlow()
    private val _sideEffects = Channel<FeedItemDetailSideEffects>()
    val sideEffects: Flow<FeedItemDetailSideEffects> = _sideEffects.receiveAsFlow()

    fun onStarted(navigator: Navigator, link: String) {
        this.navigator = navigator
        screenModelScope.launch {
            getAllNewsUseCase.invoke()
                .onSuccess {
                    _state.value =
                        _state.value.copy(feedItem = it.find { item -> item.link == link }!!)
                }
                .onFailure { error ->
                    error.message?.let { message ->
                        Logger.e(message)
                    }
                }
        }
    }
}

data class FeedItemDetailScreenState(
    val feedItem: FeedItem = FeedItem(
        title = "",
        link = "",
        description = "",
        enclosure = null,
        pubDate = ""
    )
)

sealed class FeedItemDetailSideEffects {
    data object Initial : FeedItemDetailSideEffects()
    data object OnSignedUp : FeedItemDetailSideEffects()
    data object OnSignUpError : FeedItemDetailSideEffects()
}
