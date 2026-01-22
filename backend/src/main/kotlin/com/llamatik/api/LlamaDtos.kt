package com.llamatik.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitModelRequest(
    val modelPath: String
)

@Serializable
data class InitModelResponse(
    val ok: Boolean
)

@Serializable
data class EmbedRequest(
    val input: String
)

@Serializable
data class EmbedResponse(
    val embedding: List<Float>
)

@Serializable
data class GenerateRequest(
    val prompt: String
)

@Serializable
data class GenerateWithContextRequest(
    val systemPrompt: String,
    val contextBlock: String,
    val userPrompt: String
)

@Serializable
data class GenerateJsonRequest(
    val prompt: String,
    val jsonSchema: String? = null
)

@Serializable
data class GenerateJsonWithContextRequest(
    val systemPrompt: String,
    val contextBlock: String,
    val userPrompt: String,
    val jsonSchema: String? = null
)

@Serializable
data class GenerateResponse(
    val text: String
)

@Serializable
data class UpdateParamsRequest(
    val temperature: Float,
    val maxTokens: Int,
    @SerialName("topP") val topP: Float,
    @SerialName("topK") val topK: Int,
    val repeatPenalty: Float
)

@Serializable
data class OkResponse(
    val ok: Boolean = true
)
