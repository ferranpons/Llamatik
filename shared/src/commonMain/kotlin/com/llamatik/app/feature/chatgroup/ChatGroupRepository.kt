package com.llamatik.app.feature.chatgroup

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CHAT_GROUPS_KEY = "llamatik_chat_groups_v1"

@Serializable
data class ChatGroup(
    val id: String,
    val name: String,
    @SerialName("created_at") val createdAtEpochMs: Long,
    @SerialName("updated_at") val updatedAtEpochMs: Long,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
private data class ChatGroupStore(
    val groups: List<ChatGroup> = emptyList(),
)

class ChatGroupRepository(private val settings: Settings) {
    private val mutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun getGroups(): List<ChatGroup> = mutex.withLock {
        readStore().groups.sortedBy { it.sortOrder }
    }

    suspend fun upsert(group: ChatGroup) = mutex.withLock {
        val store = readStore()
        val updated = store.groups
            .filterNot { it.id == group.id }
            .toMutableList()
            .apply { add(group) }
            .sortedBy { it.sortOrder }
        writeStore(ChatGroupStore(updated))
    }

    suspend fun delete(id: String) = mutex.withLock {
        val store = readStore()
        writeStore(ChatGroupStore(store.groups.filterNot { it.id == id }))
    }

    private fun readStore(): ChatGroupStore {
        val raw = settings.getString(CHAT_GROUPS_KEY, "")
        if (raw.isBlank()) return ChatGroupStore()
        return runCatching { json.decodeFromString(ChatGroupStore.serializer(), raw) }
            .getOrElse { ChatGroupStore() }
    }

    private fun writeStore(store: ChatGroupStore) {
        settings.putString(CHAT_GROUPS_KEY, json.encodeToString(ChatGroupStore.serializer(), store))
    }
}
