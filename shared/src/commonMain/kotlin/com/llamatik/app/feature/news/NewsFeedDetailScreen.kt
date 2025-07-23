package com.llamatik.app.feature.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.news.viewmodel.FeedItemDetailScreenState
import com.llamatik.app.feature.news.viewmodel.FeedItemDetailViewModel
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.llamatik_icon_logo
import com.llamatik.app.ui.components.toRichHtmlString
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder

class NewsFeedDetailScreen(private val link: String) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()

        val viewModel = koinScreenModel<FeedItemDetailViewModel>(
            parameters = { ParametersHolder(listOf(currentNavigator).toMutableList(), false) }
        )

        DisposableEffect(key) {
            viewModel.onStarted(currentNavigator, link)
            onDispose {}
        }

        val state by viewModel.state.collectAsState()
        NewsScreenView(state, localization) {
            currentNavigator.pop()
        }
    }

    @Composable
    fun NewsScreenView(
        state: FeedItemDetailScreenState,
        localization: Localization,
        onClose: () -> Unit
    ) {
        val scrollState = rememberScrollState()
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
                                text = localization.feedItemTitle,
                                style = Typography.get().titleMedium
                            )
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier.Companion.padding(paddingValues = paddingValues)
                        .padding(bottom = 80.dp).verticalScroll(scrollState)
                ) {
                    val imageHeight = 260.dp
                    Image(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .height(imageHeight)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        painter = painterResource(Res.drawable.llamatik_icon_logo),
                        contentScale = ContentScale.Companion.Inside,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.Companion.padding(
                            top = 16.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        text = state.feedItem.title,
                        style = Typography.get().titleLarge,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Text(
                        modifier = Modifier.Companion.padding(
                            top = 4.dp,
                            start = 16.dp,
                            end = 16.dp
                        ),
                        text = state.feedItem.pubDate,
                        style = Typography.get().bodySmall,
                    )
                    Text(
                        modifier = Modifier.Companion.fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        text = state.feedItem.description.toRichHtmlString(),
                        style = Typography.get().bodyMedium
                    )
                }
            }
        }
    }
}