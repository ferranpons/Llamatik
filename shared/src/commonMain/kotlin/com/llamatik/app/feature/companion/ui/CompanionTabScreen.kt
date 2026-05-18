package com.llamatik.app.feature.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.agent.AgentExecutor
import com.llamatik.app.feature.agent.ToolCallParser
import com.llamatik.app.feature.chatbot.ChatBotTabScreen
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotSideEffects
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.companion.CompanionMode
import com.llamatik.app.feature.companion.CompanionRepository
import com.llamatik.app.feature.companion.description
import com.llamatik.app.feature.companion.displayName
import com.llamatik.app.feature.companion.systemPrompt
import com.llamatik.app.feature.entitlement.EntitlementRepository
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import kotlinx.coroutines.launch
import org.koin.core.parameter.ParametersHolder
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

class CompanionTabScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val companionRepo = remember { KoinPlatform.getKoin().get<CompanionRepository>() }
        val hasChosen = remember { mutableStateOf(companionRepo.hasChosen()) }

        LlamatikTheme {
            if (!hasChosen.value) {
                PersonaPickerScreen(
                    onChosen = { mode ->
                        companionRepo.setCompanionMode(mode)
                        hasChosen.value = true
                    }
                )
            } else {
                val mode = companionRepo.getCompanionMode()
                CompanionChatScreen(
                    mode = mode,
                    onReset = {
                        companionRepo.reset()
                        hasChosen.value = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonaPickerScreen(onChosen: (CompanionMode) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Choose your companion",
                        style = Typography.get().titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick a personality for your companion. You can change this anytime from the toolbar.",
                style = Typography.get().bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            CompanionMode.entries.forEach { mode ->
                PersonaCard(
                    icon = mode.icon(),
                    name = mode.displayName(),
                    description = mode.description(),
                    onClick = { onChosen(mode) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PersonaCard(
    icon: ImageVector,
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = Typography.get().titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = Typography.get().bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = LlamatikIcons.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompanionChatScreen(mode: CompanionMode, onReset: () -> Unit) {
    val navigator = LocalNavigator.currentOrThrow
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val localization = getCurrentLocalization()

    val viewModel = navigator.koinNavigatorScreenModel<ChatBotViewModel>(
        qualifier = named("companion"),
        parameters = { ParametersHolder(mutableListOf(navigator, mode.systemPrompt()), false) }
    )

    LaunchedEffect(Unit) {
        viewModel.onStarted(navigator)
    }

    val state by viewModel.state.collectAsState()
    val conversation = viewModel.conversation.collectAsState()
    val isLoading = remember { mutableStateOf(false) }
    val showSuggestions = remember { mutableStateOf(false) }

    // Wire agent tool execution
    val entitlementRepo = remember { KoinPlatform.getKoin().get<EntitlementRepository>() }
    val agentExecutor = remember {
        val koin = KoinPlatform.getKoin()
        AgentExecutor(
            toolRegistry = koin.get(),
            permissionRepository = koin.get(),
            logRepository = koin.get(),
            entitlementRepository = entitlementRepo,
            requestConfirmation = { _, _ -> true }
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect: ChatBotSideEffects ->
            when (effect) {
                ChatBotSideEffects.OnMessageLoaded -> isLoading.value = false
                ChatBotSideEffects.OnMessageLoading -> isLoading.value = true
                is ChatBotSideEffects.OnToolCallDetected -> {
                    scope.launch {
                        val call = ToolCallParser.parse(effect.rawJson) ?: return@launch
                        val result = agentExecutor.execute(call)
                        val feedback = when (result) {
                            is com.llamatik.app.feature.agent.AgentToolResult.Success ->
                                "✓ ${result.outputSummary}"
                            is com.llamatik.app.feature.agent.AgentToolResult.Failure ->
                                "✗ ${result.errorMessage}"
                            com.llamatik.app.feature.agent.AgentToolResult.PermissionDenied ->
                                "Action not permitted."
                            com.llamatik.app.feature.agent.AgentToolResult.Unsupported ->
                                "This action is not supported on your device."
                        }
                        snackbarHostState.showSnackbar(feedback)
                    }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = mode.displayName(),
                            style = Typography.get().bodyLarge
                        )
                        Text(
                            text = "Companion",
                            style = Typography.get().labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onReset) {
                        Icon(
                            imageVector = LlamatikIcons.Person,
                            contentDescription = "Change companion"
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
                            contentDescription = "New conversation"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val chatUiModel = com.llamatik.app.feature.chatbot.viewmodel.ChatUiModel(
            messages = conversation.value,
            addressee = com.llamatik.app.feature.chatbot.viewmodel.ChatUiModel.Author.bot
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(top = 1.dp)
        ) {
            ChatBotTabScreen().ChatView(
                localization = localization,
                viewModel = viewModel,
                chatUiModel = chatUiModel,
                isLoading = isLoading,
                state = state,
                showSuggestions = showSuggestions,
            )
        }
    }
}

private fun CompanionMode.icon(): ImageVector = when (this) {
    CompanionMode.Friend -> LlamatikIcons.Community
    CompanionMode.Pet -> LlamatikIcons.Pets
    CompanionMode.Assistant -> LlamatikIcons.ChatBot
    CompanionMode.Secretary -> LlamatikIcons.Edit
}
