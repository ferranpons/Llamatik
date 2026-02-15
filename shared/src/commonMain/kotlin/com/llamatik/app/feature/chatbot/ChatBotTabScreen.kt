package com.llamatik.app.feature.chatbot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ChatHistoryBottomSheet
import com.llamatik.app.feature.chatbot.ui.ChatInputBox
import com.llamatik.app.feature.chatbot.ui.ModelSelectorBottomSheet
import com.llamatik.app.feature.chatbot.ui.ModelSettingsBottomSheet
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotSideEffects
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotState
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.chatbot.viewmodel.ChatUiModel
import com.llamatik.app.localization.AvailableLanguages
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLanguage
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.permissions.rememberAudioPermissionRequester
import com.llamatik.app.permissions.rememberNotificationPermissionRequester
import com.llamatik.app.platform.AudioPaths
import com.llamatik.app.platform.AudioRecorder
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.ui.components.LlamatikDialog
import com.llamatik.app.ui.components.NewsCardSmall
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import com.llamatik.library.platform.WhisperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder

class ChatBotTabScreen : Screen {

    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        val isLoading = remember { mutableStateOf(false) }
        val showSuggestions = remember { mutableStateOf(true) }
        val showSettingsSheet = remember { mutableStateOf(false) }
        val showModelSelectorSheet = remember { mutableStateOf(false) }
        val notificationPermissionRequester = rememberNotificationPermissionRequester()

        val loadingEmbedModelName = remember { mutableStateOf<String?>(null) }
        val loadingGenerateModelName = remember { mutableStateOf<String?>(null) }
        val loadingSttModelName = remember { mutableStateOf<String?>(null) }

