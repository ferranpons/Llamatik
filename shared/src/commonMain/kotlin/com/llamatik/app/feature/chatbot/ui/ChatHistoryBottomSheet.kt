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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.repositories.ChatSessionSummary
import com.llamatik.app.localization.Localization
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.Typography

@Composable
fun ChatHistoryBottomSheet(
    localization: Localization,
    sessions: List<ChatSessionSummary>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = localization.chatHistory,
                style = Typography.get().titleLarge
            )
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = localization.noChatsYet,
                    style = Typography.get().bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                return@ModalBottomSheet
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions.size) { index ->
                    val s = sessions[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoad(s.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = s.title,
                                style = Typography.get().labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${s.messageCount} ${localization.messages}",
                                style = Typography.get().labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onDelete(s.id) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = LlamatikIcons.Delete,
                                contentDescription = localization.delete
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
