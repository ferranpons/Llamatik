package com.llamatik.app.di

import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.llamatik.app.data.repositories.DownloadFileRepository
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.debugmenu.repositories.GlobalAppSettingsRepository
import com.llamatik.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.llamatik.app.feature.news.repositories.NewsRepository
import com.llamatik.app.feature.news.usecases.GetAllNewsUseCase
import com.llamatik.app.feature.news.viewmodel.FeedItemDetailViewModel
import com.llamatik.app.feature.news.viewmodel.NewsFeedViewModel
import com.llamatik.app.feature.webview.viewmodel.WebViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.LlamatikEventTracker
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.platform.RootSnackbarHostStateRepository
import com.llamatik.app.platform.ServiceClient
import com.llamatik.app.ui.screens.viewmodel.HomeScreenViewModel
import com.llamatik.app.ui.screens.viewmodel.SettingsViewModel
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
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

    factory {
        ChatBotViewModel(get(), get())
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

    singleOf(::DownloadFileRepository)
    singleOf(::Settings)
    single { ServiceClient }
    single { getCurrentLocalization() }
}
