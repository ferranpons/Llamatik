package com.llamatik.app.feature.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.Typography

@Composable
fun ModelSelectorBottomSheet(
    downloadingMap: Map<String, Boolean>,
    progressMap: Map<String, Float>,
    selectedEmbedModelName: String?,
    selectedGenerateModelName: String?,
    selectedSttModelName: String?,
    selectedStableDiffusionModelName: String?,
    embedModels: List<LlamaModel>,
    generateModels: List<LlamaModel>,
    sttModels: List<LlamaModel>,
    stableDiffusionModels: List<LlamaModel>,
    loadingEmbedModelName: String?,
    loadingGenerateModelName: String?,
    loadingSttModelName: String?,
    loadingStableDiffusionModelName: String?,
    onEmbedModelSelectedClicked: (LlamaModel) -> Unit,
    onGenerateModelSelectedClicked: (LlamaModel) -> Unit,
    onSttModelSelectedClicked: (LlamaModel) -> Unit,
    onStableDiffusionModelSelectedClicked: (LlamaModel) -> Unit,
    onDownloadModelClicked: (LlamaModel) -> Unit,
    onDeleteModelClicked: (LlamaModel) -> Unit,
    onCancelDownloadClicked: (LlamaModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val localization = getCurrentLocalization()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            // --- Generate models ---
            Text(
                text = localization.generateModels,
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            generateModels.forEach { model ->
                ModelRow(
                    model = model,
                    isCurrent = (model.name == selectedGenerateModelName),
                    isDownloading = downloadingMap[model.url] == true,
                    progress = progressMap[model.url] ?: 0f,
                    isSelecting = (model.name == loadingGenerateModelName),
                    onModelSelectedClicked = onGenerateModelSelectedClicked,
                    onDownloadModelClicked = onDownloadModelClicked,
                    onDeleteModelClicked = onDeleteModelClicked,
                    onCancelDownloadClicked = onCancelDownloadClicked,
                )
                Spacer(Modifier.height(12.dp))
            }

            // --- Embed models ---
            Spacer(Modifier.height(24.dp))
            Text(
                text = localization.embedModels,
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            embedModels.forEach { model ->
                ModelRow(
                    model = model,
                    isCurrent = (model.name == selectedEmbedModelName),
                    isDownloading = downloadingMap[model.url] == true,
                    progress = progressMap[model.url] ?: 0f,
                    isSelecting = (model.name == loadingEmbedModelName),
                    onModelSelectedClicked = onEmbedModelSelectedClicked,
                    onDownloadModelClicked = onDownloadModelClicked,
                    onDeleteModelClicked = onDeleteModelClicked,
                    onCancelDownloadClicked = onCancelDownloadClicked,
                )
                Spacer(Modifier.height(12.dp))
            }

            // --- STT models ---
            Spacer(Modifier.height(24.dp))
            Text(
                text = localization.sttModels,
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            sttModels.forEach { model ->
                ModelRow(
                    model = model,
                    isCurrent = (model.name == selectedSttModelName),
                    isDownloading = downloadingMap[model.url] == true,
                    progress = progressMap[model.url] ?: 0f,
                    isSelecting = (model.name == loadingSttModelName),
                    onModelSelectedClicked = onSttModelSelectedClicked,
                    onDownloadModelClicked = onDownloadModelClicked,
                    onDeleteModelClicked = onDeleteModelClicked,
                    onCancelDownloadClicked = onCancelDownloadClicked,
                )
                Spacer(Modifier.height(12.dp))
            }

            // --- Stable Diffusion (image) models ---
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Stable Diffusion Models",
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            stableDiffusionModels.forEach { model ->
                ModelRow(
                    model = model,
                    isCurrent = (model.name == selectedStableDiffusionModelName),
                    isDownloading = downloadingMap[model.url] == true,
                    progress = progressMap[model.url] ?: 0f,
                    isSelecting = (model.name == loadingStableDiffusionModelName),
                    onModelSelectedClicked = onStableDiffusionModelSelectedClicked,
                    onDownloadModelClicked = onDownloadModelClicked,
                    onDeleteModelClicked = onDeleteModelClicked,
                    onCancelDownloadClicked = onCancelDownloadClicked,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: LlamaModel,
    isCurrent: Boolean,
    isDownloading: Boolean,
    progress: Float,
    isSelecting: Boolean,
    onModelSelectedClicked: (LlamaModel) -> Unit,
    onDownloadModelClicked: (LlamaModel) -> Unit,
    onDeleteModelClicked: (LlamaModel) -> Unit,
    onCancelDownloadClicked: (LlamaModel) -> Unit,
) {
    val localization = getCurrentLocalization()
    val hasLocalFile = !model.localPath.isNullOrEmpty() || !model.fileName.isNullOrEmpty()
    var localDownloading by remember(model.url, isDownloading) { mutableStateOf(isDownloading) }
    val effectiveDownloading = localDownloading || isDownloading

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(model.name, style = Typography.get().labelLarge)
                Text("${model.sizeMb} MB", style = Typography.get().labelSmall)
            }

            if (hasLocalFile) {
                if (isCurrent) {
                    FilledTonalButton(onClick = { /* no-op */ }, enabled = false) {
                        Text(localization.current)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { if (!isSelecting) onModelSelectedClicked(model) },
                            enabled = !isSelecting
                        ) {
                            if (isSelecting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(localization.loading)
                                }
                            } else {
                                Text(localization.select)
                            }
                        }
                        FilledTonalButton(
                            onClick = { onDeleteModelClicked(model) },
                            enabled = !isSelecting
                        ) {
                            Text(localization.delete)
                        }
                    }
                }
            } else {
                if (effectiveDownloading) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(localization.downloading, style = Typography.get().labelSmall)
                            TextButton(
                                onClick = { onCancelDownloadClicked(model) }
                            ) {
                                Text(localization.stop)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .width(140.dp)
                                .progressSemantics()
                        )
                    }
                } else {
                    Button(onClick = { localDownloading = true; onDownloadModelClicked(model) }) {
                        Text(localization.download)
                    }
                }
            }
        }

        if (effectiveDownloading || !hasLocalFile) {
            Spacer(Modifier.height(8.dp))
        }
    }
}
