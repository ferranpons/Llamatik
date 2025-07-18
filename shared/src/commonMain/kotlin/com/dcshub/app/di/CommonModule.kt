package com.dcshub.app.di

import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.dcshub.app.data.repositories.DownloadFileRepository
import com.dcshub.app.data.repositories.LatestNewsMockRepository
import com.dcshub.app.data.repositories.NewsRepository
import com.dcshub.app.data.usecases.GetAllNewsUseCase
import com.dcshub.app.data.usecases.GetLatestNewsUseCase
import com.dcshub.app.data.usecases.GetPdfUseCase
import com.dcshub.app.feature.debugmenu.repositories.GlobalAppSettingsRepository
import com.dcshub.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.LlamatikEventTracker
import com.dcshub.app.platform.RootNavigatorRepository
import com.dcshub.app.platform.RootSnackbarHostStateRepository
import com.dcshub.app.platform.ServiceClient
import com.dcshub.app.ui.screens.viewmodel.ChatBotViewModel
import com.dcshub.app.ui.screens.viewmodel.FeedItemDetailViewModel
import com.dcshub.app.ui.screens.viewmodel.HomeScreenViewModel
import com.dcshub.app.ui.screens.viewmodel.NewsFeedViewModel
import com.dcshub.app.ui.screens.viewmodel.PdfViewModel
import com.dcshub.app.ui.screens.viewmodel.SettingsViewModel
import com.dcshub.app.ui.screens.viewmodel.WebViewModel
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

    factory { (userManualUrl: String) ->
        PdfViewModel(userManualUrl, get(), get(), get())
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

    singleOf(::LatestNewsMockRepository)
    factoryOf(::GetLatestNewsUseCase)
    factoryOf(::GetPdfUseCase)

    singleOf(::NewsRepository)

    singleOf(::DownloadFileRepository)
    singleOf(::Settings)
    single { ServiceClient }
    single { getCurrentLocalization() }
}
