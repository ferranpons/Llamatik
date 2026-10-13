package com.llamatik.sdk.agent

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AGENT_LOG_KEY = "llamatik_agent_log_v1"
private const val MAX_LOG_ENTRIES = 500

@Serializable
private data class AgentLogStore(val entries: List<AgentActionLog> = emptyList())

class AgentActionLogRepository(private val settings: Settings) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    suspend fun getLogs(): List<AgentActionLog> = mutex.withLock {
        readStore().entries.sortedByDescending { it.createdAtEpochMs }
    }

    suspend fun append(log: AgentActionLog) = mutex.withLock {
        val store = readStore()
        val updated = (store.entries + log)
            .sortedByDescending { it.createdAtEpochMs }
            .take(MAX_LOG_ENTRIES)
        writeStore(AgentLogStore(updated))
    }

    suspend fun clearAll() = mutex.withLock {
        writeStore(AgentLogStore())
    }

    private fun readStore(): AgentLogStore {
        val raw = settings.getString(AGENT_LOG_KEY, "")
        if (raw.isBlank()) return AgentLogStore()
        return runCatching { json.decodeFromString(AgentLogStore.serializer(), raw) }
            .getOrElse { AgentLogStore() }
    }

    private fun writeStore(store: AgentLogStore) {
        settings.putString(AGENT_LOG_KEY, json.encodeToString(AgentLogStore.serializer(), store))
    }
}
