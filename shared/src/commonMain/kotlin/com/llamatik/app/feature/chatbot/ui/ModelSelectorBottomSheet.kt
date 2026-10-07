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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.Typography

@Composable
fun ModelCategoryRow(
    title: String,
    selectedName: String?,
    onClick: () -> Unit,
) {
    val localization = getCurrentLocalization()
    val subtitle = selectedName?.takeIf { it.isNotBlank() } ?: localization.noModelSelected

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
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
                    style = Typography.get().titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = Typography.get().labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ModelRow(
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
                            TextButton(onClick = { onCancelDownloadClicked(model) }) {
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
