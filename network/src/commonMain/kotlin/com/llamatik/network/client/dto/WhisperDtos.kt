package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhisperInitRequest(val modelPath: String)

@Serializable
data class TranscribeRequest(
    val wavPath: String,
    val language: String? = null,
    val initialPrompt: String? = null,
)

@Serializable
data class TranscribeResponse(val text: String)

@Serializable
data class TranscribeSegmentsRequest(
    val wavPath: String,
    val language: String? = null,
    val initialPrompt: String? = null,
    val translate: Boolean = false,
    val diarize: Boolean = false,
)

@Serializable
data class TranscribeSegmentsResponse(val json: String)
