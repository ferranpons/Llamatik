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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ModelSelectorBottomSheet
import com.llamatik.app.feature.chatbot.ui.ModelSettingsBottomSheet
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotSideEffects
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotState
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.chatbot.viewmodel.ChatUiModel
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.ui.components.LlamatikDialog
import com.llamatik.app.ui.components.NewsCardSmall
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder


class ChatBotTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()
        val isLoading = remember { mutableStateOf(false) }
        val showSuggestions = remember { mutableStateOf(true) }
        val showSettingsSheet = remember { mutableStateOf(false) }
        val showModelSelectorSheet = remember { mutableStateOf(false) }

        val loadingEmbedModelName = remember { mutableStateOf<String?>(null) }
        val loadingGenerateModelName = remember { mutableStateOf<String?>(null) }

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
        )
        LlamatikTheme {
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
            if (showSettingsSheet.value) {
                ModelSettingsBottomSheet {
                    showSettingsSheet.value = false
                }
            }
            if (showModelSelectorSheet.value) {
                ModelSelectorBottomSheet(
                    downloadingMap = downloadingMap,
                    progressMap = progressMap,
                    selectedEmbedModelName = state.selectedEmbedModelName,
                    selectedGenerateModelName = state.selectedGenerateModelName,
                    embedModels = state.embedModels,
                    generateModels = state.generateModels,
                    loadingEmbedModelName = loadingEmbedModelName.value,          // NEW
                    loadingGenerateModelName = loadingGenerateModelName.value,
                    onEmbedModelSelectedClicked = { model ->
                        loadingEmbedModelName.value = model.name
                        viewModel.onEmbedModelSelected(model)
                    },
                    onGenerateModelSelectedClicked = { model ->
                        loadingGenerateModelName.value = model.name
                        viewModel.onGenerateModelSelected(model)
                    },
                    onDownloadModelClicked = { model ->
                        viewModel.onDownloadModel(model)
                    }
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

    @Composable
    private fun SetupSideEffects(
        viewModel: ChatBotViewModel,
        isLoading: MutableState<Boolean>,
        showModelSelectorSheet: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
        loadingEmbedModelName: MutableState<String?>,
        loadingGenerateModelName: MutableState<String?>,
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

                ChatBotSideEffects.OnSettingsChanged -> {
                    showSettingsSheet.value = false
                }

                ChatBotSideEffects.OnEmbedModelLoadError -> {
                    loadingEmbedModelName.value = null
                }
                ChatBotSideEffects.OnGenerateModelLoadError -> {
                    loadingGenerateModelName.value = null
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
                                    showSuggestions.value = true
                                    viewModel.onClearConversation()
                                }
                            ) {
                                Icon(
                                    imageVector = LlamatikIcons.Delete,
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
                        modifier = Modifier.fillMaxWidth().height(1.dp)
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
                modifier = Modifier.fillMaxWidth().height(140.dp)
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
        val listState = rememberLazyListState()
        LaunchedEffect(chatUiModel.messages.size) {
            if (chatUiModel.messages.isNotEmpty()) {
                listState.animateScrollToItem(chatUiModel.messages.size -1)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (chatUiModel.messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChatHeader()
                    LatestNewsCarousel(viewModel, localization, state)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(chatUiModel.messages.size) { item ->
                        ChatItem(
                            message = chatUiModel.messages[item],
                            showLoading = isLoading.value && item == chatUiModel.messages.size - 1
                        )
                    }
                }
            }
            ChatInputBox(
                state = state,
                viewModel = viewModel,
                showSuggestions = showSuggestions,
                onOpenModelSelector = { showModelSelectorSheet.value = true },
                onOpenSettings = { showSettingsSheet.value = true }
            )
        }
    }

    @Composable
    fun ChatItem(message: ChatUiModel.Message, showLoading: Boolean) {
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
                    text = if (message.isFromMe) "\uD83D\uDEE9 Me" else "\uD83D\uDC68\uD83C\uDFFB\u200D✈\uFE0F Llamatik AI",
                    style = Typography.get().titleSmall,
                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
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
                    text = "View All",
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
}

@Composable
fun ChatInputBox(
    state: ChatBotState,
    viewModel: ChatBotViewModel,
    showSuggestions: MutableState<Boolean>,
    suggestions: List<String> = listOf(
        "Summarize the latest news",
        "Create a receipt",
        "Draft a polite reply"
    ),
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }

        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            if (showSuggestions.value && suggestions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(suggestions.size) { index ->
                        val hint = suggestions[index]
                        if (index == 0) {
                            Spacer(modifier = Modifier.size(16.dp))
                        }

                        Surface(
                            onClick = {
                                input = TextFieldValue(hint)
                                val message = input.text.trim()
                                if (message.isNotEmpty()) {
                                    input = TextFieldValue()
                                    viewModel.onMessageSendDirect(message)
                                    showSuggestions.value = false
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = hint,
                                style = Typography.get().labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (index == suggestions.size - 1) {
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val canSend = input.text.isNotBlank()
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    placeholder = { Text("Ask me something…") },
                    textStyle = Typography.get().bodyMedium,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { keyboardController?.hide() },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    val message = input.text.trim()
                                    input = TextFieldValue()
                                    viewModel.onMessageSendDirect(message)
                                    showSuggestions.value = false
                                    keyboardController?.hide()
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = LlamatikIcons.Send,
                                contentDescription = "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                )
            }

            GenerateModelSelector(
                selectedModelName = state.selectedGenerateModelName,
                onOpenModelSelector = onOpenModelSelector,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
fun GenerateModelSelector(
    selectedModelName: String?,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onOpenModelSelector,
                    shape = RoundedCornerShape(999.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = selectedModelName ?: "no model selected",
                            style = Typography.get().labelMedium
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
/*
                Spacer(modifier = Modifier.size(8.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(24.dp),
                    content = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
 */
            }
        }
    }
}
