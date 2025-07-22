package com.llamatik.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.components.EmptyLayout
import com.llamatik.app.ui.components.NewsFeedCard
import com.llamatik.app.ui.screens.viewmodel.NewsFeedViewModel
import com.llamatik.app.ui.screens.viewmodel.NewsScreenState
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder

class NewsFeedScreen : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()

        val viewModel = koinScreenModel<NewsFeedViewModel>(
            parameters = { ParametersHolder(listOf(currentNavigator).toMutableList(), false) }
        )

        LifecycleEffect(
            onStarted = {
                viewModel.onStarted(currentNavigator)
            }
        )

        val state by viewModel.state.collectAsState()
        NewsScreenView(viewModel, state, localization) {
            currentNavigator.popUntilRoot()
        }
    }

    @Composable
    fun NewsScreenView(
        viewModel: NewsFeedViewModel,
        state: NewsScreenState,
        localization: Localization,
        onClose: () -> Unit
    ) {

        LlamatikTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { onClose.invoke() }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        },
                        title = {
                            Text(
                                text = localization.news,
                                style = Typography.get().titleMedium
                            )
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { paddingValues ->
                NewsFeedView(paddingValues, state, viewModel, localization)
            }
        }
    }

    @Composable
    fun NewsFeedView(
        paddingValues: PaddingValues,
        state: NewsScreenState,
        viewModel: NewsFeedViewModel,
        localization: Localization
    ) {
        if (state.news.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.padding(paddingValues).fillMaxWidth()
            ) {
                items(state.news.size) { index ->
                    NewsFeedCard(state.news[index]) {
                        viewModel.onOpenFeedItemDetail(state.news[index].link)
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        } else {
            EmptyLayout(localization = localization) {}
        }
    }
}