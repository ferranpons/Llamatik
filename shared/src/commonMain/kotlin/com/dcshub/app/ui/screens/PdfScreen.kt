@file:OptIn(ExperimentalMaterial3Api::class)

package com.dcshub.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dcshub.app.localization.Localization
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.PdfReader
import com.dcshub.app.resources.Res
import com.dcshub.app.resources.air_support_cuate
import com.dcshub.app.ui.components.EmptyLayout
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.screens.viewmodel.PdfScreenSideEffects
import com.dcshub.app.ui.screens.viewmodel.PdfViewModel
import com.dcshub.app.ui.theme.LlamatikTheme
import com.dcshub.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder

class PdfScreen(
    private val title: String? = null,
    private val pdfUrl: String?
) : Screen {

    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()

        LlamatikTheme {
            val currentNavigator = LocalNavigator.currentOrThrow
            val viewModel = koinScreenModel<PdfViewModel>(
                parameters = {
                    ParametersHolder(
                        listOf(pdfUrl, currentNavigator).toMutableList(),
                        false
                    )
                }
            )

            DisposableEffect(key) {
                viewModel.onStarted()
                onDispose {}
            }

            val isLoading = remember { mutableStateOf(false) }
            val isDownloading = remember { mutableStateOf(false) }

            SetupSideEffects(viewModel, isLoading, isDownloading)

            PdfView(viewModel, localization, isLoading, isDownloading) {
                currentNavigator.pop()
            }
        }
    }

    @Composable
    private fun SetupSideEffects(
        viewModel: PdfViewModel,
        isLoading: MutableState<Boolean>,
        isDownloading: MutableState<Boolean>
    ) {
        val coroutineScope = rememberCoroutineScope()
        val sideEffects = viewModel.sideEffects.collectAsState(
            PdfScreenSideEffects.Initial,
            coroutineScope.coroutineContext
        )
        when (sideEffects.value) {
            PdfScreenSideEffects.Initial -> {
                isLoading.value = true
            }

            PdfScreenSideEffects.OnLoaded -> {
                isLoading.value = false
            }

            PdfScreenSideEffects.OnLoadError -> {
                isLoading.value = false
            }

            PdfScreenSideEffects.Downloading -> {
                isDownloading.value = true
            }

            PdfScreenSideEffects.OnDownloaded -> {
                isDownloading.value = false
            }

            PdfScreenSideEffects.OnDownloadedError -> {
                isDownloading.value = false
            }
        }
    }

    @Composable
    fun PdfView(
        viewModel: PdfViewModel,
        localization: Localization,
        isLoading: MutableState<Boolean>,
        isDownloading: MutableState<Boolean>,
        onClose: () -> Unit
    ) {
        val state by viewModel.state.collectAsState()

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { onClose.invoke() }) {
                                Icon(
                                    imageVector = LlamatikIcons.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        },
                        title = {
                            Text(
                                text = title ?: "",
                                style = Typography.get().titleMedium
                            )
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.onDownloadFileClicked()
                            }) {
                                Icon(
                                    imageVector = LlamatikIcons.Download,
                                    contentDescription = "Download file"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        ) { paddingValues ->
            if (isDownloading.value) {
                EmptyLayout(
                    localization = localization,
                    title = "DOWNLOADING...",
                    description = "",
                    imageResource = Res.drawable.air_support_cuate
                ) {}
            } else {
                PdfReader(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    ByteArray(0),
                    null,
                    pdfUrl
                )
            }
        }
    }
}
