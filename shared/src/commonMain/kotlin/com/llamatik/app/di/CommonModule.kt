package com.llamatik.app.di

import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.llamatik.app.data.repositories.DownloadFileRepository
import com.llamatik.app.feature.agent.AgentActionLogRepository
import com.llamatik.app.feature.agent.AgentFeatureFlags
import com.llamatik.app.feature.agent.ChatAgentCoordinator
import com.llamatik.app.feature.agent.ToolPermissionRepository
import com.llamatik.app.feature.agent.ToolRegistry
import com.llamatik.app.feature.agent.tools.OpenAppTool
import com.llamatik.app.feature.agent.tools.ReminderTool
import com.llamatik.app.feature.agent.tools.SystemInteractionTool
import com.llamatik.app.feature.chatbot.repositories.ChatHistoryRepository
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository
import com.llamatik.app.feature.chatbot.usecases.GetModelsUseCase
import com.llamatik.app.feature.chatbot.usecases.ImportModelUseCase
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.chatgroup.ChatGroupRepository
import com.llamatik.app.feature.chathistory.ChatFolderScreenModel
import com.llamatik.app.feature.chathistory.ChatHistoryScreenModel
import com.llamatik.app.feature.chathistory.PendingSessionRepository
import com.llamatik.app.feature.companion.CompanionRepository
import com.llamatik.app.feature.debugmenu.repositories.GlobalAppSettingsRepository
import com.llamatik.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.llamatik.app.feature.news.repositories.NewsRepository
import com.llamatik.app.feature.news.usecases.GetAllNewsUseCase
import com.llamatik.app.feature.news.viewmodel.FeedItemDetailViewModel
import com.llamatik.app.feature.news.viewmodel.NewsFeedViewModel
import com.llamatik.app.feature.reviews.ReviewRequestManager
import com.llamatik.app.feature.reviews.ReviewService
import com.llamatik.app.feature.reviews.createReviewService
import com.llamatik.app.feature.webview.viewmodel.WebViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.AppDispatchersIO
import com.llamatik.app.platform.LlamatikEventTracker
import com.llamatik.app.platform.LlamatikFileAccessAdapter
import com.llamatik.app.platform.ModelPathResolverAdapter
import com.llamatik.app.platform.RagStorageAdapter
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.platform.RootSnackbarHostStateRepository
import com.llamatik.app.platform.ServiceClient
import com.llamatik.app.ui.screens.viewmodel.HomeScreenViewModel
import com.llamatik.app.ui.screens.viewmodel.SettingsViewModel
import com.llamatik.sdk.agent.action.Action
import com.llamatik.sdk.agent.action.ActionContext
import com.llamatik.sdk.agent.action.ActionResult
import com.llamatik.sdk.agent.capability.PlatformCapabilityProvider
import com.llamatik.sdk.agent.memory.AgentMemory
import com.llamatik.sdk.agent.permissions.PermissionManager
import com.llamatik.sdk.agent.registry.ActionRegistry
import com.llamatik.sdk.agent.registry.CapabilityRegistry
import com.llamatik.sdk.agent.registry.WorkflowRegistry
import com.llamatik.sdk.agent.runtime.AgentRuntime
import com.llamatik.sdk.assistant.Assistant
import com.llamatik.sdk.assistant.ModelPathResolver
import com.llamatik.sdk.assistant.RagStorage
import com.llamatik.sdk.http.LlamatikHttpClient
import com.llamatik.sdk.platform.LlamatikFileAccess
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.llamatik.sdk.agent.audit.AgentAuditRepository as SdkAuditRepository
import com.llamatik.sdk.agent.permissions.PermissionRepository as SdkPermissionRepository

