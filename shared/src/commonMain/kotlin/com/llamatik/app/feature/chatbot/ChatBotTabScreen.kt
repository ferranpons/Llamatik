package com.llamatik.app.feature.chatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.createRefs
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotSideEffects
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.chatbot.viewmodel.ChatUiModel
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.components.LlamatikDialog
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import com.llamatik.library.platform.LlamaBridge.getModelPath
import org.koin.core.parameter.ParametersHolder

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

        DisposableEffect(key) {
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
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), propagateMinConstraints = true) {

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Llamatik AI PREVIEW",
                                style = Typography.get().titleMedium
                            )
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
                        .padding(paddingValues).padding(bottom = 80.dp)
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
                    ChatView(localization, viewModel, isDialogOpen, chatUiModel, isLoading)
                }
            }
        }
    }

    @Composable
    fun ChatView(
        localization: Localization,
        viewModel: ChatBotViewModel,
        isDialogOpen: MutableState<Boolean>,
        chatUiModel: ChatUiModel,
        isLoading: MutableState<Boolean>
    ) {
        val (messages, chatBox) = createRefs()
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
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "\uD83D\uDEEB Ok, let's start! How can I help you?",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        text = "Here are some hints:\n" +
                                "\n" +
                                "How can I start Viggen?\n" +
                                "Describe the F-18 system weapons.\n" +
                                "Can I use Mavericks on the F-16?\n" +
                                "\n",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
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
            ChatInputBox(viewModel)
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
                    .background(if (message.isFromMe) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.surfaceContainer)
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
}

@Composable
fun ChatInputBox(
    viewModel: ChatBotViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val input = remember { mutableStateOf(TextFieldValue()) }

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Spacer(
                modifier = Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceDim)
            )
            TextField(
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val message = input.value.text
                            input.value = TextFieldValue()
                            viewModel.onMessageSend(message)
                        },
                        modifier = Modifier.size(56.dp).padding(8.dp)
                            .clip(shape = RoundedCornerShape(16.dp))
                    ) {
                        Box {
                            Icon(
                                imageVector = LlamatikIcons.Send,
                                contentDescription = "Send",
                            )
                        }
                    }
                },
                label = { Text(text = "Ask a question...") },
                value = input.value,
                onValueChange = { input.value = it },
                textStyle = Typography.get().bodyMedium,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}
