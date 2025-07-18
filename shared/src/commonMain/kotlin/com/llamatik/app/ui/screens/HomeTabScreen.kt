@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.llamatik.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.SetLanguage
import com.llamatik.app.localization.getCurrentLanguage
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.resources.air_support_bro
import com.llamatik.app.ui.components.EmptyLayout
import com.llamatik.app.ui.components.LlamatikDialog
import com.llamatik.app.ui.components.NewsCardSmall
import com.llamatik.app.ui.screens.viewmodel.HomeScreenSideEffects
import com.llamatik.app.ui.screens.viewmodel.HomeScreenState
import com.llamatik.app.ui.screens.viewmodel.HomeScreenViewModel
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder

class HomeTabScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current
        val localization = getCurrentLocalization()
        val viewModel = koinScreenModel<HomeScreenViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )
        val isDialogOpen = remember { mutableStateOf(false) }
        val isLoading = remember { mutableStateOf(false) }

        DisposableEffect(key) {
            viewModel.onStarted(navigator, tabNavigator)
            onDispose {}
        }

        SetupSideEffects(viewModel, isLoading)

        LlamatikTheme {
            if (isLoading.value) {
                EmptyLayout(
                    localization = localization,
                    title = localization.loading,
                    description = "",
                    imageResource = Res.drawable.air_support_bro
                ) {}
            } else {
                HomeScreenView(viewModel, isDialogOpen, localization)
                if (isDialogOpen.value) {
                    LlamatikDialog(
                        message = localization.featureNotAvailableMessage,
                        onDismissRequest = { isDialogOpen.value = false },
                        onConfirmation = { isDialogOpen.value = false },
                        imageDescription = "",
                        dismissButtonText = localization.dismiss
                    )
                }
            }
        }
    }

    @Composable
    private fun SetupSideEffects(
        viewModel: HomeScreenViewModel,
        isLoading: MutableState<Boolean>
    ) {
        val coroutineScope = rememberCoroutineScope()
        val sideEffects = viewModel.sideEffects.collectAsState(
            HomeScreenSideEffects.Initial,
            coroutineScope.coroutineContext
        )
        when (sideEffects.value) {
            HomeScreenSideEffects.Initial -> {
                isLoading.value = true
            }

            HomeScreenSideEffects.OnLoaded -> {
                isLoading.value = false
            }

            HomeScreenSideEffects.OnLoadError -> {
                isLoading.value = false
            }
        }
    }

    @Composable
    fun HomeScreenView(
        viewModel: HomeScreenViewModel,
        isDialogOpen: MutableState<Boolean>,
        localization: Localization
    ) {
        val scrollState = rememberScrollState()
        val state by viewModel.state.collectAsState()

        if (state.currentLanguage != getCurrentLanguage()) {
            SetLanguage(state.currentLanguage)
        }

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
                )
            }
        ) { paddingValues ->

            BoxWithConstraints(
                Modifier.fillMaxSize().padding(paddingValues),
                propagateMinConstraints = true
            ) {
                val maxWidth = this.maxWidth

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Spacer(
                            modifier = Modifier.fillMaxWidth().height(1.dp)
                                .background(MaterialTheme.colorScheme.surfaceDim)
                        )

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
                        LatestNewsCarousel(viewModel, localization, state)
                    }
                }
            }
        }
    }

    @Composable
    fun LatestNewsCarousel(
        viewModel: HomeScreenViewModel,
        localization: Localization,
        state: HomeScreenState
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
