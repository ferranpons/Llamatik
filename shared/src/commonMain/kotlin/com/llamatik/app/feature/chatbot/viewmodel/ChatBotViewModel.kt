package com.llamatik.app.feature.chatbot.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.ChatbotOnboardingScreen
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
import com.llamatik.app.platform.RootNavigatorRepository
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
import kotlin.concurrent.Volatile
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

private const val PRIVACY_CHATBOT_VIEWED_KEY = "privacy_chatbot_viewed_key"

class ChatBotViewModel(
    private val rootNavigatorRepository: RootNavigatorRepository,
    private val settings: Settings,
    private val getAllNewsUseCase: GetAllNewsUseCase,
) : ScreenModel {

    private val _state = MutableStateFlow(
        ChatBotState(
            greeting = "",
            header = getCurrentLocalization().welcome,
            latestNews = emptyList(),
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

    init {
        val isPrivacyMessageDisplayed = settings.getBoolean(PRIVACY_CHATBOT_VIEWED_KEY, false)
        if (isPrivacyMessageDisplayed) {
            rootNavigatorRepository.navigator.push(ChatbotOnboardingScreen { onPrivacyAccepted() })
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

    fun onStarted(embedFilePath: String, generatorFilePath: String) {
        LlamaBridge.initModel(embedFilePath)
        LlamaBridge.initGenerateModel(generatorFilePath)
        screenModelScope.launch {
            getAllNewsUseCase.invoke()
                .onSuccess {
                    _state.value = _state.value.copy(latestNews = it)
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

    fun onMessageSend(message: String) {
        val question = message.trim()
        if (question.isBlank()) return

        screenModelScope.launch {
            _conversation.value += ChatUiModel.Message(question, ChatUiModel.Author.me)
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)
            _sideEffects.trySend(ChatBotSideEffects.ScrollToBottom)

            withContext(Dispatchers.IO) {
                try {
                    val qVec = LlamaBridge.embed(question).toList()
                    val store = vectorStore ?: return@withContext emitBot("There is a problem with the AI")

                    val topItems = retrieveContext(qVec, question, store, poolSize = 80, topContext = 4)
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
                    val chatHistory: List<ChatMessage> = toChatMessages(_conversation.value.dropLast(1))

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
                                    ChatUiModel.Message("There is a problem with the AI: $err", ChatUiModel.Author.bot)
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
        rootNavigatorRepository.navigator.push(ChatbotOnboardingScreen { onPrivacyAccepted() })
    }

    fun onOpenFeedItemDetail(link: String) {
        rootNavigatorRepository.navigator.push(NewsFeedDetailScreen(link))
    }

    fun onOpenNewsClicked() {
        rootNavigatorRepository.navigator.push(NewsFeedScreen())
    }

    private fun onPrivacyAccepted() {
        settings.putBoolean(PRIVACY_CHATBOT_VIEWED_KEY, true)
        rootNavigatorRepository.navigator.pop()
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
        val sentences = tail.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length >= 60 }
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
)

sealed class ChatBotSideEffects {
    data object Initial : ChatBotSideEffects()
    data object OnLoaded : ChatBotSideEffects()
    data object OnMessageLoading : ChatBotSideEffects()
    data object OnMessageLoaded : ChatBotSideEffects()
    data object OnNoResults : ChatBotSideEffects()
    data object OnLoadError : ChatBotSideEffects()
    data object ScrollToBottom : ChatBotSideEffects()
}