package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class SseEventDto(
    val event: String,
    val text: String? = null,
    val message: String? = null,
)
