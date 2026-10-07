package com.llamatik.sdk.assistant

import co.touchlab.kermit.Logger
import com.llamatik.core.platform.LlamaBridge
import com.llamatik.core.platform.LlamaSession
import com.llamatik.core.platform.MultimodalBridge
import com.llamatik.core.platform.StableDiffusionBridge
import com.llamatik.core.platform.WhisperBridge
import com.llamatik.sdk.agent.ParsedToolCall
import com.llamatik.sdk.agent.ToolCallParser
import com.llamatik.sdk.chat.ChatHistoryRepository
import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.chat.ChatRunner
import com.llamatik.sdk.chat.ChatSession
import com.llamatik.sdk.chat.Gemma3
import com.llamatik.sdk.chat.PersistedAuthor
import com.llamatik.sdk.chat.PersistedChatMessage
import com.llamatik.sdk.chat.PromptTemplate
import com.llamatik.sdk.download.DownloadEvent
import com.llamatik.sdk.download.ModelDownloadOrchestrator
import com.llamatik.sdk.model.GenerateSettings
import com.llamatik.sdk.model.GetModelsUseCase
import com.llamatik.sdk.model.LlamaModel
import com.llamatik.sdk.model.isVlm
import com.llamatik.sdk.rag.PersistedRagStore
import com.llamatik.sdk.rag.VectorStoreData
import com.llamatik.sdk.rag.VectorStoreItem
import com.llamatik.sdk.rag.chunkText
import com.llamatik.sdk.rag.cosineD
import com.llamatik.sdk.rag.retrieveContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val COSINE_THRESHOLD = 0.15

