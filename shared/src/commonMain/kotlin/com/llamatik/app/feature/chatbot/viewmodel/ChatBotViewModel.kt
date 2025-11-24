package com.llamatik.app.feature.chatbot.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.ChatBotOnboardingScreen
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.usecases.GetModelsUseCase
import com.llamatik.app.feature.chatbot.utils.ChatMessage
import com.llamatik.app.feature.chatbot.utils.ChatRunner
import com.llamatik.app.feature.chatbot.utils.Gemma3
import com.llamatik.app.feature.chatbot.utils.VectorStoreData
import com.llamatik.app.feature.chatbot.utils.loadVectorStoreEntries
import com.llamatik.app.feature.chatbot.utils.retrieveContext
import com.llamatik.app.feature.news.NewsFeedDetailScreen
import com.llamatik.app.feature.news.NewsFeedScreen
import com.llamatik.app.feature.news.repositories.FeedItem
import com.llamatik.app.feature.news.usecases.GetAllNewsUseCase
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

class ChatBotViewModel(
    private var navigator: Navigator,
    private val settings: Settings,
    private val getAllNewsUseCase: GetAllNewsUseCase,
    private val getModelsUseCase: GetModelsUseCase,
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
        )
    )
    val state = _state.asStateFlow()

    private val _sideEffects = Channel<ChatBotSideEffects>()
    val sideEffects: Flow<ChatBotSideEffects> = _sideEffects.receiveAsFlow()

    private var vectorStore: VectorStoreData? = null

    private val _conversation = MutableStateFlow(emptyList<ChatUiModel.Message>())
    val conversation: StateFlow<List<ChatUiModel.Message>> get() = _conversation

    /** Guard to ignore late callbacks when a new request starts */
    @Volatile
    private var activeRequestId: String? = null

    @Volatile
    private var started = false

    init {
        val isPrivacyMessageDisplayed = settings.getBoolean(PRIVACY_CHATBOT_VIEWED_KEY, false)
        if (isPrivacyMessageDisplayed) {
            navigator.push(ChatBotOnboardingScreen { onPrivacyAccepted() })
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
            in 16..21 -> getCurrentLocalization().greetingEvening
            else -> getCurrentLocalization().greetingNight
        }
    }

    fun onStarted(navigator: Navigator? = null, embedFilePath: String? = null, generatorFilePath: String? = null) {
        navigator?.let {
            this.navigator = it
        }
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
                .onSuccess {
                    _state.value = _state.value.copy(latestNews = it)
                }
                .onFailure { error ->
                    Logger.e(error.message ?: "Unknown error")
                }
            getModelsUseCase.getDefaultEmbedModels()
                .onSuccess {
                    _state.value = _state.value.copy(embedModels = it)
                }
                .onFailure { error ->
                    Logger.e(error.message ?: "Unknown error")
                }
            getModelsUseCase.getDefaultGenerateModels()
                .onSuccess {
                    for (model in it) {
                        model.localPath?.let {
                            Logger.d("LlamaVM - Init Generate Model: ${model.name}")
                            val isLoaded = LlamaBridge.initGenerateModel(model.localPath)
                            if (isLoaded) {
                                _state.value = _state.value.copy(selectedGenerateModelName = model.name)
                                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoaded)
                            } else {
                                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
                            }
                        }
                    }
                    _state.value = _state.value.copy(generateModels = it)
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
        _sideEffects.trySend(ChatBotSideEffects.OnLoaded)
    }

    fun onEmbedModelSelected(model: LlamaModel) {
        screenModelScope.launch(Dispatchers.IO) {
            val pathFromState = model.localPath
            val pathFromStorage = getModelsUseCase.getSavedModelPath(model.name)
                .takeIf { it.isNotEmpty() }
            val path = pathFromState ?: pathFromStorage

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
            val pathFromState = model.localPath
            val pathFromStorage = getModelsUseCase.getSavedModelPath(model.name)
                .takeIf { it.isNotEmpty() }
            val path = pathFromState ?: pathFromStorage

            if (!path.isNullOrEmpty()) {
                Logger.d("LlamaVM - initGenerateModel $path")
                val isLoaded = LlamaBridge.initGenerateModel(path)
                if (isLoaded) {
                    _state.value = _state.value.copy(selectedGenerateModelName = model.name)
                    _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoaded)
                } else {
                    _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
                }
            } else {
                Logger.e { "LlamaVM - no local path for generate model ${model.name}" }
                _sideEffects.trySend(ChatBotSideEffects.OnGenerateModelLoadError)
            }
        }
    }

    fun onDownloadModel(model: LlamaModel) {
        screenModelScope.launch(Dispatchers.IO) {
            try {
                updateDownload(model.url) {
                    it.copy(
                        inProgress = true,
                        progress = 0,
                        done = false,
                        error = null
                    )
                }
                getModelsUseCase.downloadModel(model) { pct ->
                    updateDownload(model.url) { ds ->
                        ds.copy(
                            inProgress = true,
                            progress = pct.coerceIn(0, 100)
                        )
                    }
                }.onSuccess { tempFile ->
                    Logger.d("LlamaVM - download finished")
                    updateDownload(model.url) {
                        it.copy(
                            inProgress = false,
                            progress = 100,
                            done = true
                        )
                    }
                    getModelsUseCase.saveModelPath(model.name, tempFile.absolutePath())
                    _state.value = _state.value.copy(
                        embedModels = _state.value.embedModels.map {
                            if (it.url == model.url) it.copy(
                                fileName = tempFile.absolutePath(),
                                localPath = tempFile.absolutePath()
                            ) else it
                        },
                        generateModels = _state.value.generateModels.map {
                            if (it.url == model.url) it.copy(
                                fileName = tempFile.absolutePath(),
                                localPath = tempFile.absolutePath()
                            ) else it
                        },
                    )

                    /*
                    val file = FileKit.openFileSaver(
                        suggestedName = model.fileName?.urlToFileName() ?: model.name,
                        extension = "gguf"
                    )
                    file?.let {
                        val fullFilenameWithPath = "${file.path}${file.name}"
                        Logger.d("LlamaVM - saving model to $fullFilenameWithPath")
                        file.write(tempFile.readBytes())
                        _state.value = _state.value.copy(
                            embedModels = _state.value.embedModels.map { if (it.url == model.url) it.copy(fileName = fullFilenameWithPath) else it },
                            generateModels = _state.value.generateModels.map { if (it.url == model.url) it.copy(fileName = fullFilenameWithPath) else it },
                        )
                    }
                     */
                }.onFailure { e ->
                    updateDownload(model.url) {
                        it.copy(
                            inProgress = false,
                            error = e.message ?: "Download failed"
                        )
                    }
                }
            } catch (t: Throwable) {
                updateDownload(model.url) {
                    it.copy(
                        inProgress = false,
                        error = t.message ?: "Download failed"
                    )
                }
            }
        }
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

                    // Tighter system prompt to discourage repetition
                    val systemPrompt = """
                        You are a concise technical assistant.
                        Use ONLY the CONTEXT to answer. If the context is insufficient, say briefly that you don't have enough information.
                        Do NOT repeat the user's question. Do NOT repeat sentences verbatim. Avoid lists longer than 6 items.
                        End your answer when done.
                    """.trimIndent()

                    // Placeholder for streaming assistant
                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    // Build common chat history (user + any prior assistant turns), drop placeholder
                    val chatHistory: List<ChatMessage> =
                        toChatMessages(_conversation.value.dropLast(1))

                    // Streaming with loop/echo guard
                    val requestId = kotlin.random.Random.nextLong().toString()
                    activeRequestId = requestId
                    val acc = StringBuilder()

                    ChatRunner.stream(
                        system = systemPrompt,
                        contexts = listOf(compact),
                        messages = chatHistory,  // last item is the user question we just appended
                        template = Gemma3,
                        maxTokens = 256,         // keep turns tight to avoid run-ons
                        onDelta = { chunk ->
                            if (activeRequestId != requestId) return@stream
                            if (chunk.isEmpty()) return@stream

                            acc.append(chunk)
                            _conversation.value = _conversation.value.dropLast(1) +
                                    ChatUiModel.Message(acc.toString(), ChatUiModel.Author.bot)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

                            // ---- Loop/Echo Guard ----
                            if (looksLikeEchoOrLoop(
                                    full = acc.toString(),
                                    user = question
                                )
                            ) {
                                // finish early with the trimmed text
                                val trimmed = trimLoop(acc.toString(), user = question)
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(trimmed, ChatUiModel.Author.bot)
                                activeRequestId = null
                                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
                            }
                        },
                        onComplete = { final ->
                            if (activeRequestId != requestId) return@stream
                            _conversation.value = _conversation.value.dropLast(1) +
                                    ChatUiModel.Message(final, ChatUiModel.Author.bot)
                            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)
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

    // === Alternative entry point using LlamaBridge directly (no RAG/embeddings) ===
    fun onMessageSendDirect(message: String) {
        val input = message.trim()
        if (input.isBlank()) return

        screenModelScope.launch {
            // 1) Append user message bubble
            _conversation.value += ChatUiModel.Message(input, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

            withContext(Dispatchers.IO) {
                try {
                    // 2) Instruction-style template (works better for small instruct GGUFs like SmolVLM/Phi/Gemma-instruct)
                    val prompt = buildString {
                        appendLine("You are a helpful, concise assistant. Answer clearly and directly.")
                        appendLine("Avoid long lists or repeating words. Keep answers short (3–6 sentences).")
                        appendLine()
                        appendLine("### Instruction:")
                        appendLine(input)
                        appendLine()
                        append("### Response:\n")
                    }

                    // 3) Insert a placeholder assistant bubble we will stream into
                    _conversation.value += ChatUiModel.Message("", ChatUiModel.Author.bot)

                    val requestId = kotlin.random.Random.nextLong().toString()
                    activeRequestId = requestId
                    val acc = StringBuilder()
                    var completed = false

                    // tiny anti-babble guard: if the same short token repeats too much, stop
                    fun looksLikeBabble(s: String): Boolean {
                        if (s.length < 60) return false
                        // detect pathological repetition like "3, 3, 3, ..." or the same short token repeating
                        val tail = s.takeLast(200)
                        val collapsed = tail.replace("\\s+".toRegex(), " ").trim()
                        val commas = collapsed.count { it == ',' }
                        if (commas > 60) return true
                        // repeated 1–3 char tokens 30+ times
                        val m =
                            Regex("""\b([A-Za-z0-9]{1,3})\b(?:[,\s]+\1\b){25,}""").find(collapsed)
                        return m != null
                    }

                    // 4) Stream tokens directly from the bridge using the callback interface
                    LlamaBridge.generateStream(
                        prompt = prompt,
                        callback = object : GenStream {
                            override fun onDelta(text: String) {
                                if (activeRequestId != requestId || completed) return

                                acc.append(text)
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(acc.toString(), ChatUiModel.Author.bot)
                                _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

                                // Your existing echo/loop guard (protects against model mirroring the user)
                                if (looksLikeEchoOrLoop(full = acc.toString(), user = input)) {
                                    val trimmed = trimLoop(acc.toString(), user = input)
                                    _conversation.value = _conversation.value.dropLast(1) +
                                            ChatUiModel.Message(trimmed, ChatUiModel.Author.bot)
                                    completed = true
                                    activeRequestId = null
                                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                    return
                                }

                                // Anti-babble guard for tiny models
                                if (looksLikeBabble(acc.toString())) {
                                    completed = true
                                    activeRequestId = null
                                    // lightly trim trailing commas/dangling tokens
                                    val cleaned = acc.toString().trim().trimEnd(',', ' ', '\n')
                                    _conversation.value = _conversation.value.dropLast(1) +
                                            ChatUiModel.Message(cleaned, ChatUiModel.Author.bot)
                                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                }
                            }

                            override fun onComplete() {
                                if (activeRequestId != requestId || completed) return
                                completed = true
                                activeRequestId = null
                                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                            }

                            override fun onError(message: String) {
                                if (activeRequestId != requestId) return
                                _conversation.value = _conversation.value.dropLast(1) +
                                        ChatUiModel.Message(
                                            "There is a problem with the AI: $message",
                                            ChatUiModel.Author.bot
                                        )
                                activeRequestId = null
                                _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    emitBot("There is a problem with the AI: ${t.message ?: "Unknown error"}")
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                }
            }
        }
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
        activeRequestId = null
        screenModelScope.launch { _conversation.emit(emptyList()) }
    }

    fun onShowPrivacyScreen() {
        navigator.push(ChatBotOnboardingScreen { onPrivacyAccepted() })
    }

    fun onOpenFeedItemDetail(link: String) {
        navigator.push(NewsFeedDetailScreen(link))
    }

    fun onOpenNewsClicked() {
        navigator.push(NewsFeedScreen())
    }

    private fun onPrivacyAccepted() {
        settings.putBoolean(PRIVACY_CHATBOT_VIEWED_KEY, true)
        navigator.pop()
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

    // --- Echo/loop guard utilities ---

    /** Detects obvious loops: question echoed, or a long suffix repeated twice. */
    private fun looksLikeEchoOrLoop(full: String, user: String): Boolean {
        val f = full.trim()
        if (f.isEmpty()) return false

        // 1) If the model starts echoing the question again after the first 80 chars, stop
        val idx = f.indexOf(user, startIndex = minOf(80, f.length))
        if (idx >= 0) return true

        // 2) Repetition of a long span (n-gram) near the end
        val tail = f.takeLast(minOf(400, f.length))
        // Look for the last sentence (>= 60 chars) being duplicated
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

    /** Trim after the first occurrence of repeated content or before echoed question. */
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
    val isEmbedModelLoaded: Boolean = false,
    val isGenerateModelLoaded: Boolean = false,
    val selectedEmbedModelName: String? = null,
    val selectedGenerateModelName: String? = null,
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
    data object OnSettingsChanged : ChatBotSideEffects()
}