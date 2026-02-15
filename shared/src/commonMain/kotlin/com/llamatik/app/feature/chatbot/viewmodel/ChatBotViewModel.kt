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
import com.llamatik.app.feature.chatbot.utils.PromptTemplate
import com.llamatik.app.feature.chatbot.utils.VectorStoreData
import com.llamatik.app.feature.chatbot.utils.loadVectorStoreEntries
import com.llamatik.app.feature.chatbot.utils.retrieveContext
import com.llamatik.app.feature.news.NewsFeedDetailScreen
import com.llamatik.app.feature.news.NewsFeedScreen
import com.llamatik.app.feature.news.repositories.FeedItem
import com.llamatik.app.feature.news.usecases.GetAllNewsUseCase
import com.llamatik.app.feature.reviews.ReviewRequestManager
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.LlamatikTempFile
import com.llamatik.app.platform.migrateModelPathIfNeeded
import com.llamatik.app.platform.tts.TtsEngine
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.WhisperBridge
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import kotlin.concurrent.Volatile
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

private const val PRIVACY_CHATBOT_VIEWED_KEY = "privacy_chatbot_viewed_key"
private const val DEFAULT_SYSTEM_PROMPT = """
You are Llamatik, a privacy-first local AI assistant running fully on-device.
Be clear, honest, and concise. Answer in the user's language.
"""

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

    private fun resolveAndMigratePath(model: LlamaModel): String? {
        val pathFromState = model.localPath
        val pathFromStorage = getModelsUseCase.getSavedModelPath(model.name).takeIf { it.isNotEmpty() }
        val rawPath = pathFromState ?: pathFromStorage
        if (rawPath.isNullOrBlank()) return null

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

        screenModelScope.launch(Dispatchers.IO) {
            embedFilePath?.let {
                LlamaBridge.initModel(embedFilePath)
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
                .onSuccess { _state.value = _state.value.copy(embedModels = it) }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            // --- STT models list + attempt load any already-downloaded model ---
            getModelsUseCase.getDefaultSTTModels()
                .onSuccess { models ->
                    // Try to init any already-downloaded STT models
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

                    // If privacy already accepted, start STT initial setup if needed (after list is ready)
                    if (hasAcceptedPrivacy) {
                        startSttInitialSetupIfNeeded(normalized)
                    }
                }
                .onFailure { error -> Logger.e(error.message ?: "Unknown error") }

            val summaries = chatHistoryRepository.getSummaries()
            _state.value = _state.value.copy(chatSessions = summaries)

            getModelsUseCase.getDefaultGenerateModels()
                .onSuccess { models ->
                    // Try to init any already-downloaded generate models
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
                        // (STT setup is also launched when STT list loads; this ensures both paths are covered)
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

            vectorStore = loadVectorStoreEntries()
        }

        onGenerateSettingsApplied(_state.value.generateSettings)
        _sideEffects.trySend(ChatBotSideEffects.OnLoaded)
    }

    /**
     * Initial setup: download default generate model if none exists.
     */
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
            val path = tempFile.absolutePath()

            getModelsUseCase.saveModelPath(defaultModel.name, path)

            _state.value = _state.value.copy(
                generateModels = _state.value.generateModels.map {
                    if (it.url == url) it.copy(fileName = path, localPath = path) else it
                }
            )

            val loaded = LlamaBridge.initGenerateModel(path)
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

    /**
     * Initial setup: download default STT model if none exists.
     * Same logic as generate initial setup, but loads WhisperBridge.
     */
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
            val path = tempFile.absolutePath()

            getModelsUseCase.saveModelPath(defaultModel.name, path)

            _state.value = _state.value.copy(
                sttModels = _state.value.sttModels.map {
                    if (it.url == url) it.copy(fileName = path, localPath = path) else it
                }
            )

            val loaded = WhisperBridge.initModel(path)
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
        screenModelScope.launch(Dispatchers.IO) {
            val path = resolveAndMigratePath(model)

            if (!path.isNullOrEmpty()) {
                Logger.d("LlamaVM - initEmbedModel $path")
                val isLoaded = LlamaBridge.initModel(path)
                if (isLoaded) {
                    _state.value = _state.value.copy(selectedEmbedModelName = model.name)
                    _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoaded)
                } else {
                    _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoadError)
                }
            } else {
                Logger.e { "LlamaVM - no local path for embed model ${model.name}" }
                _sideEffects.trySend(ChatBotSideEffects.OnEmbedModelLoadError)
            }
        }
    }

    fun onGenerateModelSelected(model: LlamaModel) {
        screenModelScope.launch(Dispatchers.IO) {
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
        screenModelScope.launch(Dispatchers.IO) {
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

    fun onDownloadModel(model: LlamaModel) {
        val url = model.url
        val existingJob = downloadJobs[url]
        if (existingJob?.isActive == true) return

        val job = screenModelScope.launch(Dispatchers.IO) {
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
                        getModelsUseCase.saveModelPath(model.name, ev.localPath)

                        _state.value = _state.value.copy(
                            embedModels = _state.value.embedModels.map {
                                if (it.url == url) it.copy(fileName = ev.localPath, localPath = ev.localPath) else it
                            },
                            generateModels = _state.value.generateModels.map {
                                if (it.url == url) it.copy(fileName = ev.localPath, localPath = ev.localPath) else it
                            },
                            sttModels = _state.value.sttModels.map {
                                if (it.url == url) it.copy(fileName = ev.localPath, localPath = ev.localPath) else it
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
        screenModelScope.launch(Dispatchers.IO) {
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
                    // If user deleted the currently selected STT model, mark not loaded
                    selectedSttModelName = if (_state.value.selectedSttModelName == model.name) null else _state.value.selectedSttModelName,
                    isSttModelLoaded = if (_state.value.selectedSttModelName == model.name) false else _state.value.isSttModelLoaded
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

    fun onMessageSendWithEmbed(message: String) {
        val question = message.trim()
        if (question.isBlank()) return

        screenModelScope.launch {
            _conversation.value += ChatUiModel.Message(question, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

            withContext(Dispatchers.IO) {
                try {
                    val qVec = LlamaBridge.embed(question).toList()
                    val store =
                        vectorStore ?: return@withContext emitBot("There is a problem with the AI")

                    val topItems =
                        retrieveContext(qVec, question, store, poolSize = 80, topContext = 4)
                    val rawContext = topItems.joinToString("\n\n") { sanitizeForRag(it.text) }
                    val compact = buildCompactContext(rawContext, question, hardLimit = 1600)

                    if (!isLikelyRelevant(compact, question)) {
                        emitBot("I don't have enough information in my sources.")
                        _sideEffects.trySend(ChatBotSideEffects.OnNoResults)
                        _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                        return@withContext
                    }

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

                            if (looksLikeEchoOrLoop(
                                    full = acc.toString(),
                                    user = question
                                )
                            ) {
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
                                        "There is a problem with the AI: $err",
                                        ChatUiModel.Author.bot
                                    )
                            _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                        }
                    )

                } catch (t: Throwable) {
                    t.printStackTrace()
                    emitBot("There is a problem with the AI")
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                    _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                }
            }
        }
    }

    // === Alternative entry point using ChatRunner directly (no RAG/embeddings) ===
    fun onMessageSendDirect(message: String) {
        val input = message.trim()
        if (input.isBlank()) return

        screenModelScope.launch {
            if (!_state.value.isTemporaryChat && currentChatId == null) {
                currentChatId = kotlin.random.Random.nextLong().toString()
            }

            // 1) Add user message
            _conversation.value += ChatUiModel.Message(input, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
            _state.value = _state.value.copy(isGenerating = true)

            withContext(Dispatchers.IO) {
                try {
                    persistCurrentConversationIfNeeded()

                    // 2) Reserve an empty bot bubble
                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    // 3) Build chat history (everything except the empty last bot)
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
                                            "There is a problem with the AI: $err",
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
                    emitBot("There is a problem with the AI: ${t.message ?: "Unknown error"}")
                    activeRequestId = null
                    _state.value = _state.value.copy(isGenerating = false)
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                }
            }
        }
    }

    /** Called from UI Stop button – logical stop + native cancellation */
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
        screenModelScope.launch(Dispatchers.IO) {
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
        screenModelScope.launch(Dispatchers.IO) {
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

        // After onboarding is closed, start initial setup (Gemma 3 download) if needed.
        screenModelScope.launch(Dispatchers.IO) {
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

    // --- mapping helpers ---

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

        // Fallback to Gemma3 so we never break if something is null
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
    ) {
        val isFromMe: Boolean get() = author.id == MY_ID
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
    val isEmbedModelLoaded: Boolean = false,
    val isGenerateModelLoaded: Boolean = false,
    val isSttModelLoaded: Boolean = false,
    val selectedEmbedModelName: String? = null,
    val selectedGenerateModelName: String? = null,
    val selectedSttModelName: String? = null,
    val isGenerating: Boolean = false,
    val isInitialSetup: Boolean = false,
    val initialSetupModelName: String? = null,
    val initialSetupProgress: Int = 0,
    val generateSettings: GenerateSettings = GenerateSettings(),
    val chatSessions: List<ChatSessionSummary> = emptyList(),
    val isTemporaryChat: Boolean = false,
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
}
