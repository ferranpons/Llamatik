@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dcshub.app.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dcshub.app.localization.Localization
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.WebViewLayout
import com.dcshub.app.ui.theme.LlamatikTheme
import com.dcshub.app.ui.theme.Typography

class WebViewScreen(
    private val title: String? = null,
    private val url: String?
) : Screen {
    private val localization = getCurrentLocalization()
    private lateinit var isLoading: MutableState<Boolean>

    @Composable
    override fun Content() {
        LlamatikTheme {
            val currentNavigator = LocalNavigator.currentOrThrow
            /*val viewModel = koinScreenModel<WebViewModel>(
                parameters = {
                    ParametersHolder(
                        listOf(url, currentNavigator).toMutableList(),
                        false
                    )
                }
            )*/

            DisposableEffect(key) {
                //viewModel.onStarted()
                onDispose {}
            }

            isLoading = remember { mutableStateOf(true) }
            //SetupSideEffects(viewModel, isLoading)
            WebViewScreenLayout(localization) {
                currentNavigator.pop()
            }
        }
    }

    /*
        @Composable
        private fun SetupSideEffects(
            viewModel: PdfViewModel,
            isLoading: MutableState<Boolean>
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
            }
        }
    */
    @Composable
    fun WebViewScreenLayout(
        //viewModel: WebViewModel,
        localization: Localization,
        onClose: () -> Unit
    ) {
        //val state by viewModel.state.collectAsState()

        BoxWithConstraints(Modifier.fillMaxSize(), propagateMinConstraints = true) {
            Scaffold(
                topBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    text = title ?: "",
                                    style = Typography.get().titleMedium
                                )
                            },
                            colors = TopAppBarDefaults.mediumTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                }
            ) {
                /*
                if (isLoading.value) {
                    EmptyLayout(
                        localization = localization,
                        title = "Loading...",
                        description = "Please wait until the document is downloaded",
                        imageResource = Res.drawable.air_support_bro
                    ) {}
                }

                 */

                WebViewLayout(
                    htmlContent = "https://digitalcombatsimulator.com",
                    isLoading = { isLoading.value = !it },
                    onUrlClicked = { url -> {} }
                )


                /*
                if (isLoading.value) {
                    EmptyLayout(
                        localization = localization,
                        title = "Loading...",
                        description = "Please wait until the document is downloaded",
                        imageResource = Res.drawable.air_support_bro
                    ) {}
                } else {
                    if (state.url != null) {
                        state.pdfData?.let { pdfData ->
                            PdfReader(modifier = Modifier.fillMaxSize(), pdfData)
                        }
                    } else {
                        EmptyLayout(localization = localization) {}
                    }
                }
                 */
            }
        }
    }
}
