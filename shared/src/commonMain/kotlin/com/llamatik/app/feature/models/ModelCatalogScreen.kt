package com.llamatik.app.feature.models

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.llamatik.app.feature.chatbot.model.ModelCategory
import com.llamatik.app.feature.chatbot.ui.ModelRow
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.permissions.rememberNotificationPermissionRequester
import org.koin.core.parameter.ParametersHolder

enum class ModelCatalogType {
    Generate, Embed, Stt, StableDiffusion, Vlm
}

class ModelCatalogScreen(private val type: ModelCatalogType) : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        val notificationPermissionRequester = rememberNotificationPermissionRequester()

        val viewModel = navigator.koinNavigatorScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )

        val state by viewModel.state.collectAsState()
        val downloadStates by viewModel.downloadStates.collectAsState()
        val downloadingMap = downloadStates.mapValues { it.value.inProgress }
        val progressMap = downloadStates.mapValues { it.value.progress.coerceIn(0, 100) / 100f }

        val loadingModelName = remember { mutableStateOf<String?>(null) }
        var showDownloadFromUrl by remember { mutableStateOf(false) }

        val catalogCategory = when (type) {
            ModelCatalogType.Generate -> ModelCategory.Generate
            ModelCatalogType.Embed -> ModelCategory.Embed
            ModelCatalogType.Stt -> ModelCategory.Stt
            ModelCatalogType.StableDiffusion -> ModelCategory.StableDiffusion
            ModelCatalogType.Vlm -> ModelCategory.Vlm
        }

        if (showDownloadFromUrl) {
            DownloadFromUrlDialog(
                initialCategory = catalogCategory,
                onDismiss = { showDownloadFromUrl = false },
                onConfirm = { url, name, category ->
                    viewModel.onDownloadFromUrl(url, name, category)
                    showDownloadFromUrl = false
                },
            )
        }

        val (models, selectedName) = when (type) {
            ModelCatalogType.Generate -> state.generateModels to state.selectedGenerateModelName
            ModelCatalogType.Embed -> state.embedModels to state.selectedEmbedModelName
            ModelCatalogType.Stt -> state.sttModels to state.selectedSttModelName
            ModelCatalogType.StableDiffusion -> state.stableDiffusionModels to state.selectedStableDiffusionModelName
            ModelCatalogType.Vlm -> state.vlmModels to state.selectedVlmModelName
        }

        val screenTitle = when (type) {
            ModelCatalogType.Generate -> "Generate Models"
            ModelCatalogType.Embed -> "Embed Models"
            ModelCatalogType.Stt -> "Speech to Text Models"
            ModelCatalogType.StableDiffusion -> "Image Generation Models"
            ModelCatalogType.Vlm -> "Vision Models"
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(screenTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                models.forEach { model ->
                    ModelRow(
                        model = model,
                        isCurrent = (model.name == selectedName),
                        isDownloading = downloadingMap[model.url] == true,
                        progress = progressMap[model.url] ?: 0f,
                        isSelecting = (model.name == loadingModelName.value),
                        onModelSelectedClicked = { m ->
                            loadingModelName.value = m.name
                            when (type) {
                                ModelCatalogType.Generate -> viewModel.onGenerateModelSelected(m)
                                ModelCatalogType.Embed -> viewModel.onEmbedModelSelected(m)
                                ModelCatalogType.Stt -> viewModel.onSttModelSelected(m)
                                ModelCatalogType.StableDiffusion -> viewModel.onStableDiffusionModelSelected(m)
                                ModelCatalogType.Vlm -> viewModel.onVlmModelSelected(m)
                            }
                        },
                        onDownloadModelClicked = { m ->
                            notificationPermissionRequester.requestAndRun(
                                onGranted = { viewModel.onDownloadModel(m) }
                            )
                        },
                        onDeleteModelClicked = { viewModel.onDeleteModel(it) },
                        onCancelDownloadClicked = { viewModel.onCancelDownload(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
