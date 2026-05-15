package com.llamatik.app.feature.chathistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
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

class ChatHistoryScreenModel(
    private val repository: ChatHistoryRepository,
    private val pendingSessionRepository: PendingSessionRepository,
) : ScreenModel {

    private val _sessions = MutableStateFlow<List<ChatSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChatSessionSummary>> = _sessions

    fun load() {
        screenModelScope.launch {
            _sessions.value = repository.getSummaries()
        }
    }

    fun selectAndSwitch(id: String) {
        pendingSessionRepository.request(id)
    }

    fun delete(id: String) {
        screenModelScope.launch {
            repository.delete(id)
            _sessions.value = repository.getSummaries()
        }
    }
}

class ChatHistoryTabScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val tabNavigator = LocalTabNavigator.current

        val screenModel = koinScreenModel<ChatHistoryScreenModel>()
        val sessions by screenModel.sessions.collectAsState()

        LaunchedEffect(Unit) {
            screenModel.load()
        }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(localization.chatHistory) })
            }
        ) { padding ->
            if (sessions.isEmpty()) {
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
                    items(sessions.size) { index ->
                        val s = sessions[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    screenModel.selectAndSwitch(s.id)
                                    tabNavigator.current = ChatBotTab
                                }
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
                                onClick = { screenModel.delete(s.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = LlamatikIcons.Delete,
                                    contentDescription = localization.delete
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
