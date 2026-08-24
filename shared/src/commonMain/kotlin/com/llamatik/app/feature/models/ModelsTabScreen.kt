package com.llamatik.app.feature.models

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ModelCategoryRow
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder

class ModelsTabScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = navigator.koinNavigatorScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )

        LaunchedEffect(Unit) {
            viewModel.onStarted(navigator)
        }

        val state by viewModel.state.collectAsState()
        var showConfirmClear by remember { mutableStateOf(false) }
        var showDownloadFromUrl by remember { mutableStateOf(false) }

        if (showDownloadFromUrl) {
            DownloadFromUrlDialog(
                initialCategory = null,
                onDismiss = { showDownloadFromUrl = false },
                onConfirm = { url, name, category ->
                    viewModel.onDownloadFromUrl(url, name, category)
                    showDownloadFromUrl = false
                },
            )
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text(localization.clearCachedModelsDialogTitle) },
                text = { Text(localization.clearCachedModelsDialogMessage) },
                confirmButton = {
                    Button(onClick = {
                        showConfirmClear = false
                        viewModel.onClearAllCachedModels()
                    }) {
                        Text(localization.clear)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) {
                        Text(localization.cancel)
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Models",
                            style = Typography.get().titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { showConfirmClear = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = localization.removeAllDownloadedModels
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showDownloadFromUrl = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = localization.downloadFromUrl,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ModelCategoryRow(
                    title = localization.generateModels,
                    selectedName = state.selectedGenerateModelName,
                    onClick = { navigator.push(ModelCatalogScreen(ModelCatalogType.Generate)) }
                )
                Spacer(Modifier.height(8.dp))
                ModelCategoryRow(
                    title = localization.embedModels,
                    selectedName = state.selectedEmbedModelName,
                    onClick = { navigator.push(ModelCatalogScreen(ModelCatalogType.Embed)) }
                )
                Spacer(Modifier.height(8.dp))
                ModelCategoryRow(
                    title = localization.sttModels,
                    selectedName = state.selectedSttModelName,
                    onClick = { navigator.push(ModelCatalogScreen(ModelCatalogType.Stt)) }
                )
                Spacer(Modifier.height(8.dp))
                ModelCategoryRow(
                    title = localization.imageGenerationModels,
                    selectedName = state.selectedStableDiffusionModelName,
                    onClick = { navigator.push(ModelCatalogScreen(ModelCatalogType.StableDiffusion)) }
                )
                Spacer(Modifier.height(8.dp))
                ModelCategoryRow(
                    title = localization.vlmModels,
                    selectedName = state.selectedVlmModelName,
                    onClick = { navigator.push(ModelCatalogScreen(ModelCatalogType.Vlm)) }
                )
            }
        }
    }
}
