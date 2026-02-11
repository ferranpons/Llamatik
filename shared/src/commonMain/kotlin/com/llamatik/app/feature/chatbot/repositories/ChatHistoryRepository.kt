package com.llamatik.app.feature.chatbot.repositories

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CHAT_HISTORY_KEY = "llamatik_chat_history_v1"
private const val MAX_SESSIONS = 50
private const val MAX_MESSAGES_PER_SESSION = 400

@Serializable
data class ChatSessionSummary(
    val id: String,
    val title: String,
    @SerialName("updated_at") val updatedAtEpochMs: Long,
    @SerialName("message_count") val messageCount: Int,
)

@Serializable
enum class PersistedAuthor { ME, BOT }

@Serializable
data class PersistedChatMessage(
    val text: String,
    val author: PersistedAuthor,
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    @SerialName("created_at") val createdAtEpochMs: Long,
    @SerialName("updated_at") val updatedAtEpochMs: Long,
    val messages: List<PersistedChatMessage>,
)

@Serializable
private data class ChatHistoryStore(
    val sessions: List<ChatSession> = emptyList(),
)

class ChatHistoryRepository(
    private val settings: Settings,
) {
    private val mutex = Mutex()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun getSummaries(): List<ChatSessionSummary> = mutex.withLock {
        readStore().sessions
            .sortedByDescending { it.updatedAtEpochMs }
            .map {
                ChatSessionSummary(
                    id = it.id,
                    title = it.title,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    messageCount = it.messages.size
                )
            }
    }

    suspend fun getSession(id: String): ChatSession? = mutex.withLock {
        readStore().sessions.firstOrNull { it.id == id }
    }

    suspend fun upsert(session: ChatSession) = mutex.withLock {
        val store = readStore()
        val trimmed = session.copy(messages = session.messages.takeLast(MAX_MESSAGES_PER_SESSION))

        val replaced = store.sessions
            .filterNot { it.id == trimmed.id }
            .toMutableList()
            .apply { add(trimmed) }
            .sortedByDescending { it.updatedAtEpochMs }
            .take(MAX_SESSIONS)

        writeStore(ChatHistoryStore(replaced))
    }

    suspend fun delete(id: String) = mutex.withLock {
        val store = readStore()
        writeStore(ChatHistoryStore(store.sessions.filterNot { it.id == id }))
    }

    private fun readStore(): ChatHistoryStore {
        val raw = settings.getString(CHAT_HISTORY_KEY, "")
        if (raw.isBlank()) return ChatHistoryStore()
        return runCatching { json.decodeFromString(ChatHistoryStore.serializer(), raw) }
            .getOrElse { ChatHistoryStore() }
    }

    private fun writeStore(store: ChatHistoryStore) {
        settings.putString(CHAT_HISTORY_KEY, json.encodeToString(ChatHistoryStore.serializer(), store))
    }
}
