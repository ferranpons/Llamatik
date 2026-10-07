package com.llamatik.app.feature.chathistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.llamatik.app.feature.chatbot.repositories.ChatHistoryRepository
import com.llamatik.app.feature.chatbot.repositories.ChatSessionSummary
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.navigation.ChatBotTab
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.Typography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatFolderScreenModel(
    private val repository: ChatHistoryRepository,
    private val pendingSessionRepository: PendingSessionRepository,
) : ScreenModel {

    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChatSessionSummary>> = _sessions

    fun load(groupId: String) {
        screenModelScope.launch {
            _sessions.value = repository.getSummaries().filter { it.groupId == groupId }
        }
    }

    fun selectAndSwitch(id: String) {
        pendingSessionRepository.request(id)
    }

    fun removeFromFolder(sessionId: String, groupId: String) {
        screenModelScope.launch {
            repository.moveToGroup(sessionId, null)
            _sessions.value = _sessions.value.filter { it.id != sessionId }
        }
    }

    fun delete(id: String, groupId: String) {
        screenModelScope.launch {
            repository.delete(id)
            _sessions.value = _sessions.value.filter { it.id != id }
        }
    }
}

data class ChatFolderScreen(val groupId: String, val folderName: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<ChatFolderScreenModel>()
        val sessions by screenModel.sessions.collectAsState()

        LaunchedEffect(groupId) { screenModel.load(groupId) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(folderName) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = LlamatikIcons.ArrowBack,
                                contentDescription = localization.backLabel,
                            )
                        }
                    }
                )
            }
        ) { padding ->
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = localization.noChatsYet,
                        style = Typography.get().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(sessions.size) { index ->
                        val s = sessions[index]
                        FolderChatItem(
                            session = s,
                            onClick = {
                                screenModel.selectAndSwitch(s.id)
                                tabNavigator.current = ChatBotTab
                            },
                            onRemoveFromFolder = { screenModel.removeFromFolder(s.id, groupId) },
                            onDelete = { screenModel.delete(s.id, groupId) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FolderChatItem(
    session: ChatSessionSummary,
    onClick: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    val localization = getCurrentLocalization()
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = Typography.get().labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${session.messageCount} ${localization.messages}",
                style = Typography.get().labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = LlamatikIcons.MoreVert,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(localization.removeFromFolder) },
                    leadingIcon = {
                        Icon(LlamatikIcons.Folder, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onRemoveFromFolder()
                    },
                )
                DropdownMenuItem(
                    text = { Text(localization.delete) },
                    leadingIcon = {
                        Icon(LlamatikIcons.Delete, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}
