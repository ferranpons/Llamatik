package com.dcshub.app.android

import com.dcshub.app.common.model.UserData

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data class Success(val userData: UserData) : MainActivityUiState
}
