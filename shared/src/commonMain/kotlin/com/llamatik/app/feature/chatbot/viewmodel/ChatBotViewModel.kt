package com.llamatik.app.feature.chatbot.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.ChatBotOnboardingScreen
import com.llamatik.app.feature.chatbot.download.DownloadEvent
import com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator
import com.llamatik.app.feature.chatbot.model.GenerateSettings
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.repositories.ChatHistoryRepository
import com.llamatik.app.feature.chatbot.repositories.ChatSession
import com.llamatik.app.feature.chatbot.repositories.ChatSessionSummary
import com.llamatik.app.feature.chatbot.repositories.PersistedAuthor
import com.llamatik.app.feature.chatbot.repositories.PersistedChatMessage
import com.llamatik.app.feature.chatbot.usecases.GetModelsUseCase
import com.llamatik.app.feature.chatbot.utils.ChatMessage
import com.llamatik.app.feature.chatbot.utils.ChatRunner
import com.llamatik.app.feature.chatbot.utils.Gemma3
import com.llamatik.app.feature.chatbot.utils.PersistedRagStore
import com.llamatik.app.feature.chatbot.utils.PromptTemplate
import com.llamatik.app.feature.chatbot.utils.VectorStoreData
import com.llamatik.app.feature.chatbot.utils.VectorStoreItem
import com.llamatik.app.feature.chatbot.utils.chunkText
import com.llamatik.app.feature.chatbot.utils.cosineD
import com.llamatik.app.feature.chatbot.utils.retrieveContext
import com.llamatik.app.feature.news.NewsFeedDetailScreen
import com.llamatik.app.feature.news.NewsFeedScreen
import com.llamatik.app.feature.news.repositories.FeedItem
import com.llamatik.app.feature.news.usecases.GetAllNewsUseCase
import com.llamatik.app.feature.reviews.ReviewRequestManager
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.AppDispatchersIO
import com.llamatik.app.platform.AppStorage
import com.llamatik.app.platform.LlamatikTempFile
import com.llamatik.app.platform.PlatformInfo
import com.llamatik.app.platform.extractPdfText
import com.llamatik.app.platform.migrateModelPathIfNeeded
import com.llamatik.app.platform.tts.TtsEngine
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.StableDiffusionBridge
import com.llamatik.library.platform.WhisperBridge
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

private const val PRIVACY_CHATBOT_VIEWED_KEY = "privacy_chatbot_viewed_key"
private const val DEFAULT_SYSTEM_PROMPT = """
You are Llamatik, a privacy-first local AI assistant running fully on-device.
Be clear, honest, and concise. Answer in the user's language.
"""

private const val PDF_RAG_STORE_PATH = "rag/pdf_rag_store.json"
enum class GenerationMode { TEXT, IMAGE }
const val COSINE_THRESHOLD = 0.15

