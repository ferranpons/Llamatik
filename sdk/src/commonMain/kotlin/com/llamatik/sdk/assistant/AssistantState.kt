package com.llamatik.sdk.assistant

import com.llamatik.sdk.chat.ChatSessionSummary
import com.llamatik.sdk.model.GenerateSettings
import com.llamatik.sdk.model.LlamaModel

data class AssistantState(
    val generateModels: List<LlamaModel> = emptyList(),
    val embedModels: List<LlamaModel> = emptyList(),
    val sttModels: List<LlamaModel> = emptyList(),
    val stableDiffusionModels: List<LlamaModel> = emptyList(),
    val vlmModels: List<LlamaModel> = emptyList(),
    val selectedGenerateModelName: String? = null,
    val selectedEmbedModelName: String? = null,
    val selectedSttModelName: String? = null,
    val selectedStableDiffusionModelName: String? = null,
    val selectedVlmModelName: String? = null,
    val isGenerateModelLoaded: Boolean = false,
    val isEmbedModelLoaded: Boolean = false,
    val isSttModelLoaded: Boolean = false,
    val isStableDiffusionModelLoaded: Boolean = false,
    val isVlmModelLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val isInitialSetup: Boolean = false,
    val initialSetupModelName: String? = null,
    val initialSetupProgress: Int = 0,
    val generateSettings: GenerateSettings = GenerateSettings(),
    val chatSessions: List<ChatSessionSummary> = emptyList(),
    val isTemporaryChat: Boolean = false,
    val generationMode: GenerationMode = GenerationMode.TEXT,
    val pendingVisionImageBytes: ByteArray? = null,
    val pendingImg2ImgBytes: ByteArray? = null,
    val img2ImgStrength: Float = 0.75f,
    val ragPdfFileName: String? = null,
    val isRagIndexing: Boolean = false,
    val ragIndexingProgress: Int = 0,
    val ragChunksCount: Int = 0,
    val downloadStates: Map<String, DownloadState> = emptyMap(),
)

enum class GenerationMode { TEXT, IMAGE, IMAGE_TO_IMAGE, VISION }

data class DownloadState(
    val inProgress: Boolean = false,
    val progress: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
)

sealed interface AssistantEvent {
    data object Loaded : AssistantEvent
    data object MessageLoading : AssistantEvent
    data object MessageLoaded : AssistantEvent
    data object NoResults : AssistantEvent
    data object LoadError : AssistantEvent
    data object ScrollToBottom : AssistantEvent
    data object GenerateModelLoaded : AssistantEvent
    data object GenerateModelLoadError : AssistantEvent
    data object EmbedModelLoaded : AssistantEvent
    data object EmbedModelLoadError : AssistantEvent
    data object SttModelLoaded : AssistantEvent
    data object SttModelLoadError : AssistantEvent
    data object VlmModelLoaded : AssistantEvent
    data object VlmModelLoadError : AssistantEvent
    data object StableDiffusionModelLoaded : AssistantEvent
    data object StableDiffusionModelLoadError : AssistantEvent
    data class CacheCleared(val message: String) : AssistantEvent
    data class CacheClearFailed(val message: String) : AssistantEvent
    data class ToolCallDetected(val toolId: String, val rawJson: String) : AssistantEvent
}
