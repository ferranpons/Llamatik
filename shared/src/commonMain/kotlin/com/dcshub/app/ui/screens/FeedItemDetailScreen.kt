package com.dcshub.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dcshub.app.localization.Localization
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.shimmerLoadingAnimation
import com.dcshub.app.resources.Res
import com.dcshub.app.resources.llamatik_icon_logo
import com.dcshub.app.ui.components.toRichHtmlString
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.screens.viewmodel.FeedItemDetailScreenState
import com.dcshub.app.ui.screens.viewmodel.FeedItemDetailViewModel
import com.dcshub.app.ui.theme.LlamatikTheme
import com.dcshub.app.ui.theme.Typography
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.ParametersHolder

class FeedItemDetailScreen(private val link: String) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()

        val viewModel = koinScreenModel<FeedItemDetailViewModel>(
            parameters = { ParametersHolder(listOf(currentNavigator).toMutableList(), false) }
        )

        LifecycleEffect(
            onStarted = {
                viewModel.onStarted(currentNavigator, link)
            }
        )

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
                    modifier = Modifier.padding(paddingValues = paddingValues)
                        .padding(bottom = 80.dp).verticalScroll(scrollState)
                ) {
                    val imageHeight = 260.dp
                    val roundedCornerSize = 10.dp

                    if (state.feedItem.enclosure?.url != null) {
                        KamelImage(
                            resource = asyncPainterResource(data = state.feedItem.enclosure.url),
                            contentDescription = "image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imageHeight),
                            onLoading = {
                                Box(
                                    modifier = Modifier
                                        .background(color = MaterialTheme.colorScheme.primaryContainer)
                                        .height(imageHeight)
                                        .fillMaxWidth()
                                        .shimmerLoadingAnimation(isLoadingCompleted = false)
                                )
                            },
                            onFailure = {
                                Box(
                                    modifier = Modifier
                                        .background(color = MaterialTheme.colorScheme.primaryContainer)
                                        .height(imageHeight)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        modifier = Modifier.size(36.dp),
                                        imageVector = LlamatikIcons.BrokenImage,
                                        contentDescription = "image",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        )
                    } else {
                        Image(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imageHeight)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            painter = painterResource(Res.drawable.llamatik_icon_logo),
                            contentScale = ContentScale.Inside,
                            contentDescription = null,
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        text = state.feedItem.title,
                        style = Typography.get().titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
                        text = state.feedItem.pubDate,
                        style = Typography.get().bodySmall,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        text = state.feedItem.description.toRichHtmlString(),
                        style = Typography.get().bodyMedium
                    )
                }
            }
        }
    }
}