        val viewModel = koinScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )

        val downloadStates by viewModel.downloadStates.collectAsState()
        val downloadingMap = downloadStates.mapValues { it.value.inProgress }
        val progressMap = downloadStates.mapValues { it.value.progress.coerceIn(0, 100) / 100f }

        val isDialogOpen = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.onStarted(navigator)
        }

        val state by viewModel.state.collectAsState()
        val conversation = viewModel.conversation.collectAsState()

        SetupSideEffects(
            viewModel = viewModel,
            isLoading = isLoading,
            showSettingsSheet = showSettingsSheet,
            showModelSelectorSheet = showModelSelectorSheet,
            loadingEmbedModelName = loadingEmbedModelName,
            loadingGenerateModelName = loadingGenerateModelName,
            loadingSttModelName = loadingSttModelName,
        )

        LlamatikTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                ChatBotScreenView(
                    viewModel,
                    localization,
                    conversation.value,
                    isLoading,
                    state,
                    showSuggestions,
                    showSettingsSheet,
                    showModelSelectorSheet
                )

                if (state.isInitialSetup) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = localization.settingUpLlamatik,
                                style = Typography.get().titleMedium
                            )
                            Text(
                                text = localization.downloadingMainModels,
                                style = Typography.get().bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            if (state.initialSetupProgress > 0) {
                                Text(
                                    text = "${localization.progress}: ${
                                        state.initialSetupProgress.coerceIn(0, 100)
                                    }%",
                                    style = Typography.get().labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (showSettingsSheet.value) {
                ModelSettingsBottomSheet(
                    current = state.generateSettings,
                    onApply = { viewModel.onGenerateSettingsApplied(it) },
                    onDismiss = { showSettingsSheet.value = false }
                )
            }
            if (showModelSelectorSheet.value) {
                ModelSelectorBottomSheet(
                    downloadingMap = downloadingMap,
                    progressMap = progressMap,

                    selectedEmbedModelName = state.selectedEmbedModelName,
                    selectedGenerateModelName = state.selectedGenerateModelName,
                    selectedSttModelName = state.selectedSttModelName,

                    embedModels = state.embedModels,
                    generateModels = state.generateModels,
                    sttModels = state.sttModels,

                    loadingEmbedModelName = loadingEmbedModelName.value,
                    loadingGenerateModelName = loadingGenerateModelName.value,
                    loadingSttModelName = loadingSttModelName.value,

                    onEmbedModelSelectedClicked = { model ->
                        loadingEmbedModelName.value = model.name
                        viewModel.onEmbedModelSelected(model)
                    },
                    onGenerateModelSelectedClicked = { model ->
                        loadingGenerateModelName.value = model.name
                        viewModel.onGenerateModelSelected(model)
                    },
                    onSttModelSelectedClicked = { model ->
                        loadingSttModelName.value = model.name
                        viewModel.onSttModelSelected(model)
                    },

                    onDownloadModelClicked = { model ->
                        notificationPermissionRequester.requestAndRun(
                            onGranted = { viewModel.onDownloadModel(model) },
                        )
                    },
                    onDeleteModelClicked = { model ->
                        viewModel.onDeleteModel(model)
                    },
                    onCancelDownloadClicked = { model ->
                        viewModel.onCancelDownload(model)
                    },
                ) {
                    showModelSelectorSheet.value = false
                }
            }
            if (isDialogOpen.value) {
                LlamatikDialog(
                    message = getCurrentLocalization().featureNotAvailableMessage,
                    onDismissRequest = { isDialogOpen.value = false },
                    onConfirmation = { isDialogOpen.value = false },
                    imageDescription = "",
                    dismissButtonText = localization.dismiss
                )
            }
        }
    }

    private fun whisperLanguageCode(): String? {
        // Whisper expects ISO 639-1 language codes (e.g., "en", "es", "fr", "de", "it", "ru", "zh")
        // Return null to allow auto-detection.
        return when (getCurrentLanguage()) {
            AvailableLanguages.EN -> "en"
            AvailableLanguages.ES -> "es"
            AvailableLanguages.IT -> "it"
            AvailableLanguages.FR -> "fr"
            AvailableLanguages.DE -> "de"
            AvailableLanguages.RU -> "ru"
            AvailableLanguages.CN -> "zh"
        }
    }

    @Composable
    private fun SetupSideEffects(
        viewModel: ChatBotViewModel,
        isLoading: MutableState<Boolean>,
        showModelSelectorSheet: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
        loadingEmbedModelName: MutableState<String?>,
        loadingGenerateModelName: MutableState<String?>,
        loadingSttModelName: MutableState<String?>,
    ) {
        val sideEffects = viewModel.sideEffects.collectAsState(ChatBotSideEffects.Initial)
        sideEffects.value.apply {
            when (this) {
                ChatBotSideEffects.Initial -> {}
                ChatBotSideEffects.OnLoadError -> {}
                is ChatBotSideEffects.OnLoaded -> {}
                ChatBotSideEffects.OnMessageLoaded -> {
                    isLoading.value = false
                }

                ChatBotSideEffects.OnMessageLoading -> {
                    isLoading.value = true
                }

                ChatBotSideEffects.OnNoResults -> {
                    isLoading.value = false
                }

                ChatBotSideEffects.ScrollToBottom -> {}

                ChatBotSideEffects.OnEmbedModelLoaded -> {
                    loadingEmbedModelName.value = null
                    showModelSelectorSheet.value = false
                }

                ChatBotSideEffects.OnGenerateModelLoaded -> {
                    loadingGenerateModelName.value = null
                    showModelSelectorSheet.value = false
                }

                ChatBotSideEffects.OnSttModelLoaded -> {
                    loadingSttModelName.value = null
                    showModelSelectorSheet.value = false
                }

                ChatBotSideEffects.OnSettingsChanged -> {
                    showSettingsSheet.value = false
                }

                ChatBotSideEffects.OnEmbedModelLoadError -> {
                    loadingEmbedModelName.value = null
                }

                ChatBotSideEffects.OnGenerateModelLoadError -> {
                    loadingGenerateModelName.value = null
                }

                ChatBotSideEffects.OnSttModelLoadError -> {
                    loadingSttModelName.value = null
                }
            }
        }
    }

    @Composable
    fun ChatBotScreenView(
        viewModel: ChatBotViewModel,
        localization: Localization,
        conversation: List<ChatUiModel.Message>,
        isLoading: MutableState<Boolean>,
        state: ChatBotState,
        showSuggestions: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
        showModelSelectorSheet: MutableState<Boolean>,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), propagateMinConstraints = true) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                            ) {
                                Text(
                                    text = state.greeting,
                                    style = Typography.get().labelSmall
                                )
                                Text(
                                    text = state.header,
                                    style = Typography.get().bodyLarge
                                )
                            }
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        actions = {
                            IconButton(
                                onClick = {
                                    viewModel.onShowPrivacyScreen()
                                }
                            ) {
                                Icon(
                                    imageVector = LlamatikIcons.Info,
                                    contentDescription = "Info about Llamatik AI"
                                )
                            }
                            IconButton(
                                onClick = {
                                    showSuggestions.value = false
                                    viewModel.onToggleTemporaryChat()
                                }
                            ) {
                                Icon(
                                    imageVector = LlamatikIcons.TemporaryChat,
                                    contentDescription = localization.temporaryChat,
                                    tint = if (state.isTemporaryChat)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    showSuggestions.value = true
                                    viewModel.onClearConversation()
                                }
                            ) {
                                Icon(
                                    imageVector = LlamatikIcons.NewConversation,
                                    contentDescription = "Delete Conversation"
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.surfaceDim)
                    )

                    val chatUiModel = ChatUiModel(
                        messages = conversation,
                        addressee = ChatUiModel.Author.bot
                    )
                    ChatView(
                        localization,
                        viewModel,
                        chatUiModel,
                        isLoading,
                        state,
                        showSuggestions,
                        showSettingsSheet,
                        showModelSelectorSheet
                    )
                }
            }
        }
    }

    @Composable
    fun ChatHeader() {
        var sizeImage by remember { mutableStateOf(IntSize.Zero) }
        val gradient = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                MaterialTheme.colorScheme.background
            ),
            startY = sizeImage.height.toFloat() / 3,
            endY = sizeImage.height.toFloat()
        )

        Box {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .onGloballyPositioned {
                        sizeImage = it.size
                    },
                contentScale = ContentScale.FillWidth,
                painter = painterResource(Res.drawable.a_pair_of_llamas_in_a_field_with_clouds_and_mounta),
                contentDescription = null
            )
            Box(modifier = Modifier.matchParentSize().background(gradient))
        }
    }

    @Composable
    fun ChatView(
        localization: Localization,
        viewModel: ChatBotViewModel,
        chatUiModel: ChatUiModel,
        isLoading: MutableState<Boolean>,
        state: ChatBotState,
        showSuggestions: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
        showModelSelectorSheet: MutableState<Boolean>,
    ) {
        val showChatHistorySheet = remember { mutableStateOf(false) }
        val audioPermissionRequester = rememberAudioPermissionRequester()
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
        val speakingMessageKey = remember { mutableStateOf<String?>(null) }

        val listState = rememberLazyListState()
        LaunchedEffect(chatUiModel.messages.size) {
            if (chatUiModel.messages.isNotEmpty()) {
                listState.animateScrollToItem(chatUiModel.messages.size - 1)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (chatUiModel.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChatHeader()
                    if (state.isTemporaryChat) {
                        TemporaryChatIndicator(localization = localization)
                    }
                    LatestNewsCarousel(viewModel, localization, state)
                }
            } else {
                if (state.isTemporaryChat) {
                    TemporaryChatIndicator(localization = localization)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(chatUiModel.messages.size) { item ->
                        val messageKey = "msg_$item"
                        ChatItem(
                            localization = localization,
                            message = chatUiModel.messages[item],
                            showLoading = isLoading.value && item == chatUiModel.messages.size - 1,
                            isSpeaking = speakingMessageKey.value == messageKey,
                            onSpeak = { text ->
                                speakingMessageKey.value = messageKey
                                viewModel.onSpeak(text)
                            },
                            onStop = {
                                speakingMessageKey.value = null
                                viewModel.onStopSpeaking()
                            }
                        )
                    }
                }
            }

            val scope = rememberCoroutineScope()
            val recorder = remember { AudioRecorder() }
            var isListening by remember { mutableStateOf(false) }
            var isTranscribing by remember { mutableStateOf(false) }

            val whisperReady = state.isSttModelLoaded
            val tempWavPath = AudioPaths.tempWavPath()

            ChatInputBox(
                localization = localization,
                state = state,
                viewModel = viewModel,
                showSuggestions = showSuggestions,
                input = input,
                onInputChange = { input = it },
                onOpenChatHistory = { showChatHistorySheet.value = true },
                onOpenModelSelector = { showModelSelectorSheet.value = true },
                onOpenSettings = { showSettingsSheet.value = true },
                isListening = isListening,
                isTranscribing = isTranscribing,
                onMicClick = {
                    audioPermissionRequester.requestAndRun(
                        onGranted = {
                            if (!whisperReady || isTranscribing) return@requestAndRun

                            scope.launch {
                                try {
                                    if (!recorder.isRecording) {
                                        // Start recording
                                        showSuggestions.value = false
                                        recorder.start(tempWavPath)
                                        isListening = true
                                    } else {
                                        // Stop recording and transcribe
                                        isListening = false
                                        isTranscribing = true

                                        val wavPath = recorder.stop()

                                        val text = withContext(Dispatchers.Default) {
                                            val lang = whisperLanguageCode()
                                            WhisperBridge.transcribeWav(wavPath, language = lang).trim()
                                        }

                                        if (text.isNotBlank()) {
                                            val newText =
                                                if (input.text.isBlank()) text
                                                else "${input.text.trimEnd()} $text"
                                            input = input.copy(text = newText)
                                        }
                                    }
                                } catch (t: Throwable) {
                                    isListening = false
                                    isTranscribing = false
                                    runCatching { if (recorder.isRecording) recorder.stop() }
                                    println("[VoiceInput] Error: ${t.message}\n${t.stackTraceToString()}")
                                } finally {
                                    isTranscribing = false
                                }
                            }
                        }
                    )
                }
            )

            if (showChatHistorySheet.value) {
                ChatHistoryBottomSheet(
                    localization = localization,
                    sessions = state.chatSessions,
                    onLoad = { id ->
                        viewModel.onLoadChatSession(id)
                        showSuggestions.value = false
                        showChatHistorySheet.value = false
                    },
                    onDelete = { id ->
                        viewModel.onDeleteChatSession(id)
                    },
                    onDismiss = { showChatHistorySheet.value = false }
                )
            }
        }
    }

    @Composable
    fun ChatItem(
        localization: Localization,
        message: ChatUiModel.Message,
        showLoading: Boolean,
        isSpeaking: Boolean,
        onSpeak: (String) -> Unit,
        onStop: () -> Unit,
    ) {
        val clipboard = LocalClipboardManager.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (message.isFromMe) 48.dp else 16.dp,
                    end = if (message.isFromMe) 16.dp else 48.dp,
                    bottom = 8.dp,
                    top = 8.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .align(if (message.isFromMe) Alignment.End else Alignment.Start)
                    .clip(
                        RoundedCornerShape(
                            topStart = 48f,
                            topEnd = 48f,
                            bottomStart = if (message.isFromMe) 48f else 0f,
                            bottomEnd = if (message.isFromMe) 0f else 48f
                        )
                    )
                    .background(
                        if (message.isFromMe)
                            MaterialTheme.colorScheme.inversePrimary
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                    .padding(16.dp)
            ) {
                Text(text = message.text)
            }
            Row(
                modifier = Modifier.align(if (message.isFromMe) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = if (message.isFromMe) localization.me else "Llamatik AI",
                    style = Typography.get().titleSmall,
                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.size(8.dp))

                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.text)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = LlamatikIcons.Copy,
                        contentDescription = localization.copy
                    )
                }

                IconButton(
                    onClick = {
                        if (isSpeaking) onStop() else onSpeak(message.text)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) LlamatikIcons.Stop else LlamatikIcons.Sound,
                        contentDescription = if (isSpeaking) {
                            localization.stop
                        } else {
                            localization.speak
                        }
                    )
                }

                if (showLoading) {
                    Spacer(modifier = Modifier.size(8.dp))
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }

    @Composable
    fun LatestNewsCarousel(
        viewModel: ChatBotViewModel,
        localization: Localization,
        state: ChatBotState
    ) {
        if (state.latestNews.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = localization.homeLastestNews,
                    style = Typography.get().titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                )
                Text(
                    text = localization.viewAll,
                    style = Typography.get().titleMedium,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .clickable {
                            viewModel.onOpenNewsClicked()
                        }
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.latestNews.size) { index ->
                    val item = state.latestNews[index]
                    NewsCardSmall(
                        feedItem = item,
                        width = 240.dp,
                        height = 200.dp
                    ) {
                        viewModel.onOpenFeedItemDetail(item.link)
                    }
                    if (index == state.latestNews.size - 1) {
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun TemporaryChatIndicator(
        localization: Localization,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = LlamatikIcons.TemporaryChat,
                    contentDescription = localization.temporaryChat,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.size(8.dp))

                Column {
                    Text(
                        text = localization.temporaryChatExplanation,
                        style = Typography.get().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
