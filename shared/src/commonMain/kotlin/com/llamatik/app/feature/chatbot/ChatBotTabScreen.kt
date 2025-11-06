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
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
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
import com.llamatik.app.feature.chatbot.model.LlamaModel
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
import korlibs.util.format
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder
import kotlin.math.roundToInt


class ChatBotTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()
        val embedFilePath = getModelPath(modelFileName = "nomic_embed_text_v1_5_Q4_0.gguf")
        val generatorFilePath = getModelPath(modelFileName = "gemma_3_270m_Q8_0.gguf")
        val isLoading = remember { mutableStateOf(false) }
        val showSuggestions = remember { mutableStateOf(true) }
        val showSettingsSheet = remember { mutableStateOf(false) }

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
                state,
                showSuggestions,
                showSettingsSheet
            )
            if (showSettingsSheet.value) {
                SettingsBottomSheet(
                    models = state.models,
                    onDismiss = { showSettingsSheet.value = false },
                    onModelSelected = { fileName ->
                        // Resolve path and initialize generator model
                        //val path = getModelPath(modelFileName = fileName)
                        //initGenerateModel(path)
                        showSettingsSheet.value = false
                    }
                )
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
        showSuggestions: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
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
                        isDialogOpen,
                        chatUiModel,
                        isLoading,
                        state,
                        showSuggestions,
                        showSettingsSheet
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
        isDialogOpen: MutableState<Boolean>,
        chatUiModel: ChatUiModel,
        isLoading: MutableState<Boolean>,
        state: ChatBotState,
        showSuggestions: MutableState<Boolean>,
        showSettingsSheet: MutableState<Boolean>,
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
            ChatInputBox(
                viewModel = viewModel,
                showSuggestions = showSuggestions,
                onOpenSettings = { showSettingsSheet.value = true }
            )
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

@Composable
fun ChatInputBox(
    viewModel: ChatBotViewModel,
    showSuggestions: MutableState<Boolean>,
    availableModels: List<String> = listOf("GPT-4o mini", "Llama 3.1 8B", "Mistral Small"),
    initialModel: String = availableModels.firstOrNull() ?: "Model",
    suggestions: List<String> = listOf(
        "Summarize the latest news",
        "Create a receipt",
        "Draft a polite reply"
    ),
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
            horizontalAlignment = Alignment.Start, // pill aligned left
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
                                    viewModel.onMessageSend(message)
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
                                    viewModel.onMessageSend(message)
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

            ModelSelector(
                viewModel,
                initialModel,
                availableModels,
                onOpenSettings
            )
        }
    }
}

@Composable
fun ModelSelector(
    viewModel: ChatBotViewModel,
    initialModel: String,
    availableModels: List<String>,
    onOpenSettings: () -> Unit,
) {
    var model by rememberSaveable { mutableStateOf(initialModel) }
    var showModelMenu by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { showModelMenu = true },
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
                            //viewModel.onModelChanged(m) // safe no-op if not implemented yet
                        }
                    )
                }
            }
        }
    }
}

data class GenerateSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 256,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f
)

private val GenerateSettingsSaver: Saver<GenerateSettings, Any> = listSaver(
    save = { gs ->
        listOf(gs.temperature, gs.maxTokens, gs.topP, gs.topK, gs.repeatPenalty)
    },
    restore = { list ->
        GenerateSettings(
            temperature   = (list[0] as Number).toFloat(),
            maxTokens     = (list[1] as Number).toInt(),
            topP          = (list[2] as Number).toFloat(),
            topK          = (list[3] as Number).toInt(),
            repeatPenalty = (list[4] as Number).toFloat()
        )
    }
)

// Keep these in memory for now; you can bind to Settings later.
@Composable
private fun rememberGenerateSettingsState(): MutableState<GenerateSettings> {
    return rememberSaveable(stateSaver = GenerateSettingsSaver) {
        mutableStateOf(GenerateSettings())
    }
}

private fun isModelInstalled(fileName: String): Boolean {
    return false
}

@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    onModelSelected: (fileName: String) -> Unit,
    models: List<LlamaModel>
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state
    val generateSettings = rememberGenerateSettingsState()

    // track download progress by fileName
    val progressMap = remember { mutableStateMapOf<String, Float>() }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val jobs = remember { mutableStateMapOf<String, Job?>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {

            Text(
                text = "Models",
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            models.forEach { model ->
                val installed = remember(model.fileName) { mutableStateOf(isModelInstalled(model.fileName)) }
                val isDownloading = downloadingMap[model.fileName] == true
                val pct = progressMap[model.fileName] ?: 0f

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (installed.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(model.name, style = Typography.get().titleMedium)
                                Text("${model.sizeMb} MB • ${model.fileName}", style = Typography.get().labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (installed.value) {
                                FilledTonalButton(
                                    onClick = { onModelSelected(model.fileName) },
                                    content = { Text("Select") }
                                )
                            } else {
                                if (isDownloading) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Downloading… ${(pct * 100).roundToInt()}%", style = Typography.get().labelSmall)
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { pct },
                                            modifier = Modifier.width(140.dp).progressSemantics()
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            downloadingMap[model.fileName] = true
                                            progressMap[model.fileName] = 0f
                                            jobs[model.fileName] = scope.launch {
                                                try {
                                                    /*
                                                    downloadModelFile(model) { p ->
                                                        progressMap[model.fileName] = p.coerceIn(0f, 1f)
                                                    }*/
                                                    installed.value = true
                                                } catch (t: Throwable) {
                                                    // You can surface a snackbar/toast here
                                                } finally {
                                                    downloadingMap[model.fileName] = false
                                                }
                                            }
                                        }
                                    ) { Text("Download") }
                                }
                            }
                        }

                        if (isDownloading || !installed.value) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Generation Settings",
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(4.dp))

            ParamSlider(
                label = "Temperature",
                value = generateSettings.value.temperature,
                valueRange = 0.0f..2.0f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { generateSettings.value = generateSettings.value.copy(temperature = it) }
            )

            ParamIntField(
                label = "Max tokens",
                value = generateSettings.value.maxTokens,
                min = 16,
                max = 8192,
                onChange = { generateSettings.value = generateSettings.value.copy(maxTokens = it) }
            )

            ParamSlider(
                label = "Top-p",
                value = generateSettings.value.topP,
                valueRange = 0.0f..1.0f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { generateSettings.value = generateSettings.value.copy(topP = it) }
            )

            ParamIntField(
                label = "Top-k",
                value = generateSettings.value.topK,
                min = 1,
                max = 1000,
                onChange = { generateSettings.value = generateSettings.value.copy(topK = it) }
            )

            ParamSlider(
                label = "Repeat penalty",
                value = generateSettings.value.repeatPenalty,
                valueRange = 0.8f..2.0f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { generateSettings.value = generateSettings.value.copy(repeatPenalty = it) }
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // TODO: thread these into your actual llama.cpp call site.
                        // Example: ChatRunner / LlamaBridge could accept these:
                        // LlamaBridge.updateGenerateParams(generateSettings.value.toBridgeParams())
                        onDismiss()
                    }
                ) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// --- Small UI helpers --------------------------------------------------------

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Typography.get().labelLarge)
            Text(format(value), style = Typography.get().labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = { onChange((it / step).roundToInt() * step) },
            valueRange = valueRange
        )
    }
}

@Composable
private fun ParamIntField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() }
                val v = text.toIntOrNull()
                if (v != null) onChange(v.coerceIn(min, max))
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${min}–$max", style = Typography.get().labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}