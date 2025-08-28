package com.llamatik.app.feature.chatbot.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import co.touchlab.kermit.Logger
import com.llamatik.app.feature.chatbot.ChatbotOnboardingScreen
import com.llamatik.app.feature.chatbot.utils.VectorStoreData
import com.llamatik.app.feature.chatbot.utils.loadVectorStoreEntries
import com.llamatik.app.feature.chatbot.utils.retrieveContext
import com.llamatik.app.feature.chatbot.utils.tidyAnswer
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

private const val PRIVACY_CHATBOT_VIEWED_KEY = "privacy_chatbot_viewed_key"

class ChatBotViewModel(
    private val rootNavigatorRepository: RootNavigatorRepository,
    private val settings: Settings,
) : ScreenModel {

    private val _state = MutableStateFlow(ChatBotState())
    val state = _state.asStateFlow()

    private val _sideEffects = Channel<ChatBotSideEffects>()
    val sideEffects: Flow<ChatBotSideEffects> = _sideEffects.receiveAsFlow()

    private var vectorStore: VectorStoreData? = null

    private val _conversation = MutableStateFlow(emptyList<ChatUiModel.Message>())
    val conversation: StateFlow<List<ChatUiModel.Message>> get() = _conversation

    init {
        val isPrivacyMessageDisplayed = settings.getBoolean(PRIVACY_CHATBOT_VIEWED_KEY, false)
        if (isPrivacyMessageDisplayed) {
            rootNavigatorRepository.navigator.push(ChatbotOnboardingScreen { onPrivacyAccepted() })
        }
    }

    fun onStarted(embedFilePath: String, generatorFilePath: String) {
        LlamaBridge.initModel(embedFilePath)
        LlamaBridge.initGenerateModel(generatorFilePath)
        screenModelScope.launch {
            vectorStore = loadVectorStoreEntries()
        }
    }

    override fun onDispose() {
        LlamaBridge.shutdown()
    }

    fun onMessageSend(message: String) {
        if (message.isBlank()) return

        screenModelScope.launch {
            val myChat = ChatUiModel.Message(message, ChatUiModel.Author.me)
            _conversation.value += myChat
            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)

            withContext(Dispatchers.IO) {
                try {
                    val qVec = LlamaBridge.embed(message).toList()
                    val store = vectorStore
                    if (store == null) {
                        emitBot("There is a problem with the AI")
                        return@withContext
                    }

                    val topItems = retrieveContext(
                        queryVector = qVec,
                        questionText = message,
                        vectorStore = store,
                        poolSize = 80,
                        topContext = 4
                    )

                    Logger.d("LlamaVM - retrieveContext -> ${topItems.size} items")

                    val rawContext = topItems.joinToString("\n") { it.text }
                    val compact = buildCompactContext(rawContext, message, hardLimit = 1800)
                    Logger.d("LlamaVM - Context length=${compact.length}")

                    if (!isLikelyRelevant(compact, message)) {
                        emitBot("I don't have enough information in my sources.")
                        _sideEffects.trySend(ChatBotSideEffects.OnNoResults)
                        return@withContext
                    }

                    // Let JNI supply strict RAG rules to avoid echo
                    val systemPrompt = ""

                    val responseText = try {
                        LlamaBridge.generateWithContext(
                            systemPrompt,
                            compact,
                            message.trim()
                        )
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        null
                    }

                    val finalText = if (responseText.isNullOrBlank()) {
                        "I don't have enough information in my sources."
                    } else tidyAnswer(responseText)

                    emitBot(finalText)
                    _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                } catch (t: Throwable) {
                    t.printStackTrace()
                    emitBot("There is a problem with the AI")
                    _sideEffects.trySend(ChatBotSideEffects.OnLoadError)
                }
            }
        }
    }

    private suspend fun emitBot(text: String) {
        _conversation.value += ChatUiModel.Message(text, ChatUiModel.Author.bot)
    }

    private fun buildCompactContext(source: String, question: String, hardLimit: Int): String {
        val qTokens = question.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        val sentences = source
            .replace("\\s+".toRegex(), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hits = sentences.filter { s ->
            val lower = s.lowercase()
            qTokens.count { t -> lower.contains(t) } >= 1
        }

        val chosen = (if (hits.isNotEmpty()) hits else sentences.take(6))
            .joinToString(" ")

        val clipped = if (chosen.length <= hardLimit) chosen else chosen.take(hardLimit)
        return clipped
    }

    private fun isLikelyRelevant(context: String, question: String): Boolean {
        val qTokens = question.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()
        val ctx = context.lowercase()
        val hits = qTokens.count { ctx.contains(it) }
        Logger.d("LlamaVM - relevance hits=$hits tokens=${qTokens.size}")
        return hits >= 2
    }

    fun onClearConversation() {
        screenModelScope.launch { _conversation.emit(emptyList()) }
    }

    fun onShowPrivacyScreen() {
        rootNavigatorRepository.navigator.push(ChatbotOnboardingScreen { onPrivacyAccepted() })
    }

    private fun onPrivacyAccepted() {
        settings.putBoolean(PRIVACY_CHATBOT_VIEWED_KEY, true)
        rootNavigatorRepository.navigator.pop()
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

data class ChatBotState(val isPrivacyMessageDisplayed: Boolean = false)

sealed class ChatBotSideEffects {
    data object Initial : ChatBotSideEffects()
    data object OnLoaded : ChatBotSideEffects()
    data object OnMessageLoading : ChatBotSideEffects()
    data object OnMessageLoaded : ChatBotSideEffects()
    data object OnNoResults : ChatBotSideEffects()
    data object OnLoadError : ChatBotSideEffects()
}