val commonModule = module {
    factory { (navigator: Navigator) ->
        HomeScreenViewModel(navigator, get(), get(), get(), get())
    }
    factory {
        NewsFeedViewModel(get(), get(), get())
    }
    factory { (navigator: Navigator) ->
        SettingsViewModel(navigator, get())
    }

    factory {
        FeedItemDetailViewModel(get(), get())
    }

    factory {
        DebugMenuViewModel(get())
    }

    factory { (url: String) ->
        WebViewModel(url, get(), get())
    }

    factory { (navigator: Navigator) ->
        ChatBotViewModel(
            navigator = navigator,
            settings = get(),
            getAllNewsUseCase = get(),
            getModelsUseCase = get(),
            modelDownloadOrchestrator = get(),
            reviewRequestManager = get(),
            chatHistoryRepository = get(),
            ttsEngine = get(),
            chatAgentCoordinator = get(),
        )
    }

    factory(named("companion")) { (navigator: Navigator, systemPrompt: String) ->
        ChatBotViewModel(
            navigator = navigator,
            settings = get(),
            getAllNewsUseCase = get(),
            getModelsUseCase = get(),
            modelDownloadOrchestrator = get(),
            reviewRequestManager = get(),
            chatHistoryRepository = get(),
            ttsEngine = get(),
            systemPromptOverride = systemPrompt
        )
    }

    single { (navigator: Navigator, tabNavigator: TabNavigator) ->
        RootNavigatorRepository(navigator, tabNavigator)
    }

    single { (snackbarHostState: SnackbarHostState) ->
        RootSnackbarHostStateRepository(snackbarHostState)
    }

    single {
        LlamatikEventTracker()
    }

    singleOf(::GlobalAppSettingsRepository)
    factoryOf(::GetAllNewsUseCase)

    singleOf(::NewsRepository)

    factoryOf(::GetModelsUseCase)
    factoryOf(::ImportModelUseCase)
    singleOf(::ModelsRepository)

    singleOf(::DownloadFileRepository)

    single<ReviewService> { createReviewService() }
    singleOf(::ReviewRequestManager)

    single { ServiceClient }
    single { getCurrentLocalization() }

    // SDK layer — UI-agnostic business logic
    single<LlamatikFileAccess> { LlamatikFileAccessAdapter() }
    single { LlamatikHttpClient(ServiceClient.httpClient) }
    single {
        val loc = getCurrentLocalization()
        com.llamatik.sdk.model.ModelsRepository(
            fileAccess = get(),
            httpClient = get(),
            defaultSystemPrompt = loc.defaultSystemPrompt,
            smolVlm256SystemPrompt = loc.smolVLM256SystemPrompt,
            smolVlm500SystemPrompt = loc.smolVLM500SystemPrompt,
        )
    }
    single { com.llamatik.sdk.download.DefaultModelDownloadOrchestrator(get()) }
    single { com.llamatik.sdk.model.GetModelsUseCase(get()) }
    single { com.llamatik.sdk.model.ImportModelUseCase(get()) }
    single { com.llamatik.sdk.chat.ChatHistoryRepository(get()) }
    single<ModelPathResolver> { ModelPathResolverAdapter(get()) }
    single<RagStorage> { RagStorageAdapter() }
    factory { (scope: kotlinx.coroutines.CoroutineScope, systemPromptOverride: String?) ->
        Assistant(
            scope = scope,
            ioDispatcher = AppDispatchersIO,
            getModelsUseCase = get<com.llamatik.sdk.model.GetModelsUseCase>(),
            modelDownloadOrchestrator = get<com.llamatik.sdk.download.DefaultModelDownloadOrchestrator>(),
            chatHistoryRepository = get<com.llamatik.sdk.chat.ChatHistoryRepository>(),
            pathResolver = get(),
            ragStorage = get(),
            systemPromptOverride = systemPromptOverride,
        )
    }

    singleOf(::ChatHistoryRepository)
    singleOf(::PendingSessionRepository)
    factoryOf(::ChatHistoryScreenModel)
    factoryOf(::ChatFolderScreenModel)
    singleOf(::ChatGroupRepository)
    singleOf(::CompanionRepository)
    singleOf(::ToolPermissionRepository)
    singleOf(::AgentActionLogRepository)
    singleOf(::AgentFeatureFlags)
    single {
        ToolRegistry().also { registry ->
            registry.register(ReminderTool())
            registry.register(OpenAppTool())
            registry.register(SystemInteractionTool())
        }
    }

    // === sdk-agent layer ===

    single { SdkPermissionRepository(get()) }
    single { PermissionManager(repository = get<SdkPermissionRepository>()) }
    single { AgentMemory(get()) }
    single { SdkAuditRepository(get()) }

    single {
        val platformActions = get<List<Action>>(named("platformActions"))
        ActionRegistry().also { registry ->
            platformActions.forEach { registry.registerAction(it) }
        }
    }

    single {
        val actionRegistry = get<ActionRegistry>()
        fun executor(toolId: String): suspend (ActionContext) -> ActionResult = { ctx ->
            actionRegistry.get(toolId)?.execute(ctx) ?: ActionResult.Unsupported
        }
        fun supported(toolId: String): () -> Boolean = {
            actionRegistry.get(toolId)?.isSupported() ?: false
        }
        com.llamatik.sdk.agent.registry.ToolRegistry().also { registry ->
            registry.registerTool(com.llamatik.sdk.agent.builtintools.CreateCalendarEventTool(executor("calendar.create_event"), supported("calendar.create_event")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.CreateReminderTool(executor("reminder.create"), supported("reminder.create")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.OpenAppTool(executor("apps.open"), supported("apps.open")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.OpenUrlTool(executor("browser.open_url"), supported("browser.open_url")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.ClipboardTool(executor("clipboard.copy"), supported("clipboard.copy")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.ShareTool(executor("share.content"), supported("share.content")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.NotificationTool(executor("notifications.post"), supported("notifications.post")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.SearchContactsTool(executor("contacts.search"), supported("contacts.search")))
            registry.registerTool(com.llamatik.sdk.agent.builtintools.OpenSettingsTool(executor("settings.open"), supported("settings.open")))
        }
    }

    single { CapabilityRegistry() }
    single { WorkflowRegistry() }

    single {
        AgentRuntime.Builder()
            .toolRegistry(get<com.llamatik.sdk.agent.registry.ToolRegistry>())
            .actionRegistry(get<ActionRegistry>())
            .capabilityRegistry(get<CapabilityRegistry>())
            .workflowRegistry(get<WorkflowRegistry>())
            .permissionManager(get<PermissionManager>())
            .agentMemory(get<AgentMemory>())
            .auditRepository(get<SdkAuditRepository>())
            .capabilityProvider(get<PlatformCapabilityProvider>())
            .platformId("llamatik")
            .build()
    }

    single {
        ChatAgentCoordinator(
            agentRuntime = get(),
            agentFeatureFlags = get(),
        )
    }
}
