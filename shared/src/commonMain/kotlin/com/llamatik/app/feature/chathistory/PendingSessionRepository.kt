package com.llamatik.app.feature.chathistory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PendingSessionRepository {
    private val _pendingId = MutableStateFlow<String?>(null)
    val pendingId: StateFlow<String?> = _pendingId

    fun request(id: String) { _pendingId.value = id }
    fun consume() { _pendingId.value = null }
}