class ChatBotViewModel(
    private var navigator: Navigator,
    private val settings: Settings,
    private val getAllNewsUseCase: GetAllNewsUseCase,
    private val getModelsUseCase: GetModelsUseCase,
    private val modelDownloadOrchestrator: ModelDownloadOrchestrator,
    private val reviewRequestManager: ReviewRequestManager,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val ttsEngine: TtsEngine,
) : ScreenModel {
    val localization = getCurrentLocalization()

    data class DownloadState(
        val inProgress: Boolean = false,
        val progress: Int = 0,
        val done: Boolean = false,
        val error: String? = null
    )

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private fun updateDownload(url: String, transform: (DownloadState) -> DownloadState) {
        val current = _downloadStates.value
        val existing = current[url] ?: DownloadState()
        _downloadStates.value = current.toMutableMap().apply { put(url, transform(existing)) }
    }

    private val _state = MutableStateFlow(
        ChatBotState(
            greeting = "",
            header = getCurrentLocalization().welcome,
            latestNews = emptyList(),
            embedModels = emptyList(),
            generateSettings = GenerateSettings()
        )
    )
    val state = _state.asStateFlow()

    private val _sideEffects = Channel<ChatBotSideEffects>()
    val sideEffects: Flow<ChatBotSideEffects> = _sideEffects.receiveAsFlow()

    private var vectorStore: VectorStoreData? = null

    private val _conversation = MutableStateFlow(emptyList<ChatUiModel.Message>())
    val conversation: StateFlow<List<ChatUiModel.Message>> get() = _conversation

    /** Guard to ignore late callbacks when a new request starts or is stopped */
    @Volatile
    private var activeRequestId: String? = null

    @Volatile
    private var started = false

    private val downloadJobs = mutableMapOf<String, Job>()

    private var currentChatId: String? = null

    /** Whether the user has already accepted the privacy/onboarding */
    private var hasAcceptedPrivacy: Boolean =
        settings.getBoolean(PRIVACY_CHATBOT_VIEWED_KEY, false)

    init {
        // If privacy not accepted yet → show onboarding first.
        if (!hasAcceptedPrivacy) {
            navigator.push(
                ChatBotOnboardingScreen { nav ->
                    onPrivacyAccepted(nav)
                }
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun getGreeting(): String {
        val currentTime = System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val localDateTime = currentTime.toLocalDateTime(timeZone)

        return when (localDateTime.hour) {
            in 6..11 -> getCurrentLocalization().greetingMorning
            in 12..17 -> getCurrentLocalization().greetingAfternoon
            in 18..21 -> getCurrentLocalization().greetingEvening
            else -> getCurrentLocalization().greetingNight
        }
    }

    /**
     * Key fix:
     * - Native: persist and load by absolute path.
     * - Web/WASM: persist and load by the IndexedDB key (fileName), NOT by absolutePath.
     *
     * For WASM we use the deterministic name derived from the model URL (same name used by your download layer).
     */
    private fun persistedPathForDownloadedModel(model: LlamaModel, downloadedLocalPath: String): String {
        return if (PlatformInfo.isWasm) {
            model.url.urlToFileName()
        } else {
            downloadedLocalPath
        }
    }

    private fun resolveAndMigratePath(model: LlamaModel): String? {
        val pathFromState = model.localPath
        val pathFromStorage = getModelsUseCase.getSavedModelPath(model.name).takeIf { it.isNotEmpty() }
        val rawPath = pathFromState ?: pathFromStorage
        if (rawPath.isNullOrBlank()) return null

        // On WASM there is nothing to "migrate" in a filesystem sense — we store by key.
        if (PlatformInfo.isWasm) return rawPath

        val migrated = migrateModelPathIfNeeded(
            modelNameOrFileName = model.name,
            savedPath = rawPath
        )

        if (migrated.isNotBlank() && migrated != rawPath) {
            runCatching { getModelsUseCase.saveModelPath(model.name, migrated) }
        }

        return migrated
    }

    fun onStarted(
        navigator: Navigator? = null,
        embedFilePath: String? = null,
        generatorFilePath: String? = null
    ) {
        navigator?.let { this.navigator = it }
        if (started) return
        started = true

        screenModelScope.launch(AppDispatchersIO) {
            embedFilePath?.let {
                LlamaBridge.initEmbedModel(embedFilePath)
                _state.value = _state.value.copy(isEmbedModelLoaded = true)
            }
            generatorFilePath?.let {
                LlamaBridge.initGenerateModel(generatorFilePath)
                _state.value = _state.value.copy(isGenerateModelLoaded = true)
            }

            getAllNewsUseCase.invoke()
                .onSuccess { _state.value = _state.value.copy(latestNews = it) }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            getModelsUseCase.getDefaultEmbedModels()
                .onSuccess { models ->
                    for (model in models) {
                        val path = resolveAndMigratePath(model) ?: continue
                        Logger.d("LlamaVM - Init Embed Model: ${model.name} at $path")
                        val loaded = LlamaBridge.initEmbedModel(path)
                        if (loaded) {
                            _state.value = _state.value.copy(
                                selectedEmbedModelName = model.name,
                                isEmbedModelLoaded = true
                            )
                            _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoaded)
                            break
                        } else {
                            Logger.e { "LlamaVM - failed to load embed model ${model.name}" }
                            _state.value = _state.value.copy(isEmbedModelLoaded = false)
                            _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoadError)
                        }
                    }

                    val normalized = models.map { m ->
                        val path = resolveAndMigratePath(m)
                        if (!path.isNullOrBlank()) m.copy(localPath = path, fileName = path) else m
                    }

                    _state.value = _state.value.copy(embedModels = normalized)
                }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            getModelsUseCase.getDefaultStableDiffusionModels()
                .onSuccess { _state.value = _state.value.copy(stableDiffusionModels = it) }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            // --- STT models list + attempt load any already-downloaded model ---
            getModelsUseCase.getDefaultSTTModels()
                .onSuccess { models ->
                    for (model in models) {
                        val path = resolveAndMigratePath(model) ?: continue
                        Logger.d("LlamaVM - Init STT Model: ${model.name} at $path")
                        val loaded = WhisperBridge.initModel(path)
                        if (loaded) {
                            _state.value = _state.value.copy(
                                selectedSttModelName = model.name,
                                isSttModelLoaded = true
                            )
                            _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoaded)
                            break
                        } else {
                            Logger.e { "LlamaVM - failed to load STT model ${model.name}" }
                            _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoadError)
                        }
                    }

                    val normalized = models.map { m ->
                        val path = resolveAndMigratePath(m)
                        if (!path.isNullOrBlank()) m.copy(localPath = path, fileName = path) else m
                    }

                    _state.value = _state.value.copy(sttModels = normalized)

                    if (hasAcceptedPrivacy) {
                        startSttInitialSetupIfNeeded(normalized)
                    }
                }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            val summaries = chatHistoryRepository.getSummaries()
            _state.value = _state.value.copy(chatSessions = summaries)

            getModelsUseCase.getDefaultGenerateModels()
                .onSuccess { models ->
                    for (model in models) {
                        val path = resolveAndMigratePath(model) ?: continue

                        Logger.d("LlamaVM - Init Generate Model: ${model.name} at $path")
                        val isLoaded = LlamaBridge.initGenerateModel(path)
                        if (isLoaded) {
                            _state.value =
                                _state.value.copy(
                                    selectedGenerateModelName = model.name,
                                    isGenerateModelLoaded = true
                                )
                            _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoaded)
                            notifyGenerateModelLoadedForReview()
                            break
                        } else {
                            Logger.e { "LlamaVM - failed to load generate model ${model.name}" }
                            _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
                        }
                    }

                    val normalized = models.map { m ->
                        val path = resolveAndMigratePath(m)
                        if (!path.isNullOrBlank()) m.copy(localPath = path, fileName = path) else m
                    }

                    _state.value = _state.value.copy(generateModels = normalized)

                    if (hasAcceptedPrivacy) {
                        startInitialSetupIfNeeded(normalized)
                    }
                }
                .onFailure { error ->
                    Logger.e(error.message ?: "Unknown error")
                }

            _state.value = _state.value.copy(
                greeting = getGreeting(),
                header = getCurrentLocalization().welcome,
                latestNews = _state.value.latestNews,
            )

            runCatching { loadPersistedPdfRagStoreIfAny() }
                .onFailure { Logger.w(it) { "RAG - failed to load persisted PDF store" } }
        }

        onGenerateSettingsApplied(_state.value.generateSettings)
        _sideEffects.trySend(ChatBotSideEffects.OnLoaded)
    }

    private suspend fun startInitialSetupIfNeeded(models: List<LlamaModel>) {
        if (models.isEmpty()) return

        val hasLocal = models.any { model ->
            val resolved = resolveAndMigratePath(model)
            !resolved.isNullOrEmpty()
        }
        if (hasLocal) return

        val defaultModel = models.firstOrNull {
            it.name.contains("gemma 3", ignoreCase = true) ||
                    it.name.contains("gemma3", ignoreCase = true)
        } ?: models.first()

        val url = defaultModel.url
        Logger.d("LlamaVM - initial setup: downloading default generate model ${defaultModel.name}")

        _state.value = _state.value.copy(
            isInitialSetup = true,
            initialSetupModelName = defaultModel.name,
            initialSetupProgress = 0
        )

        updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

        getModelsUseCase.downloadModel(url) { bytes, totalBytes ->
            val progress = if (totalBytes > 0) {
                ((bytes.toFloat() / totalBytes.toFloat()) * 100f).toInt()
            } else 0
            updateDownload(url) { it.copy(inProgress = true, progress = progress) }
            _state.value = _state.value.copy(initialSetupProgress = progress)
        }.onSuccess { tempFile ->
            Logger.d("LlamaVM - initial setup download finished for ${defaultModel.name}")

            val downloadedPath = tempFile.absolutePath()
            val persistedPath = persistedPathForDownloadedModel(defaultModel, downloadedPath)

            getModelsUseCase.saveModelPath(defaultModel.name, persistedPath)

            _state.value = _state.value.copy(
                generateModels = _state.value.generateModels.map {
                    if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                }
            )

            val loaded = LlamaBridge.initGenerateModel(persistedPath)
            if (loaded) {
                _state.value = _state.value.copy(
                    selectedGenerateModelName = defaultModel.name,
                    isGenerateModelLoaded = true,
                    isInitialSetup = false,
                    initialSetupProgress = 100
                )
                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoaded)
                notifyGenerateModelLoadedForReview()
            } else {
                _state.value = _state.value.copy(isInitialSetup = false)
                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
            }

            updateDownload(url) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
        }.onFailure { error ->
            Logger.e(error.message ?: "Unknown error") {
                "LlamaVM - initial setup download failed for ${defaultModel.name}"
            }
            _state.value = _state.value.copy(isInitialSetup = false)
            updateDownload(url) { it.copy(inProgress = false, error = error.message, done = false) }
        }
    }

    private suspend fun startSttInitialSetupIfNeeded(models: List<LlamaModel>) {
        if (models.isEmpty()) return

        val hasLocal = models.any { model ->
            val resolved = resolveAndMigratePath(model)
            !resolved.isNullOrEmpty()
        }
        if (hasLocal) return

        val defaultModel = models.firstOrNull {
            it.name.contains("whisper", ignoreCase = true) ||
                    it.name.contains("tiny", ignoreCase = true)
        } ?: models.first()

        val url = defaultModel.url
        Logger.d("LlamaVM - initial setup: downloading default STT model ${defaultModel.name}")

        _state.value = _state.value.copy(
            isInitialSetup = true,
            initialSetupModelName = defaultModel.name,
            initialSetupProgress = 0
        )

        updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

        getModelsUseCase.downloadModel(url) { bytes, totalBytes ->
            val progress = if (totalBytes > 0) {
                ((bytes.toFloat() / totalBytes.toFloat()) * 100f).toInt()
            } else 0
            updateDownload(url) { it.copy(inProgress = true, progress = progress) }
            _state.value = _state.value.copy(initialSetupProgress = progress)
        }.onSuccess { tempFile ->
            Logger.d("LlamaVM - initial setup download finished for STT ${defaultModel.name}")

            val downloadedPath = tempFile.absolutePath()
            val persistedPath = persistedPathForDownloadedModel(defaultModel, downloadedPath)

            getModelsUseCase.saveModelPath(defaultModel.name, persistedPath)

            _state.value = _state.value.copy(
                sttModels = _state.value.sttModels.map {
                    if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                }
            )

            val loaded = WhisperBridge.initModel(persistedPath)
            if (loaded) {
                _state.value = _state.value.copy(
                    selectedSttModelName = defaultModel.name,
                    isSttModelLoaded = true,
                    isInitialSetup = false,
                    initialSetupProgress = 100
                )
                _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoaded)
            } else {
                _state.value = _state.value.copy(isInitialSetup = false)
                _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoadError)
            }

            updateDownload(url) { it.copy(inProgress = false, progress = 100, done = true, error = null) }
        }.onFailure { error ->
            Logger.e(error.message ?: "Unknown error") {
                "LlamaVM - initial setup STT download failed for ${defaultModel.name}"
            }
            _state.value = _state.value.copy(isInitialSetup = false)
            updateDownload(url) { it.copy(inProgress = false, error = error.message, done = false) }
        }
    }

    fun onGenerateSettingsApplied(settings: GenerateSettings) {
        _state.value = _state.value.copy(generateSettings = settings)
        LlamaBridge.updateGenerateParams(
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            topP = settings.topP,
            topK = settings.topK,
            repeatPenalty = settings.repeatPenalty
        )
    }

    fun onEmbedModelSelected(model: LlamaModel) {
        screenModelScope.launch(AppDispatchersIO) {
            val path = resolveAndMigratePath(model)

            if (!path.isNullOrEmpty()) {
                Logger.d("LlamaVM - initEmbedModel $path")
                val isLoaded = LlamaBridge.initEmbedModel(path)
                if (isLoaded) {
                    _state.value = _state.value.copy(
                        selectedEmbedModelName = model.name,
                        isEmbedModelLoaded = true
                    )
                    _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoaded)
                } else {
                    _state.value = _state.value.copy(isEmbedModelLoaded = false)
                    _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoadError)
                }
            } else {
                Logger.e { "LlamaVM - no local path for embed model ${model.name}" }
                _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoadError)
            }
        }
    }

    fun onGenerateModelSelected(model: LlamaModel) {
        screenModelScope.launch(AppDispatchersIO) {
            val path = resolveAndMigratePath(model)

            if (!path.isNullOrEmpty()) {
                Logger.d("LlamaVM - initGenerateModel $path")
                val isLoaded = LlamaBridge.initGenerateModel(path)
                if (isLoaded) {
                    _state.value = _state.value.copy(selectedGenerateModelName = model.name)
                    _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoaded)
                    notifyGenerateModelLoadedForReview()
                } else {
                    Logger.e { "LlamaVM - failed to load generate model ${model.name}" }
                    _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
                }
            } else {
                Logger.e { "LlamaVM - no local path for generate model ${model.name}" }
                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
            }
        }
    }

    fun onSttModelSelected(model: LlamaModel) {
        screenModelScope.launch(AppDispatchersIO) {
            val path = resolveAndMigratePath(model)
            if (!path.isNullOrEmpty()) {
                Logger.d("LlamaVM - initSttModel $path")
                val isLoaded = WhisperBridge.initModel(path)
                if (isLoaded) {
                    _state.value = _state.value.copy(
                        selectedSttModelName = model.name,
                        isSttModelLoaded = true
                    )
                    _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoaded)
                } else {
                    _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoadError)
                }
            } else {
                Logger.e { "LlamaVM - no local path for STT model ${model.name}" }
                _sideEffects.trySend(ChatBotSideEffects.OnSttModelLoadError)
            }
        }
    }

    fun setGenerationMode(mode: GenerationMode) {
        _state.value = _state.value.copy(generationMode = mode)
    }

    fun onStableDiffusionModelSelected(model: LlamaModel) {
        screenModelScope.launch(AppDispatchersIO) {
            val path = resolveAndMigratePath(model) ?: return@launch

            Logger.d("StableDiffusion - selecting model ${model.name} at $path")
            val loaded = StableDiffusionBridge.initModel(path)
            if (loaded) {
                _state.value = _state.value.copy(
                    selectedStableDiffusionModelName = model.name,
                    isStableDiffusionModelLoaded = true
                )
                _sideEffects.trySend(ChatBotSideEffects.OnStableDiffusionModelLoaded)
            } else {
                _sideEffects.trySend(ChatBotSideEffects.OnStableDiffusionModelLoadError)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun onImagePromptSendDirect(prompt: String) {
        val input = prompt.trim()
        if (input.isBlank()) return

        screenModelScope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) {
                currentChatId = kotlin.random.Random.nextLong().toString()
            }

            _conversation.value += ChatUiModel.Message(input, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
            _state.value = _state.value.copy(isGenerating = true)

            withContext(AppDispatchersIO) {
                try {
                    persistCurrentConversationIfNeeded()

                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    if (!_state.value.isStableDiffusionModelLoaded) {
                        updateLastBotMessage(localization.imageModeEnabledButNoModelLoadedError)
                        _state.value = _state.value.copy(isGenerating = false)
                        _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                        return@withContext
                    }

                    val rgbaBytes = StableDiffusionBridge.txt2img(
                        prompt = input,
                        negativePrompt = "",
                        width = 512,
                        height = 512,
                        steps = 20,
                        seed = -1,
                    )

                    if (rgbaBytes.isEmpty()) {
                        updateLastBotMessage(localization.imageGenerationFailedError)
                    } else {
                        val fileName = "sd_${Random.nextInt()}_${System.now().toString().replace(":", "_")}.png"
                        updateLastBotImageRgba(
                            rgbaBytes = rgbaBytes,
                            width = 512,
                            height = 512,
                            fileName = fileName,
                        )
                    }

                    persistCurrentConversationIfNeeded()
                } catch (t: Throwable) {
                    Logger.e(t.message ?: localization.imageGenerationError)
                    updateLastBotMessage("🖼️ Error: ${t.message ?: "unknown"}")
                } finally {
                    _state.value = _state.value.copy(isGenerating = false)
                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                    _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                }
            }
        }
    }

    private fun updateLastBotMessage(text: String) {
        val messages = _conversation.value
        if (messages.isEmpty()) return
        val lastIndex = messages.lastIndex
        val last = messages[lastIndex]
        if (last.author == ChatUiModel.Author.bot) {
            _conversation.value = messages.toMutableList().apply {
                this[lastIndex] = last.copy(
                    text = text,
                    imagePng = null,
                    imageFileName = null,
                    imageRgba = null,
                    imageWidth = null,
                    imageHeight = null,
                )
            }
        }
    }

    private fun updateLastBotImageRgba(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        fileName: String,
    ) {
        val messages = _conversation.value
        if (messages.isEmpty()) return
        val lastIndex = messages.lastIndex
        val last = messages[lastIndex]
        if (last.author == ChatUiModel.Author.bot) {
            _conversation.value = messages.toMutableList().apply {
                this[lastIndex] = last.copy(
                    text = "",
                    imagePng = null,
                    imageFileName = fileName,
                    imageRgba = rgbaBytes,
                    imageWidth = width,
                    imageHeight = height,
                )
            }
        }
    }

    private fun updateLastBotImage(pngBytes: ByteArray, fileName: String) {
        val messages = _conversation.value
        if (messages.isEmpty()) return
        val lastIndex = messages.lastIndex
        val last = messages[lastIndex]
        if (last.author == ChatUiModel.Author.bot) {
            _conversation.value = messages.toMutableList().apply {
                this[lastIndex] = last.copy(
                    text = "",
                    imagePng = pngBytes,
                    imageFileName = fileName,
                )
            }
        }
    }

    fun onDownloadModel(model: LlamaModel) {
        val url = model.url
        val existingJob = downloadJobs[url]
        if (existingJob?.isActive == true) return

        val job = screenModelScope.launch(AppDispatchersIO) {
            updateDownload(url) { it.copy(inProgress = true, progress = 0, done = false, error = null) }

            modelDownloadOrchestrator.download(model).collect { ev ->
                when (ev) {
                    is DownloadEvent.Progress -> {
                        updateDownload(url) { it.copy(inProgress = true, progress = ev.percent) }
                    }

                    is DownloadEvent.Completed -> {
                        updateDownload(url) {
                            it.copy(inProgress = false, progress = 100, done = true, error = null)
                        }

                        val persistedPath = persistedPathForDownloadedModel(model, ev.localPath)
                        getModelsUseCase.saveModelPath(model.name, persistedPath)

                        _state.value = _state.value.copy(
                            embedModels = _state.value.embedModels.map {
                                if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                            },
                            generateModels = _state.value.generateModels.map {
                                if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                            },
                            sttModels = _state.value.sttModels.map {
                                if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                            },
                            stableDiffusionModels = _state.value.stableDiffusionModels.map {
                                if (it.url == url) it.copy(fileName = persistedPath, localPath = persistedPath) else it
                            },
                        )
                    }

                    is DownloadEvent.Failed -> {
                        updateDownload(url) {
                            it.copy(inProgress = false, done = false, error = ev.message)
                        }
                    }
                }
            }
        }

        downloadJobs[url] = job
    }

    fun onCancelDownload(model: LlamaModel) {
        val url = model.url
        downloadJobs[url]?.cancel()
        downloadJobs.remove(url)

        modelDownloadOrchestrator.cancel(model)

        updateDownload(url) {
            it.copy(inProgress = false, done = false, progress = 0, error = "Cancelled")
        }
    }

    fun onDeleteModel(model: LlamaModel) {
        screenModelScope.launch(AppDispatchersIO) {
            try {
                Logger.d("LlamaVM - deleting model ${model.name}")

                val path = resolveAndMigratePath(model)

                if (!path.isNullOrEmpty()) {
                    Logger.d("LlamaVM - delete model file at $path")
                    try {
                        LlamatikTempFile(model.name).delete(path)
                    } catch (e: Throwable) {
                        Logger.e(e) { "LlamaVM - failed to delete file at $path" }
                    }
                }

                getModelsUseCase.deleteModelPath(model)
                _state.value = _state.value.copy(
                    embedModels = _state.value.embedModels.map {
                        if (it.url == model.url) it.copy(fileName = null, localPath = null) else it
                    },
                    generateModels = _state.value.generateModels.map {
                        if (it.url == model.url) it.copy(fileName = null, localPath = null) else it
                    },
                    sttModels = _state.value.sttModels.map {
                        if (it.url == model.url) it.copy(fileName = null, localPath = null) else it
                    },
                    selectedSttModelName = if (_state.value.selectedSttModelName == model.name) null else _state.value.selectedSttModelName,
                    isSttModelLoaded = if (_state.value.selectedSttModelName == model.name) false else _state.value.isSttModelLoaded,
                    selectedStableDiffusionModelName = if (_state.value.selectedStableDiffusionModelName == model.name) null else _state.value.selectedStableDiffusionModelName,
                    isStableDiffusionModelLoaded = if (_state.value.selectedStableDiffusionModelName == model.name) false else _state.value.isStableDiffusionModelLoaded
                )
            } catch (t: Throwable) {
                Logger.e(t) { "LlamaVM - error deleting model ${model.name}" }
            }
        }
    }

    fun onSpeak(text: String) {
        if (!ttsEngine.isAvailable) return
        screenModelScope.launch {
            runCatching {
                ttsEngine.speak(text, interrupt = true)
            }.onFailure {
                Logger.withTag("TTS").w(it) { "TTS speak failed" }
            }
        }
    }

    /**
     * Remove all downloaded model files, clear saved model paths, and delete persisted PDF RAG store.
     * Updates view state accordingly.
     */
    fun onClearAllCachedModels() {
        screenModelScope.launch(AppDispatchersIO) {
            try {
                val allModels = (_state.value.generateModels +
                        _state.value.embedModels +
                        _state.value.sttModels +
                        _state.value.stableDiffusionModels)
                    .distinctBy { it.url }

                for (model in allModels) {
                    try {
                        val path = resolveAndMigratePath(model)
                        if (!path.isNullOrBlank()) {
                            runCatching { LlamatikTempFile(model.name).delete(path) }
                        }
                    } catch (t: Throwable) {
                        Logger.e(t) { "Failed deleting model file for ${model.name}" }
                    }
                    runCatching { getModelsUseCase.deleteModelPath(model) }
                }

                // Delete persisted RAG store
                runCatching { AppStorage.delete(PDF_RAG_STORE_PATH) }

                vectorStore = null

                _state.update { s ->
                    s.copy(
                        generateModels = s.generateModels.map { it.copy(localPath = null, fileName = null) },
                        embedModels = s.embedModels.map { it.copy(localPath = null, fileName = null) },
                        sttModels = s.sttModels.map { it.copy(localPath = null, fileName = null) },
                        stableDiffusionModels = s.stableDiffusionModels.map { it.copy(localPath = null, fileName = null) },
                        selectedEmbedModelName = null,
                        selectedGenerateModelName = null,
                        selectedSttModelName = null,
                        selectedStableDiffusionModelName = null,
                        isEmbedModelLoaded = false,
                        isGenerateModelLoaded = false,
                        isSttModelLoaded = false,
                        isStableDiffusionModelLoaded = false,
                        ragPdfFileName = null,
                        isRagIndexing = false,
                        ragIndexingProgress = 0,
                        ragChunksCount = 0
                    )
                }

                _sideEffects.trySend(
                    ChatBotSideEffects.OnCacheCleared(localization.allCachedModelsRemoved)
                )

            } catch (t: Throwable) {
                Logger.e(t) { "Failed to clear cached models / RAG" }

                _sideEffects.trySend(
                    ChatBotSideEffects.OnCacheClearFailed(
                        t.message ?: "Unknown error while clearing cache."
                    )
                )
            }
        }
    }

    fun onStopSpeaking() {
        runCatching { ttsEngine.stop() }
    }

    fun String.urlToFileName(): String {
        val filename = this.substring(this.lastIndexOf("/") + 1).removeExtension()
        return UrlEncoderUtil.decode(filename)
    }

    fun String.removeExtension(): String {
        val lastIndex = this.lastIndexOf('.')
        if (lastIndex != -1) {
            return this.substring(0, lastIndex)
        }
        return this
    }

    override fun onDispose() {
        activeRequestId = null
        _state.value = _state.value.copy(isGenerating = false)
        LlamaBridge.shutdown()
    }

    private fun sanitizeForRag(s: String): String {
        val noQa = s.replace(Regex("(?mi)^\\s*(User|Question|Assistant|Answer)\\s*:\\s*.*$"), "")
        val lines = noQa.lines().filterNot { line ->
            val w = line.trim().split(Regex("\\s+")).size
            w in 2..8 && !line.contains('.') && line == line.split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private suspend fun loadPersistedPdfRagStoreIfAny() {
        val bytes = AppStorage.readBytes(PDF_RAG_STORE_PATH) ?: return
        val json = Json { ignoreUnknownKeys = true }
        val persisted = json.decodeFromString(PersistedRagStore.serializer(), bytes.decodeToString())

        vectorStore = persisted.vectorStore
        _state.value = _state.value.copy(
            ragPdfFileName = persisted.pdfFileName,
            isRagIndexing = false,
            ragIndexingProgress = 100,
            ragChunksCount = persisted.vectorStore.items.size,
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun persistPdfRagStore(fileName: String, store: VectorStoreData) {
        val json = Json { encodeDefaults = true }
        val persisted = PersistedRagStore(
            pdfFileName = fileName,
            createdAtEpochMs = System.now().toEpochMilliseconds(),
            vectorStore = store,
        )
        val bytes = json.encodeToString(PersistedRagStore.serializer(), persisted).encodeToByteArray()
        AppStorage.writeBytes(PDF_RAG_STORE_PATH, bytes)
    }

    fun onPickPdfForRag() {
        screenModelScope.launch {
            try {
                val file = FileKit.openFilePicker() ?: return@launch

                val fileName = file.name
                if (!fileName.lowercase().endsWith(".pdf")) {
                    emitBot(localization.pdfSelectFile)
                    return@launch
                }

                _state.update {
                    it.copy(
                        ragPdfFileName = fileName,
                        isRagIndexing = true,
                        ragIndexingProgress = 0,
                        ragChunksCount = 0
                    )
                }

                val pdfBytes = file.readBytes()
                val text = extractPdfText(pdfBytes).trim()
                if (text.isBlank()) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(localization.pdfExtractionError)
                    return@launch
                }

                if (!_state.value.isEmbedModelLoaded) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(localization.pdfEmbedModelNeededWarning)
                    return@launch
                }

                val chunks = chunkText(text, chunkSize = 1000, chunkOverlap = 200)
                    .filter { it.isNotBlank() }

                val capped = chunks.take(2_000)
                if (capped.isEmpty()) {
                    _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                    emitBot(localization.pdfNoUsableChunksError)
                    return@launch
                }

                val items = ArrayList<VectorStoreItem>(capped.size)
                val total = capped.size

                for ((index, chunk) in capped.withIndex()) {
                    val vec = LlamaBridge.embed(chunk)

                    if (vec.isEmpty()) {
                        Logger.e { "RAG - embedding failed for chunk $index (empty vector); aborting PDF indexing" }
                        _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                        emitBot(localization.pdfFailedToComputeEmbeddingsError)
                        return@launch
                    }

                    items += VectorStoreItem(
                        id = "${fileName}_${index}",
                        text = chunk,
                        vector = vec.toList(),
                        metadata = mapOf(
                            "source" to JsonPrimitive("pdf"),
                            "fileName" to JsonPrimitive(fileName),
                            "chunkIndex" to JsonPrimitive(index)
                        )
                    )

                    val progress = (((index + 1) * 100.0) / total.toDouble()).toInt().coerceIn(0, 100)
                    if (index % 10 == 0 || index == total - 1) {
                        _state.update { it.copy(ragIndexingProgress = progress) }
                    }
                }

                val store = VectorStoreData(items)

                vectorStore = store
                persistPdfRagStore(
                    fileName = fileName,
                    store = store
                )

                _state.update {
                    it.copy(
                        isRagIndexing = false,
                        ragIndexingProgress = 100,
                        ragChunksCount = items.size,
                        ragPdfFileName = fileName
                    )
                }

                emitBot("${localization.pdfIndexedForRAG}: $fileName (${items.size} chunks)")

            } catch (t: Throwable) {
                t.printStackTrace()
                _state.update { it.copy(isRagIndexing = false, ragIndexingProgress = 0) }
                emitBot("${localization.pdfFailedToLoadPDFForRAG}: ${t.message ?: "unknown error"}")
            }
        }
    }

    fun onMessageSendWithEmbed(message: String) {
        val question = message.trim()
        if (question.isBlank()) return

        screenModelScope.launch {
            _conversation.value += ChatUiModel.Message(question, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

            withContext(AppDispatchersIO) {
                try {
                    val qArr = LlamaBridge.embed(question)
                    if (qArr.isEmpty()) {
                        emitBot(localization.failedToComputeEmbeddings)
                        return@withContext
                    }

                    val store = vectorStore
                        ?: return@withContext emitBot(localization.thereIsAProblemWithAI)

                    val qVec = qArr.toList()

                    val topItems = retrieveContext(
                        queryVector = qVec,
                        questionText = question,
                        vectorStore = store,
                        poolSize = 80,
                        topContext = 4
                    )

                    if (topItems.isEmpty()) {
                        emitBot(localization.iDontHaveEnoughInfoInSources)
                        _sideEffects.trySend(ChatBotSideEffects.OnNoResults)
                        _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                        return@withContext
                    }

                    val bestCosine = topItems.maxOf { item -> cosineD(qVec, item.vector) }

                    Logger.d("RAG - best cosine=$bestCosine topItems=${topItems.size}")

                    if (bestCosine < COSINE_THRESHOLD) {
                        emitBot(localization.iDontHaveEnoughInfoInSources)
                        _sideEffects.trySend(ChatBotSideEffects.OnNoResults)
                        _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                        return@withContext
                    }

                    val rawContext = topItems.joinToString("\n\n") { sanitizeForRag(it.text) }
                    val compact = buildCompactContext(rawContext, question, hardLimit = 1600)

                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    val chatHistory: List<ChatMessage> =
                        toChatMessages(_conversation.value.dropLast(1))

                    val requestId = kotlin.random.Random.nextLong().toString()
                    activeRequestId = requestId

                    val acc = StringBuilder()
                    val generateSettings = _state.value.generateSettings

                    ChatRunner.stream(
                        system = currentSystemPrompt(),
                        contexts = listOf(compact),
                        messages = chatHistory,
                        template = currentGenerateTemplate(),
                        maxTokens = generateSettings.maxTokens,
                        onDelta = { chunk ->
                            if (activeRequestId != requestId) return@stream
                            if (chunk.isEmpty()) return@stream

                            acc.append(chunk)
                            _conversation.value = _conversation.value.dropLast(1) +
                                    ChatUiModel.Message(acc.toString(), ChatUiModel.Author.bot)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

                            if (looksLikeEchoOrLoop(full = acc.toString(), user = question)) {
                                val trimmed = trimLoop(acc.toString(), user = question)
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(trimmed, ChatUiModel.Author.bot)
                                activeRequestId = null
                                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                                notifyChatCompletedForReview()
                            }
                        },
                        onComplete = { final ->
                            if (activeRequestId != requestId) return@stream
                            _conversation.value = _conversation.value.dropLast(1) +
                                    ChatUiModel.Message(final, ChatUiModel.Author.bot)
                            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                            notifyChatCompletedForReview()
                        },
                        onError = { err ->
                            if (activeRequestId != requestId) return@stream
                            _conversation.value = _conversation.value.dropLast(1) +
                                    ChatUiModel.Message(
                                        "${localization.thereIsAProblemWithAI}: $err",
                                        ChatUiModel.Author.bot
                                    )
                            _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                        }
                    )
                } catch (t: Throwable) {
                    t.printStackTrace()
                    emitBot(localization.thereIsAProblemWithAI)
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                    _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                }
            }
        }
    }

    fun onMessageSendDirect(message: String) {
        val input = message.trim()
        if (input.isBlank()) return

        screenModelScope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) {
                currentChatId = kotlin.random.Random.nextLong().toString()
            }

            _conversation.value += ChatUiModel.Message(input, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
            _state.value = _state.value.copy(isGenerating = true)

            withContext(AppDispatchersIO) {
                try {
                    persistCurrentConversationIfNeeded()

                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    val chatHistory: List<ChatMessage> =
                        toChatMessages(_conversation.value.dropLast(1))

                    val requestId = kotlin.random.Random.nextLong().toString()
                    activeRequestId = requestId
                    val acc = StringBuilder()
                    var completed = false

                    fun looksLikeBabble(s: String): Boolean {
                        if (s.length < 60) return false
                        val tail = s.takeLast(200)
                        val collapsed = tail.replace("\\s+".toRegex(), " ").trim()
                        val commas = collapsed.count { it == ',' }
                        if (commas > 60) return true
                        val m =
                            Regex("""\b([A-Za-z0-9]{1,3})\b(?:[,\s]+\1\b){25,}""").find(collapsed)
                        return m != null
                    }

                    val generateSettings = _state.value.generateSettings

                    try {
                        ChatRunner.stream(
                            system = currentSystemPrompt(),
                            contexts = emptyList(),
                            messages = chatHistory,
                            template = currentGenerateTemplate(),
                            maxTokens = generateSettings.maxTokens,
                            onDelta = { chunk ->
                                if (activeRequestId != requestId || completed) return@stream
                                if (chunk.isEmpty()) return@stream

                                acc.append(chunk)
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(acc.toString(), ChatUiModel.Author.bot)
                                _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

                                if (looksLikeEchoOrLoop(full = acc.toString(), user = input)) {
                                    val trimmed = trimLoop(acc.toString(), user = input)
                                    _conversation.value = _conversation.value.dropLast(1) +
                                            ChatUiModel.Message(trimmed, ChatUiModel.Author.bot)
                                    completed = true
                                    activeRequestId = null
                                    _state.value = _state.value.copy(isGenerating = false)
                                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                    notifyChatCompletedForReview()
                                    return@stream
                                }

                                if (looksLikeBabble(acc.toString())) {
                                    completed = true
                                    activeRequestId = null
                                    val cleaned = acc.toString().trim().trimEnd(',', ' ', '\n')
                                    _conversation.value = _conversation.value.dropLast(1) +
                                            ChatUiModel.Message(cleaned, ChatUiModel.Author.bot)
                                    _state.value = _state.value.copy(isGenerating = false)
                                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                    notifyChatCompletedForReview()
                                }
                            },
                            onComplete = { final ->
                                if (activeRequestId != requestId || completed) return@stream
                                completed = true
                                activeRequestId = null
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(final, ChatUiModel.Author.bot)
                                _state.value = _state.value.copy(isGenerating = false)
                                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                notifyChatCompletedForReview()
                            },
                            onError = { err ->
                                if (activeRequestId != requestId) return@stream
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(
                                            "${localization.thereIsAProblemWithAI}: $err",
                                            ChatUiModel.Author.bot
                                        )
                                activeRequestId = null
                                _state.value = _state.value.copy(isGenerating = false)
                                _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                            }
                        )
                    } finally {
                        persistCurrentConversationIfNeeded()
                        if (activeRequestId == null) {
                            _state.value = _state.value.copy(isGenerating = false)
                        }
                    }
                } catch (t: Throwable) {
                    emitBot("${localization.thereIsAProblemWithAI}: ${t.message ?: "Unknown error"}")
                    activeRequestId = null
                    _state.value = _state.value.copy(isGenerating = false)
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                }
            }
        }
    }

    fun stopGeneration() {
        Logger.d { "LlamaVM - stopGeneration()" }
        LlamaBridge.nativeCancelGenerate()
        activeRequestId = null
        _state.value = _state.value.copy(isGenerating = false)
        val messages = _conversation.value
        if (messages.isNotEmpty()) {
            val last = messages.last()
            if (last.author == ChatUiModel.Author.bot && last.text.isBlank()) {
                _conversation.value = messages.dropLast(1)
            }
        }
        _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
        _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
        notifyChatCompletedForReview()
    }

    private fun emitBot(text: String) {
        _conversation.value += ChatUiModel.Message(text, ChatUiModel.Author.bot)
    }

    private fun buildCompactContext(source: String, question: String, hardLimit: Int): String {
        val qTokens = question.lowercase()
            .split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()

        val sentences = source.replace("\\s+".toRegex(), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hits = sentences.filter { s ->
            val lower = s.lowercase()
            qTokens.count { t -> lower.contains(t) } >= 1
        }

        val chosen = (hits.ifEmpty { sentences.take(6) }).joinToString(" ")
        val clipped = if (chosen.length <= hardLimit) chosen else chosen.take(hardLimit)
        return clipped
    }

    private fun isLikelyRelevant(context: String, question: String): Boolean {
        val qTokens = question.lowercase()
            .split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }.toSet()
        val ctx = context.lowercase()
        val hits = qTokens.count { ctx.contains(it) }
        Logger.d("LlamaVM - relevance hits=$hits tokens=${qTokens.size}")
        return hits >= 2
    }

    fun onClearConversation() {
        stopGeneration()
        currentChatId = null
        screenModelScope.launch { _conversation.emit(emptyList()) }
    }

    fun onShowPrivacyScreen() {
        navigator.push(
            ChatBotOnboardingScreen { nav ->
                onPrivacyAccepted(nav)
            }
        )
    }

    fun onOpenFeedItemDetail(link: String) {
        navigator.push(NewsFeedDetailScreen(link))
    }

    fun onOpenNewsClicked() {
        navigator.push(NewsFeedScreen())
    }

    fun onToggleTemporaryChat() {
        stopGeneration()
        currentChatId = null
        _conversation.value = emptyList()
        _state.value = _state.value.copy(isTemporaryChat = !_state.value.isTemporaryChat)
    }

    fun onLoadChatSession(chatId: String) {
        screenModelScope.launch(AppDispatchersIO) {
            val session = chatHistoryRepository.getSession(chatId) ?: return@launch
            stopGeneration()
            currentChatId = chatId

            val restored = session.messages.map {
                ChatUiModel.Message(
                    text = it.text,
                    author = if (it.author == PersistedAuthor.ME) ChatUiModel.Author.me else ChatUiModel.Author.bot
                )
            }

            _conversation.value = restored
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
        }
    }

    fun onDeleteChatSession(chatId: String) {
        screenModelScope.launch(AppDispatchersIO) {
            chatHistoryRepository.delete(chatId)
            if (currentChatId == chatId) {
                currentChatId = null
                _conversation.value = emptyList()
            }
            refreshSessions()
        }
    }

    private fun onPrivacyAccepted(currentNavigator: Navigator) {
        navigator = currentNavigator

        settings.putBoolean(PRIVACY_CHATBOT_VIEWED_KEY, true)
        hasAcceptedPrivacy = true

        currentNavigator.pop()

        screenModelScope.launch(AppDispatchersIO) {
            val genModels = _state.value.generateModels.ifEmpty {
                getModelsUseCase.getDefaultGenerateModels().getOrElse { emptyList() }
            }
            startInitialSetupIfNeeded(genModels)

            val sttModels = _state.value.sttModels.ifEmpty {
                getModelsUseCase.getDefaultSTTModels().getOrElse { emptyList() }
            }
            startSttInitialSetupIfNeeded(sttModels)
        }
    }

    private fun toChatMessages(ui: List<ChatUiModel.Message>): List<ChatMessage> {
        return ui.mapNotNull { m ->
            when (m.author) {
                ChatUiModel.Author.me -> ChatMessage(ChatMessage.Role.User, m.text)
                ChatUiModel.Author.bot -> ChatMessage(ChatMessage.Role.Assistant, m.text)
                else -> null
            }
        }
    }

    private fun currentGenerateTemplate(): PromptTemplate {
        val state = _state.value
        val selectedName = state.selectedGenerateModelName

        val modelTemplate = state.generateModels
            .firstOrNull { it.name == selectedName }
            ?.template

        return modelTemplate ?: Gemma3
    }

    private fun currentGenerateModel(): LlamaModel? {
        val state = _state.value
        return state.generateModels.firstOrNull { it.name == state.selectedGenerateModelName }
    }

    private fun currentSystemPrompt(): String {
        return currentGenerateModel()?.systemPrompt ?: DEFAULT_SYSTEM_PROMPT.trimIndent()
    }

    private fun looksLikeEchoOrLoop(full: String, user: String): Boolean {
        val f = full.trim()
        if (f.isEmpty()) return false

        val idx = f.indexOf(user, startIndex = minOf(80, f.length))
        if (idx >= 0) return true

        val tail = f.takeLast(minOf(400, f.length))
        val sentences =
            tail.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length >= 60 }
        if (sentences.isNotEmpty()) {
            val last = sentences.last()
            val firstIdx = f.indexOf(last)
            val lastIdx = f.lastIndexOf(last)
            if (firstIdx >= 0 && lastIdx > firstIdx) return true
        }
        return false
    }

    private fun trimLoop(full: String, user: String): String {
        val f = full.trim()
        val idxEcho = f.indexOf(user, startIndex = minOf(80, f.length))
        if (idxEcho >= 0) return f.substring(0, idxEcho).trim()

        val sentences = f.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }
        if (sentences.isNotEmpty()) {
            val seen = HashSet<String>()
            val out = StringBuilder()
            for (s in sentences) {
                val key = s.lowercase()
                if (key.length >= 60 && !seen.add(key)) break
                if (out.isNotEmpty()) out.append(' ')
                out.append(s)
            }
            if (out.isNotEmpty()) return out.toString().trim()
        }
        return f
    }

    private fun notifyChatCompletedForReview() {
        screenModelScope.launch {
            runCatching { reviewRequestManager.onChatCompleted() }
        }
    }

    private fun notifyGenerateModelLoadedForReview() {
        screenModelScope.launch {
            runCatching { reviewRequestManager.onGenerateModelLoaded() }
        }
    }

    private fun buildTitle(firstUserMessage: String): String {
        val t = firstUserMessage.trim().replace("\n", " ")
        return if (t.length <= 40) t else t.take(40) + "…"
    }

    private suspend fun refreshSessions() {
        _state.value = _state.value.copy(chatSessions = chatHistoryRepository.getSummaries())
    }

    private fun toPersistedMessages(messages: List<ChatUiModel.Message>): List<PersistedChatMessage> {
        return messages
            .filter { it.text.isNotBlank() }
            .map {
                PersistedChatMessage(
                    text = it.text,
                    author = if (it.author == ChatUiModel.Author.me) PersistedAuthor.ME else PersistedAuthor.BOT
                )
            }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun persistCurrentConversationIfNeeded() {
        if (_state.value.isTemporaryChat) return
        val id = currentChatId ?: return

        val now = System.now().toEpochMilliseconds()
        val existing = chatHistoryRepository.getSession(id)
        val createdAt = existing?.createdAtEpochMs ?: now
        val title = existing?.title ?: buildTitle(
            _conversation.value.firstOrNull { it.author == ChatUiModel.Author.me }?.text.orEmpty()
        )

        val session = ChatSession(
            id = id,
            title = title,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = now,
            messages = toPersistedMessages(_conversation.value)
        )

        chatHistoryRepository.upsert(session)
        refreshSessions()
    }
}

data class ChatUiModel(
    val messages: List<Message>,
    val addressee: Author,
) {
    data class Message(
        val text: String,
        val author: Author,
        val imagePng: ByteArray? = null,
        val imageFileName: String? = null,
        val imageRgba: ByteArray? = null,
        val imageWidth: Int? = null,
        val imageHeight: Int? = null,
    ) {
        val isFromMe: Boolean get() = author.id == MY_ID
        val hasImage: Boolean get() = imagePng != null || imageRgba != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Message

            if (text != other.text) return false
            if (author != other.author) return false

            if (imagePng != null || other.imagePng != null) {
                if (imagePng == null || other.imagePng == null) return false
                if (!imagePng.contentEquals(other.imagePng)) return false
            }

            if (imageRgba != null || other.imageRgba != null) {
                if (imageRgba == null || other.imageRgba == null) return false
                if (!imageRgba.contentEquals(other.imageRgba)) return false
            }

            if (imageWidth != other.imageWidth) return false
            if (imageHeight != other.imageHeight) return false
            if (imageFileName != other.imageFileName) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + author.hashCode()
            result = 31 * result + (imagePng?.contentHashCode() ?: 0)
            result = 31 * result + (imageFileName?.hashCode() ?: 0)
            result = 31 * result + (imageRgba?.contentHashCode() ?: 0)
            result = 31 * result + (imageWidth ?: 0)
            result = 31 * result + (imageHeight ?: 0)
            return result
        }
    }

    data class Author(
        val id: String,
        val name: String
    ) {
        companion object {
            val bot = Author(BOT_ID, "Llamatik AI")
            val me = Author(MY_ID, "Me")
        }
    }

    companion object {
        const val MY_ID = "-1"
        const val BOT_ID = "1"
    }
}

data class ChatBotState(
    val greeting: String,
    val header: String,
    val isPrivacyMessageDisplayed: Boolean = false,
    val latestNews: List<FeedItem>,
    val embedModels: List<LlamaModel> = emptyList(),
    val generateModels: List<LlamaModel> = emptyList(),
    val sttModels: List<LlamaModel> = emptyList(),
    val stableDiffusionModels: List<LlamaModel> = emptyList(),
    val isEmbedModelLoaded: Boolean = false,
    val isGenerateModelLoaded: Boolean = false,
    val isSttModelLoaded: Boolean = false,
    val isStableDiffusionModelLoaded: Boolean = false,
    val selectedEmbedModelName: String? = null,
    val selectedGenerateModelName: String? = null,
    val selectedSttModelName: String? = null,
    val selectedStableDiffusionModelName: String? = null,
    val isGenerating: Boolean = false,
    val isInitialSetup: Boolean = false,
    val initialSetupModelName: String? = null,
    val initialSetupProgress: Int = 0,
    val generateSettings: GenerateSettings = GenerateSettings(),
    val chatSessions: List<ChatSessionSummary> = emptyList(),
    val isTemporaryChat: Boolean = false,
    val generationMode: GenerationMode = GenerationMode.TEXT,

    val ragPdfFileName: String? = null,
    val isRagIndexing: Boolean = false,
    val ragIndexingProgress: Int = 0,
    val ragChunksCount: Int = 0,
)

sealed class ChatBotSideEffects {
    data object Initial : ChatBotSideEffects()
    data object OnLoaded : ChatBotSideEffects()
    data object OnMessageLoading : ChatBotSideEffects()
    data object OnMessageLoaded : ChatBotSideEffects()
    data object OnNoResults : ChatBotSideEffects()
    data object OnLoadError : ChatBotSideEffects()
    data object ScrollToBottom : ChatBotSideEffects()
    data object OnEmbedModelLoaded : ChatBotSideEffects()
    data object OnEmbedModelLoadError : ChatBotSideEffects()
    data object OnGenerateModelLoaded : ChatBotSideEffects()
    data object OnGenerateModelLoadError : ChatBotSideEffects()
    data object OnSttModelLoaded : ChatBotSideEffects()
    data object OnSttModelLoadError : ChatBotSideEffects()
    data object OnSettingsChanged : ChatBotSideEffects()
    data object OnStableDiffusionModelLoaded : ChatBotSideEffects()
    data object OnStableDiffusionModelLoadError : ChatBotSideEffects()
    data class OnCacheCleared(val message: String) : ChatBotSideEffects()
    data class OnCacheClearFailed(val message: String) : ChatBotSideEffects()
}
