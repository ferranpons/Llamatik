package com.llamatik.app.feature.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.Typography

@Composable
fun GenerateModelSelector(
    selectedModelName: String?,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChatHistory: () -> Unit,
) {
    val localization = getCurrentLocalization()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onOpenChatHistory,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = LlamatikIcons.ChatHistory,
                        contentDescription = localization.chatHistory,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Surface(
                    onClick = onOpenModelSelector,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = selectedModelName ?: localization.noModelSelected,
                            style = Typography.get().labelMedium
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(24.dp),
                    content = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }
        }
    }
}
