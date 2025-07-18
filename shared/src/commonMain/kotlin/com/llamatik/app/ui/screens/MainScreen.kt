package com.llamatik.app.ui.screens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.llamatik.app.navigation.ChatBotTab
import com.llamatik.app.navigation.HomeTab
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.platform.RootSnackbarHostStateRepository
import com.llamatik.app.ui.components.LLamatikNavigationBar
import com.llamatik.app.ui.components.LlamatikNavigationBarItem
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder
import org.koin.mp.KoinPlatform

class MainScreen : Screen {
    @Composable
    override fun Content() {
        LlamatikTheme {
            MainScreenView()
        }
    }
}

@Composable
fun MainScreenView() {
    val snackbarHostState = remember { SnackbarHostState() }
    val rootNavigator = LocalNavigator.currentOrThrow
    TabNavigator(
        HomeTab,
        disposeNestedNavigators = false
    ) { _ ->
        val rootNavigatorRepository = setupRootNavigator(rootNavigator, LocalTabNavigator.current)
        val rootSnackbarHostStateRepository = setupRootSnackbarHostState(snackbarHostState)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(rootSnackbarHostStateRepository.snackbarHostState) },
            bottomBar = {
                LLamatikNavigationBar {
                    TabNavigationItem(HomeTab, rootNavigatorRepository)
                    TabNavigationItem(ChatBotTab, rootNavigatorRepository)
                }
            }
        ) {
            SlideTransition(LocalNavigator.currentOrThrow) { screen ->
                screen.Content()
            }
        }
    }

}

fun setupRootNavigator(
    rootNavigator: Navigator,
    tabNavigator: TabNavigator
): RootNavigatorRepository {
    val koin = KoinPlatform.getKoin()
    return koin.get(
        null,
        parameters = {
            ParametersHolder(
                listOf(rootNavigator, tabNavigator).toMutableList(),
                false
            )
        })
}

fun setupRootSnackbarHostState(snackbarHostState: SnackbarHostState): RootSnackbarHostStateRepository {
    val koin = KoinPlatform.getKoin()
    return koin.get(
        null,
        parameters = { ParametersHolder(listOf(snackbarHostState).toMutableList(), false) })
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab, rootNavigator: RootNavigatorRepository) {
    val tabNavigator = LocalTabNavigator.current
    val currentDestination = tabNavigator.current.key == tab.key

    LlamatikNavigationBarItem(
        selected = currentDestination,
        onClick = {
            tabNavigator.current = tab
        },
        icon = {
            tab.options.icon?.let {
                Icon(
                    painter = it,
                    contentDescription = tab.options.title
                )
            }
        },
        label = {
            Text(
                text = tab.options.title,
                style = Typography.get().labelSmall
            )
        }
    )
}
