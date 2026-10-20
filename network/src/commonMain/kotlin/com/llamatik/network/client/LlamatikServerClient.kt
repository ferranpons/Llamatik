package com.llamatik.network.client

import co.touchlab.kermit.Logger
import com.llamatik.network.client.dto.AnalyzeImageRequest
import com.llamatik.network.client.dto.ApplyChatTemplateRequest
import com.llamatik.network.client.dto.ApplyChatTemplateResponse
import com.llamatik.network.client.dto.ChatMessageDto
import com.llamatik.network.client.dto.EmbedRequest
import com.llamatik.network.client.dto.EmbedResponse
import com.llamatik.network.client.dto.GenerateContinueRequest
import com.llamatik.network.client.dto.GenerateJsonRequest
import com.llamatik.network.client.dto.GenerateJsonWithContextRequest
import com.llamatik.network.client.dto.GenerateRequest
import com.llamatik.network.client.dto.GenerateResponse
import com.llamatik.network.client.dto.GenerateWithContextRequest
import com.llamatik.network.client.dto.ImageResponse
import com.llamatik.network.client.dto.InitModelRequest
import com.llamatik.network.client.dto.InitModelResponse
import com.llamatik.network.client.dto.InitMtpRequest
import com.llamatik.network.client.dto.ModelChatTemplateResponse
import com.llamatik.network.client.dto.ModelFinetuneTypeResponse
import com.llamatik.network.client.dto.MultimodalInitRequest
import com.llamatik.network.client.dto.OkResponse
import com.llamatik.network.client.dto.SessionResult
import com.llamatik.network.client.dto.SseEventDto
import com.llamatik.network.client.dto.StableDiffusionInitRequest
import com.llamatik.network.client.dto.TranscribeRequest
import com.llamatik.network.client.dto.TranscribeResponse
import com.llamatik.network.client.dto.TranscribeSegmentsRequest
import com.llamatik.network.client.dto.TranscribeSegmentsResponse
import com.llamatik.network.client.dto.Txt2ImgRequest
import com.llamatik.network.client.dto.UpdateParamsRequest
import com.llamatik.network.client.dto.WhisperInitRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.contentType
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class LlamatikServerClient(
    val config: LlamatikServerConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    // --- Generation ---

    suspend fun initModel(modelPath: String): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/init") {
            contentType(ContentType.Application.Json)
            setBody(InitModelRequest(modelPath))
        }.body<InitModelResponse>().ok
    }.getOrElse { e -> Logger.e(e) { "initModel failed" }; false }

    suspend fun generate(prompt: String): String =
        httpClient.post("${config.baseUrl}/generation/generate") {
            contentType(ContentType.Application.Json)
            setBody(GenerateRequest(prompt))
        }.body<GenerateResponse>().text

    fun streamGenerate(prompt: String): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/generation/stream") {
            contentType(ContentType.Application.Json)
            setBody(GenerateRequest(prompt))
        }.bodyAsChannel()
        parseSse(ch)
    }

    fun streamGenerateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
    ): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/generation/streamWithContext") {
            contentType(ContentType.Application.Json)
            setBody(GenerateWithContextRequest(systemPrompt, contextBlock, userPrompt))
        }.bodyAsChannel()
        parseSse(ch)
    }

    fun streamContinue(prompt: String): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/generation/continueStream") {
            contentType(ContentType.Application.Json)
            setBody(GenerateContinueRequest(prompt))
        }.bodyAsChannel()
        parseSse(ch)
    }

    fun streamGenerateJson(prompt: String, jsonSchema: String? = null): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/generation/jsonStream") {
            contentType(ContentType.Application.Json)
            setBody(GenerateJsonRequest(prompt, jsonSchema))
        }.bodyAsChannel()
        parseSse(ch)
    }

    fun streamGenerateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null,
    ): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/generation/jsonStreamWithContext") {
            contentType(ContentType.Application.Json)
            setBody(GenerateJsonWithContextRequest(systemPrompt, contextBlock, userPrompt, jsonSchema))
        }.bodyAsChannel()
        parseSse(ch)
    }

    suspend fun updateParams(params: UpdateParamsRequest): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/params") {
            contentType(ContentType.Application.Json)
            setBody(params)
        }.body<OkResponse>().ok
    }.getOrElse { false }

    suspend fun cancelGeneration(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/cancel").body<OkResponse>().ok
    }.getOrElse { false }

    suspend fun shutdownModel(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/shutdown").body<OkResponse>().ok
    }.getOrElse { false }

    suspend fun resetSession(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/session/reset").body<SessionResult>().ok
    }.getOrElse { false }

    suspend fun initMtp(modelPath: String, draftLen: Int = 3): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/mtp/init") {
            contentType(ContentType.Application.Json)
            setBody(InitMtpRequest(modelPath, draftLen))
        }.body<InitModelResponse>().ok
    }.getOrElse { false }

    suspend fun shutdownMtp(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/generation/mtp/shutdown").body<OkResponse>().ok
    }.getOrElse { false }

    suspend fun getFinetuneType(): String? = runCatching {
        httpClient.get("${config.baseUrl}/generation/model/finetuneType")
            .body<ModelFinetuneTypeResponse>().finetuneType
    }.getOrNull()

    suspend fun getChatTemplate(): String? = runCatching {
        httpClient.get("${config.baseUrl}/generation/model/chatTemplate")
            .body<ModelChatTemplateResponse>().chatTemplate
    }.getOrNull()

    suspend fun applyChatTemplate(
        messages: List<ChatMessageDto>,
        addAssistantPrefix: Boolean = true,
    ): String? = runCatching {
        httpClient.post("${config.baseUrl}/generation/model/applyChatTemplate") {
            contentType(ContentType.Application.Json)
            setBody(ApplyChatTemplateRequest(messages, addAssistantPrefix))
        }.body<ApplyChatTemplateResponse>().prompt
    }.getOrNull()

    // --- Embeddings ---

    suspend fun initEmbedModel(modelPath: String): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/embeddings/init") {
            contentType(ContentType.Application.Json)
            setBody(InitModelRequest(modelPath))
        }.body<InitModelResponse>().ok
    }.getOrElse { false }

    suspend fun embed(input: String): List<Float> =
        httpClient.post("${config.baseUrl}/embeddings/embed") {
            contentType(ContentType.Application.Json)
            setBody(EmbedRequest(input))
        }.body<EmbedResponse>().embedding

    // --- Multimodal ---

    suspend fun initMultimodal(modelPath: String, mmprojPath: String): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/multimodal/init") {
            contentType(ContentType.Application.Json)
            setBody(MultimodalInitRequest(modelPath, mmprojPath))
        }.body<InitModelResponse>().ok
    }.getOrElse { false }

    fun streamAnalyzeImage(imageBase64: String, prompt: String): Flow<String> = channelFlow {
        val ch = httpClient.post("${config.baseUrl}/multimodal/analyzeStream") {
            contentType(ContentType.Application.Json)
            setBody(AnalyzeImageRequest(imageBase64, prompt))
        }.bodyAsChannel()
        parseSse(ch)
    }

    suspend fun cancelMultimodal(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/multimodal/cancel").body<OkResponse>().ok
    }.getOrElse { false }

    // --- Whisper ---

    suspend fun initWhisper(modelPath: String): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/whisper/init") {
            contentType(ContentType.Application.Json)
            setBody(WhisperInitRequest(modelPath))
        }.body<InitModelResponse>().ok
    }.getOrElse { false }

    suspend fun transcribe(
        wavPath: String,
        language: String? = null,
        initialPrompt: String? = null,
    ): String = httpClient.post("${config.baseUrl}/whisper/transcribe") {
        contentType(ContentType.Application.Json)
        setBody(TranscribeRequest(wavPath, language, initialPrompt))
    }.body<TranscribeResponse>().text

    suspend fun transcribeSegments(
        wavPath: String,
        language: String? = null,
        initialPrompt: String? = null,
        translate: Boolean = false,
        diarize: Boolean = false,
    ): String = httpClient.post("${config.baseUrl}/whisper/transcribeSegments") {
        contentType(ContentType.Application.Json)
        setBody(TranscribeSegmentsRequest(wavPath, language, initialPrompt, translate, diarize))
    }.body<TranscribeSegmentsResponse>().json

    // --- Stable Diffusion ---

    suspend fun initStableDiffusion(modelPath: String, threads: Int = -1): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/stablediffusion/init") {
            contentType(ContentType.Application.Json)
            setBody(StableDiffusionInitRequest(modelPath, threads))
        }.body<InitModelResponse>().ok
    }.getOrElse { false }

    suspend fun txt2img(request: Txt2ImgRequest): String =
        httpClient.post("${config.baseUrl}/stablediffusion/txt2img") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<ImageResponse>().imageBase64

    suspend fun releaseStableDiffusion(): Boolean = runCatching {
        httpClient.post("${config.baseUrl}/stablediffusion/release").body<OkResponse>().ok
    }.getOrElse { false }

    // --- Health ---

    suspend fun isReachable(): Boolean = runCatching {
        httpClient.get("http://${config.host}:${config.port}/").status.isSuccess()
    }.getOrElse { false }

    // --- SSE parser ---

    private suspend fun ProducerScope<String>.parseSse(byteChannel: ByteReadChannel) {
        try {
            while (!byteChannel.isClosedForRead && isActive) {
                val line = byteChannel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty()) continue
                    try {
                        val event = json.decodeFromString<SseEventDto>(data)
                        when (event.event) {
                            "delta" -> event.text?.let { send(it) }
                            "done" -> break
                            "error" -> error(event.message ?: "Remote inference error")
                        }
                    } catch (e: IllegalStateException) {
                        throw e
                    } catch (_: Exception) {
                        // skip malformed SSE line
                    }
                }
            }
        } finally {
            byteChannel.cancel()
        }
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