class Assistant(
    private val scope: CoroutineScope,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val getModelsUseCase: GetModelsUseCase,
    private val modelDownloadOrchestrator: ModelDownloadOrchestrator,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val pathResolver: ModelPathResolver,
    private val ragStorage: RagStorage,
    private val systemPromptOverride: String? = null,
) {
    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _conversation = MutableStateFlow(emptyList<ConversationMessage>())
    val conversation: StateFlow<List<ConversationMessage>> = _conversation.asStateFlow()

    private val _events = Channel<AssistantEvent>(Channel.BUFFERED)
    val events: Flow<AssistantEvent> = _events.receiveAsFlow()

    @Volatile private var activeRequestId: String? = null
    @Volatile private var activeSession: LlamaSession? = null

    private val downloadJobs = mutableMapOf<String, Job>()
    private var currentChatId: String? = null
    private var vectorStore: VectorStoreData? = null

    // region — Model initialisation

    suspend fun loadAvailableModels() {
        getModelsUseCase.getDefaultEmbedModels()
            .onSuccess { models ->
                for (model in models) {
                    val path = pathResolver.resolve(model) ?: continue
                    val loaded = LlamaBridge.initEmbedModel(path)
                    if (loaded) {
                        _state.update { it.copy(selectedEmbedModelName = model.name, isEmbedModelLoaded = true) }
                        _events.trySend(AssistantEvent.EmbedModelLoaded)
                        break
                    } else {
                        _events.trySend(AssistantEvent.EmbedModelLoadError)
                    }
                }
                _state.update { it.copy(embedModels = normalizeModelPaths(models)) }
            }

        getModelsUseCase.getDefaultStableDiffusionModels()
            .onSuccess { models -> _state.update { it.copy(stableDiffusionModels = models) } }

        getModelsUseCase.getDefaultVlmModels()
            .onSuccess { models ->
                for (model in models) {
                    val path = pathResolver.resolve(model) ?: continue
                    val mmprojPath = pathResolver.resolveMmproj(model) ?: continue
                    val loaded = MultimodalBridge.initModel(path, mmprojPath)
                    if (loaded) {
                        _state.update { it.copy(selectedVlmModelName = model.name, isVlmModelLoaded = true) }
                        _events.trySend(AssistantEvent.VlmModelLoaded)
                        break
                    } else {
                        _events.trySend(AssistantEvent.VlmModelLoadError)
                    }
                }
                _state.update { it.copy(vlmModels = normalizeVlmModelPaths(models)) }
            }

        getModelsUseCase.getDefaultSTTModels()
            .onSuccess { models ->
                for (model in models) {
                    val path = pathResolver.resolve(model) ?: continue
                    val loaded = WhisperBridge.initModel(path)
                    if (loaded) {
                        _state.update { it.copy(selectedSttModelName = model.name, isSttModelLoaded = true) }
                        _events.trySend(AssistantEvent.SttModelLoaded)
                        break
                    } else {
                        _events.trySend(AssistantEvent.SttModelLoadError)
                    }
                }
                _state.update { it.copy(sttModels = normalizeModelPaths(models)) }
            }

        val summaries = chatHistoryRepository.getSummaries()
        _state.update { it.copy(chatSessions = summaries) }

        getModelsUseCase.getDefaultGenerateModels()
            .onSuccess { models ->
                for (model in models) {
                    val path = pathResolver.resolve(model) ?: continue
                    val loaded = LlamaBridge.initGenerateModel(path)
                    if (loaded) {
                        _state.update { it.copy(selectedGenerateModelName = model.name, isGenerateModelLoaded = true) }
                        _events.trySend(AssistantEvent.GenerateModelLoaded)
                        break
                    } else {
                        _events.trySend(AssistantEvent.GenerateModelLoadError)
                    }
                }
                _state.update { it.copy(generateModels = normalizeModelPaths(models)) }
            }

        runCatching { loadPersistedPdfRagStoreIfAny() }
            .onFailure { Logger.w(it) { "RAG — failed to load persisted PDF store" } }

        applyGenerateSettings(_state.value.generateSettings)
        _events.trySend(AssistantEvent.Loaded)
    }

    fun applyGenerateSettings(settings: GenerateSettings) {
        _state.update { it.copy(generateSettings = settings) }
        LlamaBridge.updateGenerateParams(
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            topP = settings.topP,
            topK = settings.topK,
            repeatPenalty = settings.repeatPenalty,
            contextLength = settings.contextLength,
            numThreads = settings.numThreads,
            useMmap = settings.useMmap,
            flashAttention = settings.flashAttention,
            batchSize = settings.batchSize,
        )
    }

    fun selectGenerateModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            val path = pathResolver.resolve(model) ?: run {
                _events.trySend(AssistantEvent.GenerateModelLoadError)
                return@launch
            }
            val loaded = LlamaBridge.initGenerateModel(path)
            if (loaded) {
                _state.update { it.copy(selectedGenerateModelName = model.name, isGenerateModelLoaded = true) }
                _events.trySend(AssistantEvent.GenerateModelLoaded)
            } else {
                _state.update { it.copy(isGenerateModelLoaded = false) }
                _events.trySend(AssistantEvent.GenerateModelLoadError)
            }
        }
    }

    fun selectEmbedModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            val path = pathResolver.resolve(model) ?: run {
                _events.trySend(AssistantEvent.EmbedModelLoadError)
                return@launch
            }
            val loaded = LlamaBridge.initEmbedModel(path)
            if (loaded) {
                _state.update { it.copy(selectedEmbedModelName = model.name, isEmbedModelLoaded = true) }
                _events.trySend(AssistantEvent.EmbedModelLoaded)
            } else {
                _state.update { it.copy(isEmbedModelLoaded = false) }
                _events.trySend(AssistantEvent.EmbedModelLoadError)
            }
        }
    }

    fun selectSttModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            val path = pathResolver.resolve(model) ?: run {
                _events.trySend(AssistantEvent.SttModelLoadError)
                return@launch
            }
            val loaded = WhisperBridge.initModel(path)
            if (loaded) {
                _state.update { it.copy(selectedSttModelName = model.name, isSttModelLoaded = true) }
                _events.trySend(AssistantEvent.SttModelLoaded)
            } else {
                _events.trySend(AssistantEvent.SttModelLoadError)
            }
        }
    }

    fun selectStableDiffusionModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            val path = pathResolver.resolve(model) ?: return@launch
            val loaded = StableDiffusionBridge.initModel(path)
            if (loaded) {
                _state.update { it.copy(selectedStableDiffusionModelName = model.name, isStableDiffusionModelLoaded = true) }
                _events.trySend(AssistantEvent.StableDiffusionModelLoaded)
            } else {
                _events.trySend(AssistantEvent.StableDiffusionModelLoadError)
            }
        }
    }

    fun selectVlmModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            val path = pathResolver.resolve(model) ?: return@launch
            val mmprojPath = pathResolver.resolveMmproj(model) ?: run {
                val mmprojUrl = model.mmprojUrl
                if (mmprojUrl != null) {
                    downloadMmprojIfNeeded(model, mmprojUrl, path)
                } else {
                    _events.trySend(AssistantEvent.VlmModelLoadError)
                }
                return@launch
            }
            val loaded = MultimodalBridge.initModel(path, mmprojPath)
            if (loaded) {
                _state.update { it.copy(selectedVlmModelName = model.name, isVlmModelLoaded = true) }
                _events.trySend(AssistantEvent.VlmModelLoaded)
            } else {
                _state.update { it.copy(isVlmModelLoaded = false) }
                _events.trySend(AssistantEvent.VlmModelLoadError)
            }
        }
    }

    // endregion

    // region — Download management

    fun downloadModel(model: LlamaModel) {
        val url = model.url
        if (downloadJobs[url]?.isActive == true) return

        val job = scope.launch(ioDispatcher) {
            updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

            modelDownloadOrchestrator.download(model).collect { ev ->
                when (ev) {
                    is DownloadEvent.Progress -> updateDownload(url) { it.copy(inProgress = true, progress = ev.percent) }

                    is DownloadEvent.Completed -> {
                        updateDownload(url) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
                        val persistedPath = pathResolver.persistDownloaded(model, ev.localPath)

                        _state.update { s ->
                            s.copy(
                                embedModels = s.embedModels.updatePath(url, persistedPath),
                                generateModels = s.generateModels.updatePath(url, persistedPath),
                                sttModels = s.sttModels.updatePath(url, persistedPath),
                                stableDiffusionModels = s.stableDiffusionModels.updatePath(url, persistedPath),
                                vlmModels = s.vlmModels.updatePath(url, persistedPath),
                            )
                        }

                        val isGenerateModel = _state.value.generateModels.any { it.url == url }
                        if (isGenerateModel && !_state.value.isGenerateModelLoaded && persistedPath.isNotBlank()) {
                            val loaded = LlamaBridge.initGenerateModel(persistedPath)
                            if (loaded) {
                                _state.update { it.copy(selectedGenerateModelName = model.name, isGenerateModelLoaded = true) }
                                _events.trySend(AssistantEvent.GenerateModelLoaded)
                            } else {
                                _events.trySend(AssistantEvent.GenerateModelLoadError)
                            }
                        }

                        if (model.isVlm) {
                            val mmprojUrl = model.mmprojUrl ?: return@collect
                            downloadMmprojIfNeeded(model, mmprojUrl, persistedPath)
                        }
                    }

                    is DownloadEvent.Failed -> updateDownload(url) { it.copy(inProgress = false, done = false, error = ev.message) }
                }
            }
        }
        downloadJobs[url] = job
    }

    fun cancelDownload(model: LlamaModel) {
        val url = model.url
        downloadJobs[url]?.cancel()
        downloadJobs.remove(url)
        modelDownloadOrchestrator.cancel(model)
        updateDownload(url) { it.copy(inProgress = false, done = false, progress = 0, error = "Cancelled") }
    }

    fun deleteModel(model: LlamaModel) {
        scope.launch(ioDispatcher) {
            try {
                val path = pathResolver.resolve(model)
                if (!path.isNullOrBlank()) {
                    pathResolver.deleteFile(model.name, path)
                }
                pathResolver.clearSavedPath(model.name)

                if (model.isVlm) {
                    val mmprojPath = model.mmprojLocalPath
                    if (!mmprojPath.isNullOrBlank()) {
                        runCatching { pathResolver.deleteFile("${model.name}_mmproj", mmprojPath) }
                    }
                    pathResolver.clearSavedPath("${model.name}_mmproj")
                }

                _state.update { s ->
                    s.copy(
                        embedModels = s.embedModels.clearPath(model.url),
                        generateModels = s.generateModels.clearPath(model.url),
                        sttModels = s.sttModels.clearPath(model.url),
                        stableDiffusionModels = s.stableDiffusionModels.clearPath(model.url),
                        vlmModels = s.vlmModels.clearVlmPath(model.url),
                        selectedSttModelName = if (s.selectedSttModelName == model.name) null else s.selectedSttModelName,
                        isSttModelLoaded = if (s.selectedSttModelName == model.name) false else s.isSttModelLoaded,
                        selectedStableDiffusionModelName = if (s.selectedStableDiffusionModelName == model.name) null else s.selectedStableDiffusionModelName,
                        isStableDiffusionModelLoaded = if (s.selectedStableDiffusionModelName == model.name) false else s.isStableDiffusionModelLoaded,
                        selectedVlmModelName = if (s.selectedVlmModelName == model.name) null else s.selectedVlmModelName,
                        isVlmModelLoaded = if (s.selectedVlmModelName == model.name) false else s.isVlmModelLoaded,
                    )
                }
            } catch (t: Throwable) {
                Logger.e(t) { "Assistant — error deleting model ${model.name}" }
            }
        }
    }

    fun clearAllCachedModels(
        allCachedModelsRemovedMessage: String,
        ragStorePath: String,
    ) {
        scope.launch(ioDispatcher) {
            try {
                val allModels = (_state.value.generateModels +
                        _state.value.embedModels +
                        _state.value.sttModels +
                        _state.value.stableDiffusionModels +
                        _state.value.vlmModels).distinctBy { it.url }

                for (model in allModels) {
                    runCatching {
                        val path = pathResolver.resolve(model)
                        if (!path.isNullOrBlank()) pathResolver.deleteFile(model.name, path)
                    }
                    runCatching { pathResolver.clearSavedPath(model.name) }
                }

                runCatching { ragStorage.delete(ragStorePath) }
                vectorStore = null

                _state.update { s ->
                    s.copy(
                        generateModels = s.generateModels.clearPath(),
                        embedModels = s.embedModels.clearPath(),
                        sttModels = s.sttModels.clearPath(),
                        stableDiffusionModels = s.stableDiffusionModels.clearPath(),
                        vlmModels = s.vlmModels.clearVlmPath(),
                        selectedEmbedModelName = null,
                        selectedGenerateModelName = null,
                        selectedSttModelName = null,
                        selectedStableDiffusionModelName = null,
                        selectedVlmModelName = null,
                        isEmbedModelLoaded = false,
                        isGenerateModelLoaded = false,
                        isSttModelLoaded = false,
                        isStableDiffusionModelLoaded = false,
                        isVlmModelLoaded = false,
                        ragPdfFileName = null,
                        isRagIndexing = false,
                        ragIndexingProgress = 0,
                        ragChunksCount = 0,
                    )
                }

                _events.trySend(AssistantEvent.CacheCleared(allCachedModelsRemovedMessage))
            } catch (t: Throwable) {
                Logger.e(t) { "Assistant — failed to clear cached models" }
                _events.trySend(AssistantEvent.CacheClearFailed(t.message ?: "Unknown error"))
            }
        }
    }

    // endregion

    // region — Auto-setup (first launch)

    suspend fun startInitialSetupIfNeeded(
        models: List<LlamaModel>,
        preferredModelPredicate: (LlamaModel) -> Boolean = { it.name.contains("gemma 3", ignoreCase = true) || it.name.contains("gemma3", ignoreCase = true) },
    ) {
        if (models.isEmpty()) return
        val hasLocal = models.any { !pathResolver.resolve(it).isNullOrBlank() }
        if (hasLocal) return

        val defaultModel = models.firstOrNull(preferredModelPredicate) ?: models.first()
        val url = defaultModel.url

        _state.update { it.copy(isInitialSetup = true, initialSetupModelName = defaultModel.name, initialSetupProgress = 0) }
        updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

        getModelsUseCase.downloadModel(url) { bytes, totalBytes ->
            val progress = if (totalBytes > 0) ((bytes.toFloat() / totalBytes.toFloat()) * 100f).toInt() else 0
            updateDownload(url) { it.copy(inProgress = true, progress = progress) }
            _state.update { it.copy(initialSetupProgress = progress) }
        }.onSuccess { tempFile ->
            val downloadedPath = tempFile.absolutePath()
            val persistedPath = pathResolver.persistDownloaded(defaultModel, downloadedPath)
            pathResolver.saveModelPath(defaultModel.name, persistedPath)

            _state.update { s ->
                s.copy(generateModels = s.generateModels.updatePath(url, persistedPath))
            }

            val loaded = LlamaBridge.initGenerateModel(persistedPath)
            if (loaded) {
                _state.update { it.copy(selectedGenerateModelName = defaultModel.name, isGenerateModelLoaded = true, isInitialSetup = false, initialSetupProgress = 100) }
                _events.trySend(AssistantEvent.GenerateModelLoaded)
            } else {
                _state.update { it.copy(isInitialSetup = false) }
                _events.trySend(AssistantEvent.GenerateModelLoadError)
            }
            updateDownload(url) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
        }.onFailure { error ->
            Logger.e(error) { "Assistant — initial setup download failed for ${defaultModel.name}" }
            _state.update { it.copy(isInitialSetup = false) }
            updateDownload(url) { it.copy(inProgress = false, error = error.message, done = false) }
        }
    }

    suspend fun startSttInitialSetupIfNeeded(
        models: List<LlamaModel>,
        preferredModelPredicate: (LlamaModel) -> Boolean = { it.name.contains("tiny", ignoreCase = true) },
    ) {
        if (models.isEmpty()) return
        val hasLocal = models.any { !pathResolver.resolve(it).isNullOrBlank() }
        if (hasLocal) return

        val defaultModel = models.firstOrNull(preferredModelPredicate) ?: models.first()
        val url = defaultModel.url

        _state.update { it.copy(isInitialSetup = true, initialSetupModelName = defaultModel.name, initialSetupProgress = 0) }
        updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

        getModelsUseCase.downloadModel(url) { bytes, totalBytes ->
            val progress = if (totalBytes > 0) ((bytes.toFloat() / totalBytes.toFloat()) * 100f).toInt() else 0
            updateDownload(url) { it.copy(inProgress = true, progress = progress) }
            _state.update { it.copy(initialSetupProgress = progress) }
        }.onSuccess { tempFile ->
            val downloadedPath = tempFile.absolutePath()
            val persistedPath = pathResolver.persistDownloaded(defaultModel, downloadedPath)
            pathResolver.saveModelPath(defaultModel.name, persistedPath)

            _state.update { s ->
                s.copy(sttModels = s.sttModels.updatePath(url, persistedPath))
            }

            val loaded = WhisperBridge.initModel(persistedPath)
            if (loaded) {
                _state.update { it.copy(selectedSttModelName = defaultModel.name, isSttModelLoaded = true, isInitialSetup = false, initialSetupProgress = 100) }
                _events.trySend(AssistantEvent.SttModelLoaded)
            } else {
                _state.update { it.copy(isInitialSetup = false) }
                _events.trySend(AssistantEvent.SttModelLoadError)
            }
            updateDownload(url) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
        }.onFailure { error ->
            Logger.e(error) { "Assistant — STT initial setup download failed for ${defaultModel.name}" }
            _state.update { it.copy(isInitialSetup = false) }
            updateDownload(url) { it.copy(inProgress = false, error = error.message, done = false) }
        }
    }

    // endregion

    // region — Chat generation

    fun chat(
        message: String,
        systemPrompt: String? = null,
        languageHint: String? = null,
        errorMessage: String = "There was a problem with the AI",
    ) {
        if (_state.value.isGenerating) return
        val input = message.trim().ifBlank { return }

        scope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) {
                currentChatId = Random.nextLong().toString()
            }

            _conversation.update { it + ConversationMessage(input, ConversationMessage.Author.ME) }
            _events.trySend(AssistantEvent.MessageLoading)
            _events.trySend(AssistantEvent.ScrollToBottom)
            _state.update { it.copy(isGenerating = true) }

            kotlinx.coroutines.withContext(ioDispatcher) {
                try {
                    persistConversationIfNeeded()
                    _conversation.update { it + ConversationMessage("", ConversationMessage.Author.BOT) }

                    val chatHistory = toSdkMessages(_conversation.value.dropLast(1))
                        .withLanguageHint(languageHint)

                    val requestId = Random.nextLong().toString()
                    activeRequestId = requestId

                    activeSession?.close()
                    activeSession = null

                    val acc = StringBuilder()
                    var completed = false
                    val priorBotTexts = chatHistory.filter { it.role == ChatMessage.Role.Assistant }.map { it.content }
                    val settings = _state.value.generateSettings

                    try {
                        ChatRunner.stream(
                            system = resolveSystemPrompt(systemPrompt, languageHint),
                            messages = chatHistory,
                            template = currentGenerateTemplate(),
                            maxTokens = settings.maxTokens,
                            contextTokens = settings.contextLength,
                            onDelta = { chunk ->
                                if (activeRequestId != requestId || completed) return@stream
                                if (chunk.isEmpty()) return@stream
                                acc.append(chunk)
                                _conversation.update { msgs ->
                                    msgs.dropLast(1) + ConversationMessage(acc.toString(), ConversationMessage.Author.BOT)
                                }
                                _events.trySend(AssistantEvent.ScrollToBottom)
                                if (looksLikeEchoOrLoop(acc.toString(), input, priorBotTexts)) {
                                    completed = true
                                    activeRequestId = null
                                    _conversation.update { msgs ->
                                        msgs.dropLast(1) + ConversationMessage(trimLoop(acc.toString(), input, priorBotTexts), ConversationMessage.Author.BOT)
                                    }
                                    _state.update { it.copy(isGenerating = false) }
                                    _events.trySend(AssistantEvent.MessageLoaded)
                                    return@stream
                                }
                                if (looksLikeBabble(acc.toString())) {
                                    completed = true
                                    activeRequestId = null
                                    _conversation.update { msgs ->
                                        msgs.dropLast(1) + ConversationMessage(acc.toString().trim().trimEnd(',', ' ', '\n'), ConversationMessage.Author.BOT)
                                    }
                                    _state.update { it.copy(isGenerating = false) }
                                    _events.trySend(AssistantEvent.MessageLoaded)
                                }
                            },
                            onComplete = { final ->
                                if (activeRequestId != requestId || completed) return@stream
                                completed = true
                                activeRequestId = null
                                _conversation.update { msgs ->
                                    msgs.dropLast(1) + ConversationMessage(final, ConversationMessage.Author.BOT)
                                }
                                _state.update { it.copy(isGenerating = false) }
                                _events.trySend(AssistantEvent.MessageLoaded)
                                if (systemPromptOverride != null) {
                                    detectAndEmitToolCall(final)
                                }
                            },
                            onError = { err ->
                                if (activeRequestId != requestId) return@stream
                                _conversation.update { msgs ->
                                    msgs.dropLast(1) + ConversationMessage("$errorMessage: $err", ConversationMessage.Author.BOT)
                                }
                                activeRequestId = null
                                _state.update { it.copy(isGenerating = false) }
                                _events.trySend(AssistantEvent.LoadError)
                            }
                        )
                    } finally {
                        activeSession = null
                        persistConversationIfNeeded()
                        if (activeRequestId == null) _state.update { it.copy(isGenerating = false) }
                    }
                } catch (t: Throwable) {
                    emitBot("$errorMessage: ${t.message ?: "Unknown error"}")
                    activeRequestId = null
                    _state.update { it.copy(isGenerating = false) }
                    _events.trySend(AssistantEvent.LoadError)
                }
            }
        }
    }

    fun chatWithRag(
        message: String,
        systemPrompt: String? = null,
        languageHint: String? = null,
        noResultsMessage: String = "I don't have enough information in my sources.",
        errorMessage: String = "There was a problem with the AI",
    ) {
        if (_state.value.isGenerating) return
        val question = message.trim().ifBlank { return }

        scope.launch {
            _conversation.update { it + ConversationMessage(question, ConversationMessage.Author.ME) }
            _events.trySend(AssistantEvent.MessageLoading)
            _events.trySend(AssistantEvent.ScrollToBottom)
            _state.update { it.copy(isGenerating = true) }

            kotlinx.coroutines.withContext(ioDispatcher) {
                try {
                    val qArr = LlamaBridge.embed(question)
                    if (qArr.isEmpty()) {
                        emitBot("Failed to compute embeddings.")
                        _state.update { it.copy(isGenerating = false) }
                        return@withContext
                    }

                    val store = vectorStore ?: run {
                        _state.update { it.copy(isGenerating = false) }
                        return@withContext emitBot(errorMessage)
                    }

                    val qVec = qArr.toList()
                    val topItems = retrieveContext(qVec, question, store, poolSize = 80, topContext = 4)

                    if (topItems.isEmpty() || cosineD(qVec, topItems.maxBy { cosineD(qVec, it.vector) }.vector) < COSINE_THRESHOLD) {
                        emitBot(noResultsMessage)
                        _events.trySend(AssistantEvent.NoResults)
                        _events.trySend(AssistantEvent.ScrollToBottom)
                        _state.update { it.copy(isGenerating = false) }
                        return@withContext
                    }

                    val rawContext = topItems.joinToString("\n\n") { sanitizeForRag(it.text) }
                    val compact = buildCompactContext(rawContext, question, hardLimit = 1600)

                    _conversation.update { it + ConversationMessage("", ConversationMessage.Author.BOT) }

                    val chatHistory = toSdkMessages(_conversation.value.dropLast(1)).withLanguageHint(languageHint)
                    val requestId = Random.nextLong().toString()
                    activeRequestId = requestId
                    val acc = StringBuilder()
                    val settings = _state.value.generateSettings
                    val priorBotTexts = chatHistory.filter { it.role == ChatMessage.Role.Assistant }.map { it.content }

                    activeSession?.close()
                    activeSession = null

                    ChatRunner.stream(
                        system = resolveSystemPrompt(systemPrompt, languageHint),
                        contexts = listOf(compact),
                        messages = chatHistory,
                        template = currentGenerateTemplate(),
                        maxTokens = settings.maxTokens,
                        contextTokens = settings.contextLength,
                        onDelta = { chunk ->
                            if (activeRequestId != requestId) return@stream
                            if (chunk.isEmpty()) return@stream
                            acc.append(chunk)
                            _conversation.update { msgs ->
                                msgs.dropLast(1) + ConversationMessage(acc.toString(), ConversationMessage.Author.BOT)
                            }
                            _events.trySend(AssistantEvent.ScrollToBottom)
                            if (looksLikeEchoOrLoop(acc.toString(), question, priorBotTexts)) {
                                _conversation.update { msgs ->
                                    msgs.dropLast(1) + ConversationMessage(trimLoop(acc.toString(), question, priorBotTexts), ConversationMessage.Author.BOT)
                                }
                                activeRequestId = null
                                _events.trySend(AssistantEvent.MessageLoaded)
                                _events.trySend(AssistantEvent.ScrollToBottom)
                            }
                            _state.update { it.copy(isGenerating = false) }
                        },
                        onComplete = { final ->
                            if (activeRequestId != requestId) return@stream
                            _conversation.update { msgs ->
                                msgs.dropLast(1) + ConversationMessage(final, ConversationMessage.Author.BOT)
                            }
                            _events.trySend(AssistantEvent.MessageLoaded)
                            _events.trySend(AssistantEvent.ScrollToBottom)
                            _state.update { it.copy(isGenerating = false) }
                        },
                        onError = { err ->
                            if (activeRequestId != requestId) return@stream
                            _conversation.update { msgs ->
                                msgs.dropLast(1) + ConversationMessage("$errorMessage: $err", ConversationMessage.Author.BOT)
                            }
                            _events.trySend(AssistantEvent.LoadError)
                            _events.trySend(AssistantEvent.ScrollToBottom)
                            _state.update { it.copy(isGenerating = false) }
                        }
                    )
                    activeSession = null
                } catch (t: Throwable) {
                    activeSession = null
                    emitBot(errorMessage)
                    _events.trySend(AssistantEvent.LoadError)
                    _events.trySend(AssistantEvent.ScrollToBottom)
                    _state.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    // endregion

    // region — Image generation

    @OptIn(ExperimentalTime::class)
    fun generateImage(
        prompt: String,
        imageGenerationFailedMessage: String = "Image generation failed.",
        errorMessage: String = "Image generation error.",
    ) {
        if (_state.value.isGenerating) return
        val input = prompt.trim().ifBlank { return }

        scope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) currentChatId = Random.nextLong().toString()
            _conversation.update { it + ConversationMessage(input, ConversationMessage.Author.ME) }
            _events.trySend(AssistantEvent.MessageLoading)
            _events.trySend(AssistantEvent.ScrollToBottom)
            _state.update { it.copy(isGenerating = true) }

            kotlinx.coroutines.withContext(ioDispatcher) {
                try {
                    persistConversationIfNeeded()
                    _conversation.update { it + ConversationMessage("", ConversationMessage.Author.BOT) }

                    if (!_state.value.isStableDiffusionModelLoaded) {
                        updateLastBotMessage("Image mode enabled but no model loaded.")
                        _state.update { it.copy(isGenerating = false) }
                        _events.trySend(AssistantEvent.MessageLoaded)
                        return@withContext
                    }

                    val rgbaBytes = StableDiffusionBridge.txt2img(prompt = input, negativePrompt = "", width = 512, height = 512, steps = 20, seed = -1)

                    if (rgbaBytes.isEmpty()) {
                        updateLastBotMessage(imageGenerationFailedMessage)
                    } else {
                        val fileName = "sd_${Random.nextInt()}_${Clock.System.now().toString().replace(":", "_")}.png"
                        updateLastBotImageRgba(rgbaBytes, 512, 512, fileName)
                    }

                    persistConversationIfNeeded()
                } catch (t: Throwable) {
                    Logger.e(t.message ?: errorMessage)
                    updateLastBotMessage("Error: ${t.message ?: "unknown"}")
                } finally {
                    _state.update { it.copy(isGenerating = false) }
                    _events.trySend(AssistantEvent.MessageLoaded)
                    _events.trySend(AssistantEvent.ScrollToBottom)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun imageToImage(
        prompt: String,
        encodedBytes: ByteArray,
        strength: Float,
        decodeImageBytesToRgba: (ByteArray) -> Triple<ByteArray, Int, Int>?,
        imageGenerationFailedMessage: String = "Image generation failed.",
        errorMessage: String = "Image generation error.",
    ) {
        if (_state.value.isGenerating) return
        val input = prompt.trim().ifBlank { return }

        scope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) currentChatId = Random.nextLong().toString()
            _conversation.update { it + ConversationMessage(input, ConversationMessage.Author.ME) }
            _events.trySend(AssistantEvent.MessageLoading)
            _events.trySend(AssistantEvent.ScrollToBottom)
            _state.update { it.copy(isGenerating = true) }

            kotlinx.coroutines.withContext(ioDispatcher) {
                try {
                    persistConversationIfNeeded()
                    _conversation.update { it + ConversationMessage("", ConversationMessage.Author.BOT) }

                    if (!_state.value.isStableDiffusionModelLoaded) {
                        updateLastBotMessage("Image mode enabled but no model loaded.")
                        _state.update { it.copy(isGenerating = false) }
                        _events.trySend(AssistantEvent.MessageLoaded)
                        return@withContext
                    }

                    val decoded = decodeImageBytesToRgba(encodedBytes) ?: run {
                        updateLastBotMessage(imageGenerationFailedMessage)
                        _state.update { it.copy(isGenerating = false) }
                        _events.trySend(AssistantEvent.MessageLoaded)
                        return@withContext
                    }

                    val (initRgba, initW, initH) = decoded
                    fun snapTo64(v: Int) = (v.coerceIn(64, 1024) / 64) * 64
                    val outW = snapTo64(initW)
                    val outH = snapTo64(initH)

                    val rgbaBytes = StableDiffusionBridge.img2img(initRgba, initW, initH, input, "", outW, outH, 20, 7.0f, strength, -1L)

                    if (rgbaBytes.isEmpty()) {
                        updateLastBotMessage(imageGenerationFailedMessage)
                    } else {
                        val fileName = "img2img_${Random.nextInt()}_${Clock.System.now().toString().replace(":", "_")}.png"
                        updateLastBotImageRgba(rgbaBytes, outW, outH, fileName)
                    }

                    persistConversationIfNeeded()
                } catch (t: Throwable) {
                    Logger.e(t.message ?: errorMessage)
                    updateLastBotMessage("Error: ${t.message ?: "unknown"}")
                } finally {
                    _state.update { it.copy(isGenerating = false) }
                    _events.trySend(AssistantEvent.MessageLoaded)
                    _events.trySend(AssistantEvent.ScrollToBottom)
                }
            }
        }
    }

    // endregion

    // region — Vision (VLM)

    fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String,
        languageCode: String? = null,
        visionNoModelMessage: String = "Vision mode enabled but no model loaded.",
    ) {
        if (_state.value.isGenerating) return
        val userInput = prompt.trim().ifBlank { "Describe this image." }
        val input = if (languageCode != null && languageCode != "en") "$userInput\n\nReply in language code: $languageCode." else userInput

        scope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) currentChatId = Random.nextLong().toString()
            _state.update { it.copy(isGenerating = true) }
            _conversation.update { it + ConversationMessage(input, ConversationMessage.Author.ME) }
            _conversation.update { it + ConversationMessage("", ConversationMessage.Author.BOT) }
            _events.trySend(AssistantEvent.MessageLoading)
            _events.trySend(AssistantEvent.ScrollToBottom)

            val requestId = Random.nextLong().toString()
            activeRequestId = requestId
            val acc = StringBuilder()

            kotlinx.coroutines.withContext(ioDispatcher) {
                if (!_state.value.isVlmModelLoaded) {
                    updateLastBotMessage(visionNoModelMessage)
                    _state.update { it.copy(isGenerating = false) }
                    _events.trySend(AssistantEvent.MessageLoaded)
                    return@withContext
                }

                MultimodalBridge.analyzeImageBytesStream(
                    imageBytes = imageBytes,
                    prompt = input,
                    callback = object : com.llamatik.core.platform.GenStream {
                        override fun onDelta(text: String) {
                            if (activeRequestId != requestId) return
                            acc.append(text)
                            _conversation.update { msgs ->
                                if (msgs.isNotEmpty() && msgs.last().author == ConversationMessage.Author.BOT) {
                                    msgs.dropLast(1) + ConversationMessage(acc.toString(), ConversationMessage.Author.BOT)
                                } else msgs
                            }
                            _events.trySend(AssistantEvent.ScrollToBottom)
                        }

                        override fun onComplete() {
                            if (activeRequestId != requestId) return
                            activeRequestId = null
                            _state.update { it.copy(isGenerating = false) }
                            _events.trySend(AssistantEvent.MessageLoaded)
                            _events.trySend(AssistantEvent.ScrollToBottom)
                        }

                        override fun onError(message: String) {
                            activeRequestId = null
                            updateLastBotMessage("Vision error: $message")
                            _state.update { it.copy(isGenerating = false) }
                            _events.trySend(AssistantEvent.LoadError)
                        }
                    }
                )
            }
        }
    }

    // endregion

    // region — RAG / PDF indexing

    fun indexPdf(
        fileName: String,
        pdfBytes: ByteArray,
        extractPdfText: suspend (ByteArray) -> String,
        ragStorePath: String,
        pdfSelectFileMessage: String = "Please select a PDF file.",
        pdfExtractionErrorMessage: String = "Could not extract text from the PDF.",
        pdfEmbedModelNeededMessage: String = "Load an embed model to enable PDF search.",
        pdfNoUsableChunksMessage: String = "The PDF contained no usable text chunks.",
        pdfFailedEmbeddingsMessage: String = "Failed to compute embeddings.",
        pdfIndexedMessage: String = "PDF indexed for RAG",
        pdfFailedLoadMessage: String = "Failed to load PDF for RAG",
    ) {
        scope.launch {
            _state.update { it.copy(ragPdfFileName = fileName, isRagIndexing = true, ragIndexingProgress = 0, ragChunksCount = 0) }

            try {
                val text = kotlinx.coroutines.withContext(ioDispatcher) { extractPdfText(pdfBytes) }.trim()
                if (text.isBlank()) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(pdfExtractionErrorMessage)
                    return@launch
                }

                if (!_state.value.isEmbedModelLoaded) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(pdfEmbedModelNeededMessage)
                    return@launch
                }

                val chunks = chunkText(text, chunkSize = 1000, chunkOverlap = 200).filter { it.isNotBlank() }.take(2_000)
                if (chunks.isEmpty()) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(pdfNoUsableChunksMessage)
                    return@launch
                }

                val items = ArrayList<VectorStoreItem>(chunks.size)
                val total = chunks.size

                for ((index, chunk) in chunks.withIndex()) {
                    val vec = LlamaBridge.embed(chunk)
                    if (vec.isEmpty()) {
                        _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                        emitBot(pdfFailedEmbeddingsMessage)
                        return@launch
                    }
                    items += VectorStoreItem(
                        id = "${fileName}_${index}",
                        text = chunk,
                        vector = vec.toList(),
                        metadata = mapOf(
                            "source" to JsonPrimitive("pdf"),
                            "fileName" to JsonPrimitive(fileName),
                            "chunkIndex" to JsonPrimitive(index),
                        )
                    )
                    val progress = (((index + 1) * 100.0) / total.toDouble()).toInt().coerceIn(0, 100)
                    if (index % 10 == 0 || index == total - 1) _state.update { it.copy(ragIndexingProgress = progress) }
                }

                val store = VectorStoreData(items)
                vectorStore = store
                persistPdfRagStore(fileName, store, ragStorePath)

                _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 100, ragChunksCount = items.size, ragPdfFileName = fileName) }
                emitBot("$pdfIndexedMessage: $fileName (${items.size} chunks)")

            } catch (t: Throwable) {
                _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                emitBot("$pdfFailedLoadMessage: ${t.message ?: "unknown error"}")
            }
        }
    }

    // endregion

    // region — Stop / clear / session management

    fun stopGeneration() {
        if (!_state.value.isGenerating && activeRequestId == null) return
        activeSession?.cancel()
        activeSession?.close()
        activeSession = null
        LlamaBridge.nativeCancelGenerate()
        activeRequestId = null
        _state.update { it.copy(isGenerating = false) }

        val msgs = _conversation.value
        if (msgs.isNotEmpty() && msgs.last().author == ConversationMessage.Author.BOT && msgs.last().text.isBlank()) {
            _conversation.update { it.dropLast(1) }
        }
        _events.trySend(AssistantEvent.MessageLoaded)
        _events.trySend(AssistantEvent.ScrollToBottom)
    }

    fun clearConversation() {
        stopGeneration()
        currentChatId = null
        scope.launch { _conversation.emit(emptyList()) }
    }

    fun toggleTemporaryChat() {
        stopGeneration()
        currentChatId = null
        _conversation.update { emptyList() }
        _state.update { it.copy(isTemporaryChat = !it.isTemporaryChat) }
    }

    fun loadSession(chatId: String) {
        scope.launch(ioDispatcher) {
            val session = chatHistoryRepository.getSession(chatId) ?: return@launch
            stopGeneration()
            currentChatId = chatId
            val restored = session.messages.map {
                ConversationMessage(
                    text = it.text,
                    author = if (it.author == PersistedAuthor.ME) ConversationMessage.Author.ME else ConversationMessage.Author.BOT,
                )
            }
            _conversation.update { restored }
            _events.trySend(AssistantEvent.ScrollToBottom)
        }
    }

    fun deleteSession(chatId: String) {
        scope.launch(ioDispatcher) {
            chatHistoryRepository.delete(chatId)
            if (currentChatId == chatId) {
                currentChatId = null
                _conversation.update { emptyList() }
            }
            refreshSessions()
        }
    }

    fun setGenerationMode(mode: GenerationMode) {
        _state.update { s ->
            s.copy(
                generationMode = mode,
                pendingImg2ImgBytes = if (mode == GenerationMode.TEXT || mode == GenerationMode.VISION) null else s.pendingImg2ImgBytes,
            )
        }
    }

    fun setPendingVisionImage(bytes: ByteArray) {
        _state.update { it.copy(pendingVisionImageBytes = bytes, generationMode = GenerationMode.VISION) }
    }

    fun clearPendingVisionImage() {
        _state.update { it.copy(pendingVisionImageBytes = null, generationMode = GenerationMode.TEXT) }
    }

    fun setPendingImg2ImgImage(bytes: ByteArray) {
        _state.update { it.copy(pendingImg2ImgBytes = bytes, generationMode = GenerationMode.IMAGE_TO_IMAGE) }
    }

    fun clearPendingImg2ImgImage() {
        _state.update { it.copy(pendingImg2ImgBytes = null, generationMode = GenerationMode.IMAGE) }
    }

    fun setImg2ImgStrength(strength: Float) {
        _state.update { it.copy(img2ImgStrength = strength) }
    }

    fun dispose() {
        activeRequestId = null
        activeSession?.cancel()
        activeSession?.close()
        activeSession = null
        _state.update { it.copy(isGenerating = false) }
        LlamaBridge.shutdown()
    }

    // endregion

    // region — Private helpers

    private fun updateDownload(url: String, transform: (DownloadState) -> DownloadState) {
        val current = _state.value.downloadStates
        val updated = current.toMutableMap().apply { put(url, transform(current[url] ?: DownloadState())) }
        _state.update { it.copy(downloadStates = updated) }
    }

    private fun currentGenerateTemplate(): PromptTemplate {
        val s = _state.value
        return s.generateModels.firstOrNull { it.name == s.selectedGenerateModelName }?.template ?: Gemma3
    }

    private fun resolveSystemPrompt(override: String?, languageHint: String?): String {
        val base = override ?: systemPromptOverride
            ?: _state.value.generateModels.firstOrNull { it.name == _state.value.selectedGenerateModelName }?.systemPrompt
            ?: "You are a helpful AI assistant."
        return if (languageHint != null) "$base\nYou MUST reply exclusively in $languageHint. Do not switch to English or any other language." else base
    }

    private fun toSdkMessages(ui: List<ConversationMessage>): List<ChatMessage> = ui.mapNotNull { m ->
        when (m.author) {
            ConversationMessage.Author.ME -> if (m.text.isNotBlank()) ChatMessage(ChatMessage.Role.User, m.text) else null
            ConversationMessage.Author.BOT -> if (m.text.isNotBlank()) ChatMessage(ChatMessage.Role.Assistant, m.text) else null
        }
    }

    private fun List<ChatMessage>.withLanguageHint(languageHint: String?): List<ChatMessage> {
        if (languageHint == null) return this
        val assistantCount = count { it.role == ChatMessage.Role.Assistant }
        if (assistantCount == 0) return this
        val idx = indexOfLast { it.role == ChatMessage.Role.User }
        if (idx < 0) return this
        return toMutableList().also { list ->
            val original = list[idx]
            list[idx] = original.copy(content = "${original.content}\n\nReply in $languageHint.")
        }
    }

    private fun emitBot(text: String) {
        _conversation.update { it + ConversationMessage(text, ConversationMessage.Author.BOT) }
    }

    private fun updateLastBotMessage(text: String) {
        val msgs = _conversation.value
        if (msgs.isEmpty()) return
        val last = msgs.last()
        if (last.author == ConversationMessage.Author.BOT) {
            _conversation.update { it.dropLast(1) + last.copy(text = text, imagePng = null, imageFileName = null, imageRgba = null, imageWidth = null, imageHeight = null) }
        }
    }

    private fun updateLastBotImageRgba(rgbaBytes: ByteArray, width: Int, height: Int, fileName: String) {
        val msgs = _conversation.value
        if (msgs.isEmpty()) return
        val last = msgs.last()
        if (last.author == ConversationMessage.Author.BOT) {
            _conversation.update { it.dropLast(1) + last.copy(text = "", imagePng = null, imageFileName = fileName, imageRgba = rgbaBytes, imageWidth = width, imageHeight = height) }
        }
    }

    private fun normalizeModelPaths(models: List<LlamaModel>): List<LlamaModel> = models.map { m ->
        val path = pathResolver.resolve(m)
        if (!path.isNullOrBlank()) m.copy(localPath = path, fileName = path) else m
    }

    private fun normalizeVlmModelPaths(models: List<LlamaModel>): List<LlamaModel> = models.map { m ->
        val path = pathResolver.resolve(m)
        val mmprojPath = pathResolver.resolveMmproj(m)
        m.copy(
            localPath = if (!path.isNullOrBlank()) path else m.localPath,
            fileName = if (!path.isNullOrBlank()) path else m.fileName,
            mmprojLocalPath = if (!mmprojPath.isNullOrBlank()) mmprojPath else m.mmprojLocalPath,
        )
    }

    private fun downloadMmprojIfNeeded(vlmModel: LlamaModel, mmprojUrl: String, modelLocalPath: String) {
        if (downloadJobs[mmprojUrl]?.isActive == true) return

        val job = scope.launch(ioDispatcher) {
            updateDownload(mmprojUrl) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

            val mmprojModel = LlamaModel(name = "${vlmModel.name}_mmproj", url = mmprojUrl, sizeMb = vlmModel.mmprojSizeMb)
            modelDownloadOrchestrator.download(mmprojModel).collect { ev ->
                when (ev) {
                    is DownloadEvent.Progress -> updateDownload(mmprojUrl) { it.copy(inProgress = true, progress = ev.percent) }
                    is DownloadEvent.Completed -> {
                        updateDownload(mmprojUrl) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
                        val mmprojPath = pathResolver.persistDownloaded(mmprojModel, ev.localPath)
                        pathResolver.saveModelPath("${vlmModel.name}_mmproj", mmprojPath)

                        _state.update { s ->
                            s.copy(vlmModels = s.vlmModels.map { if (it.url == vlmModel.url) it.copy(mmprojLocalPath = mmprojPath) else it })
                        }

                        if (!_state.value.isVlmModelLoaded) {
                            val modelPath = pathResolver.resolve(vlmModel)
                            if (!modelPath.isNullOrBlank()) {
                                val loaded = MultimodalBridge.initModel(modelPath, mmprojPath)
                                if (loaded) {
                                    _state.update { it.copy(selectedVlmModelName = vlmModel.name, isVlmModelLoaded = true) }
                                    _events.trySend(AssistantEvent.VlmModelLoaded)
                                }
                            }
                        }
                    }
                    is DownloadEvent.Failed -> {
                        updateDownload(mmprojUrl) { it.copy(inProgress = false, done = false, error = ev.message) }
                        Logger.e { "Failed to download mmproj for ${vlmModel.name}: ${ev.message}" }
                    }
                }
            }
        }
        downloadJobs[mmprojUrl] = job
    }

    private fun detectAndEmitToolCall(final: String) {
        val rawJson = ToolCallParser.extractFirstJsonBlock(final)
        val call: ParsedToolCall? = rawJson?.let { ToolCallParser.parse(it) }
        if (call != null) {
            _events.trySend(AssistantEvent.ToolCallDetected(call.toolId, rawJson))
        }
    }

    private fun looksLikeBabble(s: String): Boolean {
        if (s.length < 60) return false
        val tail = s.takeLast(200)
        val collapsed = tail.replace("\\s+".toRegex(), " ").trim()
        if (collapsed.count { it == ',' } > 60) return true
        return Regex("""\b([A-Za-z0-9]{1,3})\b(?:[,\s]+\1\b){25,}""").containsMatchIn(collapsed)
    }

    private fun looksLikeEchoOrLoop(full: String, user: String, priorBotTexts: List<String> = emptyList()): Boolean {
        val f = full.trim()
        if (f.isEmpty()) return false
        if (f.indexOf(user, startIndex = minOf(80, f.length)) >= 0) return true

        val tail = f.takeLast(minOf(500, f.length))
        val phraseLen = minOf(80, tail.length)
        if (phraseLen >= 70) {
            val phrase = tail.takeLast(phraseLen)
            var count = 0; var pos = 0
            while (true) {
                val found = f.indexOf(phrase, pos)
                if (found < 0) break
                if (++count >= 3) return true
                pos = found + 1
            }
        }

        val sentences = tail.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length >= 60 }
        if (sentences.isNotEmpty()) {
            val last = sentences.last()
            if (f.indexOf(last) >= 0 && f.lastIndexOf(last) > f.indexOf(last)) return true
        }

        if (priorBotTexts.isNotEmpty() && f.length >= 200) {
            val blocks = f.split(Regex("(?<=[.!?\"])\\s+")).map { it.trim() }.filter { it.length >= 150 }
            for (block in blocks) {
                if (priorBotTexts.any { prior -> prior.contains(block) }) return true
            }
        }
        return false
    }

    private fun trimLoop(full: String, user: String, priorBotTexts: List<String> = emptyList()): String {
        val f = full.trim()
        val idxEcho = f.indexOf(user, startIndex = minOf(80, f.length))
        if (idxEcho >= 0) return f.substring(0, idxEcho).trim()

        if (priorBotTexts.isNotEmpty()) {
            val sentences = f.split(Regex("(?<=[.!?\"])\\s+")).map { it.trim() }
            val out = StringBuilder()
            for (s in sentences) {
                if (s.length >= 150 && priorBotTexts.any { prior -> prior.contains(s) }) break
                if (out.isNotEmpty()) out.append(' ')
                out.append(s)
            }
            if (out.isNotEmpty()) return out.toString().trim()
        }

        val sentences = f.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }
        val seen = HashSet<String>(); val out = StringBuilder()
        for (s in sentences) {
            val key = s.lowercase()
            if (key.length >= 60 && !seen.add(key)) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(s)
        }
        return if (out.isNotEmpty()) out.toString().trim() else f
    }

    private fun sanitizeForRag(s: String): String {
        val noQa = s.replace(Regex("(?mi)^\\s*(User|Question|Assistant|Answer)\\s*:\\s*.*$"), "")
        val lines = noQa.lines().filterNot { line ->
            val w = line.trim().split(Regex("\\s+")).size
            w in 2..8 && !line.contains('.') && line == line.split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun buildCompactContext(source: String, question: String, hardLimit: Int): String {
        val qTokens = question.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
        val sentences = source.replace("\\s+".toRegex(), " ").split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val hits = sentences.filter { s -> qTokens.count { t -> s.lowercase().contains(t) } >= 1 }
        val chosen = (hits.ifEmpty { sentences.take(6) }).joinToString(" ")
        return if (chosen.length <= hardLimit) chosen else chosen.take(hardLimit)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun persistConversationIfNeeded() {
        if (_state.value.isTemporaryChat) return
        val id = currentChatId ?: return

        val now = Clock.System.now().toEpochMilliseconds()
        val existing = chatHistoryRepository.getSession(id)
        val createdAt = existing?.createdAtEpochMs ?: now
        val title = existing?.title ?: buildTitle(
            _conversation.value.firstOrNull { it.author == ConversationMessage.Author.ME }?.text.orEmpty()
        )

        val session = ChatSession(
            id = id,
            title = title,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = now,
            messages = toPersistedMessages(_conversation.value),
        )
        chatHistoryRepository.upsert(session)
        refreshSessions()
    }

    private fun toPersistedMessages(messages: List<ConversationMessage>): List<PersistedChatMessage> =
        messages.filter { it.text.isNotBlank() }.map {
            PersistedChatMessage(
                text = it.text,
                author = if (it.author == ConversationMessage.Author.ME) PersistedAuthor.ME else PersistedAuthor.BOT,
            )
        }

    private fun buildTitle(firstUserMessage: String): String {
        val t = firstUserMessage.trim().replace("\n", " ")
        return if (t.length <= 40) t else t.take(40) + "…"
    }

    private suspend fun refreshSessions() {
        _state.update { it.copy(chatSessions = chatHistoryRepository.getSummaries()) }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun persistPdfRagStore(fileName: String, store: VectorStoreData, ragStorePath: String) {
        val persisted = PersistedRagStore(
            pdfFileName = fileName,
            createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            vectorStore = store,
        )
        val bytes = ragJson.encodeToString(PersistedRagStore.serializer(), persisted).encodeToByteArray()
        ragStorage.write(ragStorePath, bytes)
    }

    private suspend fun loadPersistedPdfRagStoreIfAny() {
        val bytes = ragStorage.read(PDF_RAG_STORE_PATH) ?: return
        val persisted = ragJson.decodeFromString(PersistedRagStore.serializer(), bytes.decodeToString())
        vectorStore = persisted.vectorStore
        _state.update { it.copy(ragPdfFileName = persisted.pdfFileName, isRagIndexing = false, ragIndexingProgress = 100, ragChunksCount = persisted.vectorStore.items.size) }
    }

    // endregion

    private val ragJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    companion object {
        const val PDF_RAG_STORE_PATH = "rag/pdf_rag_store.json"
    }
}

// region — List helpers

private fun List<LlamaModel>.updatePath(url: String, path: String): List<LlamaModel> =
    map { if (it.url == url) it.copy(fileName = path, localPath = path) else it }

private fun List<LlamaModel>.clearPath(url: String): List<LlamaModel> =
    map { if (it.url == url) it.copy(localPath = null, fileName = null) else it }

private fun List<LlamaModel>.clearPath(): List<LlamaModel> =
    map { it.copy(localPath = null, fileName = null) }

private fun List<LlamaModel>.clearVlmPath(url: String): List<LlamaModel> =
    map { if (it.url == url) it.copy(localPath = null, fileName = null, mmprojLocalPath = null) else it }

private fun List<LlamaModel>.clearVlmPath(): List<LlamaModel> =
    map { it.copy(localPath = null, fileName = null, mmprojLocalPath = null) }

// endregion
