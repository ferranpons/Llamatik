package com.llamatik.app.feature.chatbot.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    /**
     * Called when user confirms clearing all cached models and the PDF RAG store.
     */
    onClearAllCachedModelsClicked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val localization = getCurrentLocalization()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Collapsible groups (collapsed by default)
    var expandGenerate by rememberSaveable { mutableStateOf(false) }
    var expandEmbed by rememberSaveable { mutableStateOf(false) }
    var expandStt by rememberSaveable { mutableStateOf(false) }
    var expandSd by rememberSaveable { mutableStateOf(false) }

    // confirmation dialog state
    var showConfirmClear by remember { mutableStateOf(false) }

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
            // top action: clear cached models & RAG
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localization.settings,
                            style = Typography.get().titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = localization.removeAllDownloadedModels,
                            style = Typography.get().labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { showConfirmClear = true }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear cached")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // confirmation dialog
            if (showConfirmClear) {
                AlertDialog(
                    onDismissRequest = { showConfirmClear = false },
                    title = { Text(text = localization.clearCachedModelsDialogTitle) },
                    text = { Text(text = localization.clearCachedModelsDialogMessage) },
                    confirmButton = {
                        Button(onClick = {
                            showConfirmClear = false
                            onClearAllCachedModelsClicked()
                        }) {
                            Text(text = localization.clear)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmClear = false }) {
                            Text(text = localization.cancel)
                        }
                    }
                )
            }

            // --- Generate models ---
            GroupHeader(
                title = localization.generateModels,
                expanded = expandGenerate,
                // When collapsed, show current model or "Nothing selected"
                selectedName = selectedGenerateModelName,
                onToggle = { expandGenerate = !expandGenerate }
            )
            if (expandGenerate) {
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
            }

            // --- Embed models ---
            Spacer(Modifier.height(16.dp))
            GroupHeader(
                title = localization.embedModels,
                expanded = expandEmbed,
                selectedName = selectedEmbedModelName,
                onToggle = { expandEmbed = !expandEmbed }
            )
            if (expandEmbed) {
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
            }

            // --- STT models ---
            Spacer(Modifier.height(16.dp))
            GroupHeader(
                title = localization.sttModels,
                expanded = expandStt,
                selectedName = selectedSttModelName,
                onToggle = { expandStt = !expandStt }
            )
            if (expandStt) {
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
            }

            // --- Stable Diffusion (image) models ---
            Spacer(Modifier.height(16.dp))
            GroupHeader(
                title = localization.imageGenerationModels,
                expanded = expandSd,
                selectedName = selectedStableDiffusionModelName,
                onToggle = { expandSd = !expandSd }
            )
            if (expandSd) {
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

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    expanded: Boolean,
    selectedName: String?,
    onToggle: () -> Unit,
) {
    val subtitle = selectedName?.takeIf { it.isNotBlank() } ?: getCurrentLocalization().noModelSelected

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.get().titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = Typography.get().labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
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
                                    Spacer(Modifier.size(8.dp))
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
