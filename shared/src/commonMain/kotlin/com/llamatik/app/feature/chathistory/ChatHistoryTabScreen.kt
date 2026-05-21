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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.llamatik.app.feature.chatgroup.ChatGroup
import com.llamatik.app.feature.chatgroup.ChatGroupRepository
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.navigation.ChatBotTab
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.Typography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.ExperimentalTime

data class ChatHistoryState(
    val sessions: List<ChatSessionSummary> = emptyList(),
    val groups: List<ChatGroup> = emptyList(),
)

class ChatHistoryScreenModel(
    private val repository: ChatHistoryRepository,
    private val groupRepository: ChatGroupRepository,
    private val pendingSessionRepository: PendingSessionRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ChatHistoryState())
    val state: StateFlow<ChatHistoryState> = _state

    fun load() {
        screenModelScope.launch {
            _state.value = ChatHistoryState(
                sessions = repository.getSummaries(),
                groups = groupRepository.getGroups(),
            )
        }
    }

    fun selectAndSwitch(id: String) {
        pendingSessionRepository.request(id)
    }

    fun delete(id: String) {
        screenModelScope.launch {
            repository.delete(id)
            _state.value = _state.value.copy(sessions = repository.getSummaries())
        }
    }

    fun createFolderAndMove(folderName: String, sessionId: String) {
        screenModelScope.launch {
            val group = ChatGroup(
                id = Random.nextLong().toString(),
                name = folderName.trim(),
                createdAtEpochMs = currentEpochMs(),
                updatedAtEpochMs = currentEpochMs(),
                sortOrder = _state.value.groups.size,
            )
            groupRepository.upsert(group)
            repository.moveToGroup(sessionId, group.id)
            _state.value = ChatHistoryState(
                sessions = repository.getSummaries(),
                groups = groupRepository.getGroups(),
            )
        }
    }

    fun moveToFolder(sessionId: String, groupId: String) {
        screenModelScope.launch {
            repository.moveToGroup(sessionId, groupId)
            _state.value = _state.value.copy(sessions = repository.getSummaries())
        }
    }

    fun createFolder(folderName: String) {
        screenModelScope.launch {
            val group = ChatGroup(
                id = Random.nextLong().toString(),
                name = folderName.trim(),
                createdAtEpochMs = currentEpochMs(),
                updatedAtEpochMs = currentEpochMs(),
                sortOrder = _state.value.groups.size,
            )
            groupRepository.upsert(group)
            _state.value = _state.value.copy(groups = groupRepository.getGroups())
        }
    }

    fun deleteFolder(groupId: String) {
        screenModelScope.launch {
            // ungroup sessions that were in the deleted folder
            _state.value.sessions
                .filter { it.groupId == groupId }
                .forEach { repository.moveToGroup(it.id, null) }
            groupRepository.delete(groupId)
            _state.value = ChatHistoryState(
                sessions = repository.getSummaries(),
                groups = groupRepository.getGroups(),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun currentEpochMs(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}

class ChatHistoryTabScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = koinScreenModel<ChatHistoryScreenModel>()
        val state by screenModel.state.collectAsState()

        var showCreateFolderDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { screenModel.load() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(localization.chatHistory) },
                    actions = {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(
                                imageVector = LlamatikIcons.CreateNewFolder,
                                contentDescription = localization.newFolder,
                            )
                        }
                    }
                )
            }
        ) { padding ->
            val ungrouped = state.sessions.filter { it.groupId == null }
            val hasContent = state.groups.isNotEmpty() || ungrouped.isNotEmpty()

            if (!hasContent) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = localization.noChatsYet,
                        style = Typography.get().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // Folder items
                    items(state.groups.size) { index ->
                        val group = state.groups[index]
                        FolderItem(
                            group = group,
                            chatCount = state.sessions.count { it.groupId == group.id },
                            onClick = {
                                navigator.push(ChatFolderScreen(group.id, group.name))
                            },
                            onDelete = { screenModel.deleteFolder(group.id) },
                        )
                    }

                    // Ungrouped sessions
                    items(ungrouped.size) { index ->
                        val s = ungrouped[index]
                        ChatSessionItem(
                            session = s,
                            groups = state.groups,
                            onClick = {
                                screenModel.selectAndSwitch(s.id)
                                tabNavigator.current = ChatBotTab
                            },
                            onDelete = { screenModel.delete(s.id) },
                            onMoveToFolder = { groupId -> screenModel.moveToFolder(s.id, groupId) },
                            onCreateFolderAndMove = { name -> screenModel.createFolderAndMove(name, s.id) },
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = { name ->
                    screenModel.createFolder(name)
                    showCreateFolderDialog = false
                },
            )
        }
    }
}

@Composable
private fun FolderItem(
    group: ChatGroup,
    chatCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val localization = getCurrentLocalization()
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = LlamatikIcons.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = Typography.get().labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$chatCount ${localization.messages}",
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

@Composable
private fun ChatSessionItem(
    session: ChatSessionSummary,
    groups: List<ChatGroup>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: (String) -> Unit,
    onCreateFolderAndMove: (String) -> Unit,
) {
    val localization = getCurrentLocalization()
    var menuExpanded by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
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
                    text = { Text(localization.moveToFolder) },
                    leadingIcon = {
                        Icon(LlamatikIcons.Folder, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        showMoveDialog = true
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

    if (showMoveDialog) {
        MoveToFolderDialog(
            groups = groups,
            onDismiss = { showMoveDialog = false },
            onSelectFolder = { groupId ->
                onMoveToFolder(groupId)
                showMoveDialog = false
            },
            onCreateFolderAndMove = { name ->
                onCreateFolderAndMove(name)
                showMoveDialog = false
            },
        )
    }
}

@Composable
private fun MoveToFolderDialog(
    groups: List<ChatGroup>,
    onDismiss: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onCreateFolderAndMove: (String) -> Unit,
) {
    val localization = getCurrentLocalization()
    var showCreateInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localization.selectOrCreateFolder) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder(group.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LlamatikIcons.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                        )
                        Text(
                            text = group.name,
                            style = Typography.get().labelLarge,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (showCreateInput) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = {
                            newFolderName = it
                            nameError = false
                        },
                        label = { Text(localization.folderName) },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(localization.noFolderName) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateInput = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = LlamatikIcons.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                        )
                        Text(
                            text = localization.createFolder,
                            style = Typography.get().labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateInput) {
                TextButton(
                    onClick = {
                        if (newFolderName.isBlank()) {
                            nameError = true
                        } else {
                            onCreateFolderAndMove(newFolderName)
                        }
                    }
                ) {
                    Text(localization.createFolder)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localization.cancel)
            }
        },
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val localization = getCurrentLocalization()
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localization.newFolder) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text(localization.folderName) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(localization.noFolderName) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) nameError = true else onConfirm(name)
                }
            ) {
                Text(localization.createFolder)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localization.cancel) }
        },
    )
}
