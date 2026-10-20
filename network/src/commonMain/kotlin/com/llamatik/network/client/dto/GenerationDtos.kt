package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class InitModelRequest(val modelPath: String)

@Serializable
data class InitModelResponse(val ok: Boolean)

@Serializable
data class GenerateRequest(val prompt: String)

@Serializable
data class GenerateWithContextRequest(
    val systemPrompt: String,
    val contextBlock: String,
    val userPrompt: String,
)

@Serializable
data class GenerateJsonRequest(
    val prompt: String,
    val jsonSchema: String? = null,
)

@Serializable
data class GenerateJsonWithContextRequest(
    val systemPrompt: String,
    val contextBlock: String,
    val userPrompt: String,
    val jsonSchema: String? = null,
)

@Serializable
data class GenerateResponse(val text: String)

@Serializable
data class GenerateContinueRequest(val prompt: String)

@Serializable
data class UpdateParamsRequest(
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val contextLength: Int = 4096,
    val numThreads: Int = 4,
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false,
    val batchSize: Int = 512,
    val gpuLayers: Int = 0,
)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class SessionSaveRequest(val path: String)

@Serializable
data class SessionLoadRequest(val path: String)

@Serializable
data class SessionResult(val ok: Boolean)

@Serializable
data class ModelFinetuneTypeResponse(val finetuneType: String?)

@Serializable
data class ModelChatTemplateResponse(val chatTemplate: String?)

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ApplyChatTemplateRequest(
    val messages: List<ChatMessageDto>,
    val addAssistantPrefix: Boolean = true,
)

@Serializable
data class ApplyChatTemplateResponse(val prompt: String?)

@Serializable
data class InitMtpRequest(val modelPath: String, val draftLen: Int = 3)
