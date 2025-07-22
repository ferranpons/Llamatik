package com.llamatik.app.ui.screens.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.llamatik.app.ai.VectorStoreData
import com.llamatik.app.ai.findTopKRelevantDocumentsDebug
import com.llamatik.app.ai.loadVectorStoreEntries
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.ui.screens.ChatbotOnboardingScreen
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
    private val settings: Settings
) : ScreenModel {
    private val _state = MutableStateFlow(ChatBotState())
    val state = _state.asStateFlow()
    private val _sideEffects = Channel<ChatBotSideEffects>()
    val sideEffects: Flow<ChatBotSideEffects> = _sideEffects.receiveAsFlow()
    private var vectorStore: VectorStoreData? = null

    private val _conversation = MutableStateFlow(
        emptyList<ChatUiModel.Message>()
    )
    val conversation: StateFlow<List<ChatUiModel.Message>>
        get() = _conversation

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

    fun onMessageSend(message: String) {
        if (message.isNotBlank()) {
            screenModelScope.launch {
                val myChat = ChatUiModel.Message(message, ChatUiModel.Author.me)
                _conversation.value += myChat
                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoading)

                withContext(Dispatchers.IO) {
                    val vectorToEmbed = LlamaBridge.embed(message)
                    vectorStore?.let { store ->
                        val searchResults =
                            findTopKRelevantDocumentsDebug(vectorToEmbed.toList(), store, 5)

                        val prompt = buildPrompt(message, searchResults.map { it.first.text })
                        val responseText = LlamaBridge.generate(prompt) // Add generate() in JNI

                        //val contextString = searchResults.joinToString("\n---\n") { it.first.text }
                        if (responseText.isNullOrEmpty()) {
                            val botResponse =
                                ChatUiModel.Message(
                                    "There is a problem with the AI",
                                    ChatUiModel.Author.bot
                                )
                            _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                            _conversation.value += botResponse
                        } else {
                            responseText.let { response ->
                                val botResponse =
                                    ChatUiModel.Message(response, ChatUiModel.Author.bot)
                                _sideEffects.trySend(ChatBotSideEffects.OnMessageLoaded)
                                _conversation.value += botResponse
                            }
                        }
                    }
                }
            }
        }
    }

    fun buildPrompt(question: String, contextChunks: List<String>): String {
        val context = contextChunks.joinToString("\n- ") { it }
        return """
        Instruction: $question
        Context: 
        - $context
        Response:
    """.trimIndent()
    }

    fun onClearConversation() {
        screenModelScope.launch {
            _conversation.emit(emptyList())
        }
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
        val isFromMe: Boolean
            get() = author.id == MY_ID
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
    val isPrivacyMessageDisplayed: Boolean = false
)

sealed class ChatBotSideEffects {
    data object Initial : ChatBotSideEffects()
    data object OnLoaded : ChatBotSideEffects()
    data object OnMessageLoading : ChatBotSideEffects()
    data object OnMessageLoaded : ChatBotSideEffects()
    data object OnNoResults : ChatBotSideEffects()
    data object OnLoadError : ChatBotSideEffects()
}


