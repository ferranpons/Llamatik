package com.llamatik.sdk.agent.audit

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val AUDIT_KEY = "llamatik_agent_audit_v1"
private const val MAX_ENTRIES = 1000

@Serializable
private data class AuditStore(val entries: List<AgentAuditEntry> = emptyList())

class AgentAuditRepository(private val settings: Settings) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    suspend fun getAll(): List<AgentAuditEntry> = mutex.withLock {
        readStore().entries.sortedByDescending { it.createdAtMs }
    }

    suspend fun getByTool(toolId: String): List<AgentAuditEntry> =
        getAll().filter { it.toolId == toolId }

    suspend fun getRecent(limit: Int = 50): List<AgentAuditEntry> =
        getAll().take(limit)

    suspend fun append(entry: AgentAuditEntry) = mutex.withLock {
        val store = readStore()
        val updated = (store.entries + entry)
            .sortedByDescending { it.createdAtMs }
            .take(MAX_ENTRIES)
        writeStore(AuditStore(updated))
    }

    suspend fun clearAll() = mutex.withLock { writeStore(AuditStore()) }

    private fun readStore(): AuditStore {
        val raw = settings.getString(AUDIT_KEY, "")
        if (raw.isBlank()) return AuditStore()
        return runCatching { json.decodeFromString(AuditStore.serializer(), raw) }
            .getOrElse { AuditStore() }
    }

    private fun writeStore(store: AuditStore) {
        settings.putString(AUDIT_KEY, json.encodeToString(AuditStore.serializer(), store))
    }
}
