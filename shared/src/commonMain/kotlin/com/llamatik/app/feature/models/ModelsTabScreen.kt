package com.llamatik.app.feature.models

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ModelSelectorContent
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.permissions.rememberNotificationPermissionRequester
import org.koin.core.parameter.ParametersHolder

class ModelsTabScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        val notificationPermissionRequester = rememberNotificationPermissionRequester()

        val viewModel = koinScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )

        val state by viewModel.state.collectAsState()
        val downloadStates by viewModel.downloadStates.collectAsState()
        val downloadingMap = downloadStates.mapValues { it.value.inProgress }
        val progressMap = downloadStates.mapValues { it.value.progress.coerceIn(0, 100) / 100f }

        val loadingEmbedModelName = remember { mutableStateOf<String?>(null) }
        val loadingGenerateModelName = remember { mutableStateOf<String?>(null) }
        val loadingSttModelName = remember { mutableStateOf<String?>(null) }
        val loadingStableDiffusionModelName = remember { mutableStateOf<String?>(null) }
        val loadingVlmModelName = remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(localization.generateModels) })
            }
        ) { padding ->
            ModelSelectorContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                downloadingMap = downloadingMap,
                progressMap = progressMap,
                selectedEmbedModelName = state.selectedEmbedModelName,
                selectedGenerateModelName = state.selectedGenerateModelName,
                selectedSttModelName = state.selectedSttModelName,
                selectedStableDiffusionModelName = state.selectedStableDiffusionModelName,
                selectedVlmModelName = state.selectedVlmModelName,
                embedModels = state.embedModels,
                generateModels = state.generateModels,
                sttModels = state.sttModels,
                stableDiffusionModels = state.stableDiffusionModels,
                vlmModels = state.vlmModels,
                loadingEmbedModelName = loadingEmbedModelName.value,
                loadingGenerateModelName = loadingGenerateModelName.value,
                loadingSttModelName = loadingSttModelName.value,
                loadingStableDiffusionModelName = loadingStableDiffusionModelName.value,
                loadingVlmModelName = loadingVlmModelName.value,
                onEmbedModelSelectedClicked = { model ->
                    loadingEmbedModelName.value = model.name
                    viewModel.onEmbedModelSelected(model)
                },
                onGenerateModelSelectedClicked = { model ->
                    loadingGenerateModelName.value = model.name
                    viewModel.onGenerateModelSelected(model)
                },
                onSttModelSelectedClicked = { model ->
                    loadingSttModelName.value = model.name
                    viewModel.onSttModelSelected(model)
                },
                onStableDiffusionModelSelectedClicked = { model ->
                    loadingStableDiffusionModelName.value = model.name
                    viewModel.onStableDiffusionModelSelected(model)
                },
                onVlmModelSelectedClicked = { model ->
                    loadingVlmModelName.value = model.name
                    viewModel.onVlmModelSelected(model)
                },
                onDownloadModelClicked = { model ->
                    notificationPermissionRequester.requestAndRun(
                        onGranted = { viewModel.onDownloadModel(model) },
                    )
                },
                onDeleteModelClicked = { viewModel.onDeleteModel(it) },
                onCancelDownloadClicked = { viewModel.onCancelDownload(it) },
                onClearAllCachedModelsClicked = { viewModel.onClearAllCachedModels() },
            )
        }
    }
}
