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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
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
import com.llamatik.library.platform.LlamaBridge.getModelPath
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder

// --- Safe wiring: if your ViewModel already has onModelChanged(String), it will be used.
// This extension is a no-op fallback so this file compiles either way.
@Suppress("unused")
fun ChatBotViewModel.onModelChanged(model: String) { /* no-op fallback */ }

class ChatBotTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()
        val embedFilePath = getModelPath(modelFileName = "nomic_embed_text_v1_5_Q4_0.gguf")
        val generatorFilePath = getModelPath(modelFileName = "gemma_3_270m_Q8_0.gguf")
        val isLoading = remember { mutableStateOf(false) }

        val viewModel = koinScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )

        val isDialogOpen = remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            viewModel.onStarted(embedFilePath, generatorFilePath)
            onDispose {
                viewModel.onDispose()
            }
        }

        val state by viewModel.state.collectAsState()
        val conversation = viewModel.conversation.collectAsState()
        SetupSideEffects(viewModel, isLoading)
        LlamatikTheme {
            ChatBotScreenView(
                viewModel,
                localization,
                isDialogOpen,
                conversation.value,
                isLoading,
                state
            )
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
        isLoading: MutableState<Boolean>
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
            }
        }
    }

    @Composable
    fun ChatBotScreenView(
        viewModel: ChatBotViewModel,
        localization: Localization,
        isDialogOpen: MutableState<Boolean>,
        conversation: List<ChatUiModel.Message>,
        isLoading: MutableState<Boolean>,
        state: ChatBotState,
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
                    ChatView(localization, viewModel, isDialogOpen, chatUiModel, isLoading, state)
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
        isDialogOpen: MutableState<Boolean>,
        chatUiModel: ChatUiModel,
        isLoading: MutableState<Boolean>,
        state: ChatBotState
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(chatUiModel.messages.size) {
            listState.animateScrollToItem(chatUiModel.messages.size)
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
                        ChatItem(chatUiModel.messages[item])
                        if (isLoading.value && item == chatUiModel.messages.size - 1) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).align(Alignment.End)
                            )
                        }
                    }
                }
            }
            ChatInputBox(viewModel = viewModel)
        }
    }

    @Composable
    fun ChatItem(message: ChatUiModel.Message) {
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
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                    .padding(16.dp)
            ) {
                Text(text = message.text)
            }
            Text(
                modifier = Modifier.align(if (message.isFromMe) Alignment.End else Alignment.Start),
                text = if (message.isFromMe) "\uD83D\uDEE9 Me" else "\uD83D\uDC68\uD83C\uDFFB\u200D✈\uFE0F Llamatik AI",
                style = Typography.get().titleSmall,
                color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
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
                    NewsCardSmall(state.latestNews[index], 240.dp, 200.dp) {
                        val item = state.latestNews[index]
                        viewModel.onOpenFeedItemDetail(
                            item.link
                        )
                    }
                    if (index == state.latestNews.size - 1) {
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * ChatGPT-style input with:
 * - left: model pill (dropdown)
 * - middle: rounded multiline text field
 * - right: send button (disabled when input is blank)
 *
 * IMPORTANT: Use rememberSaveable with TextFieldValue.Saver to avoid the crash.
 */
@Composable
fun ChatInputBox(
    viewModel: ChatBotViewModel,
    // Provide your real model list from VM/state when ready.
    availableModels: List<String> = listOf("GPT-4o mini", "Llama 3.1 8B", "Mistral Small"),
    initialModel: String = availableModels.firstOrNull() ?: "Model"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ✅ Fix: TextFieldValue must use an explicit Saver with rememberSaveable
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }
        var model by rememberSaveable { mutableStateOf(initialModel) }
        var showModelMenu by rememberSaveable { mutableStateOf(false) }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceDim)
            )

            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Model "pill"
                Box {
                    Surface(
                        onClick = { showModelMenu = true },
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(end = 8.dp)
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
                                text = model,
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

                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false }
                    ) {
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    model = m
                                    showModelMenu = false
                                    viewModel.onModelChanged(m) // safe no-op if not implemented in VM yet
                                }
                            )
                        }
                    }
                }

                // Rounded multiline TextField inside a Surface
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        placeholder = { Text("Message…") },
                        textStyle = Typography.get().bodyMedium,
                        singleLine = false,
                        minLines = 1,
                        maxLines = 6,
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {}
                    )
                }

                // Send button
                val canSend = input.text.isNotBlank()
                IconButton(
                    onClick = {
                        if (canSend) {
                            val message = input.text.trim()
                            input = TextFieldValue()
                            viewModel.onMessageSend(message)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
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
            }
        }
    }
}