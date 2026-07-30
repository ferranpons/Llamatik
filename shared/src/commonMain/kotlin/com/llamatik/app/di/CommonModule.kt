package com.llamatik.app.di

import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.llamatik.app.data.repositories.DownloadFileRepository
import com.llamatik.app.feature.agent.AgentActionLogRepository
import com.llamatik.app.feature.agent.AgentFeatureFlags
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
import com.llamatik.sdk.assistant.Assistant
import com.llamatik.sdk.assistant.ModelPathResolver
import com.llamatik.sdk.assistant.RagStorage
import com.llamatik.sdk.http.LlamatikHttpClient
import com.llamatik.sdk.platform.LlamatikFileAccess
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

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
            ttsEngine = get()
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
}
