package com.llamatik.sdk.agent.memory

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MEMORY_KEY = "llamatik_agent_memory_v1"
private const val MAX_ENTRIES = 200

@Serializable
private data class MemoryStore(val entries: List<MemoryEntry> = emptyList())

class AgentMemory(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun getAll(): List<MemoryEntry> = readStore().entries.filter { it.enabled }

    fun getByType(type: MemoryType): List<MemoryEntry> = getAll().filter { it.type == type }

    fun search(query: String): List<MemoryEntry> {
        if (query.isBlank()) return getAll()
        val tokens = query.lowercase().split(Regex("\\s+"))
        return getAll().filter { entry ->
            val lower = entry.content.lowercase()
            tokens.any { lower.contains(it) } ||
                entry.tags.any { tag -> tokens.any { tag.lowercase().contains(it) } }
        }
    }

    fun add(content: String, type: MemoryType, tags: List<String> = emptyList()): MemoryEntry {
        val entry = MemoryEntry(
            id = generateId(),
            content = content.take(500),
            type = type,
            tags = tags,
            createdAtMs = currentTimeMs(),
        )
        val store = readStore()
        val updated = (store.entries + entry).takeLast(MAX_ENTRIES)
        writeStore(MemoryStore(updated))
        return entry
    }

    fun update(id: String, content: String) {
        val store = readStore()
        writeStore(MemoryStore(store.entries.map {
            if (it.id == id) it.copy(content = content.take(500)) else it
        }))
    }

    fun delete(id: String) {
        val store = readStore()
        writeStore(MemoryStore(store.entries.filterNot { it.id == id }))
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val store = readStore()
        writeStore(MemoryStore(store.entries.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }))
    }

    fun buildContextBlock(maxChars: Int = 600): String {
        val entries = getAll()
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine("=== Persistent memory ===")
            entries.take(15).forEach { appendLine("- [${it.type.name.lowercase()}] ${it.content}") }
            appendLine("=== End persistent memory ===")
        }.take(maxChars)
    }

    private fun readStore(): MemoryStore {
        val raw = settings.getString(MEMORY_KEY, "")
        if (raw.isBlank()) return MemoryStore()
        return runCatching { json.decodeFromString(MemoryStore.serializer(), raw) }
            .getOrElse { MemoryStore() }
    }

    private fun writeStore(store: MemoryStore) {
        settings.putString(MEMORY_KEY, json.encodeToString(MemoryStore.serializer(), store))
    }
}

internal expect fun generateId(): String
internal expect fun currentTimeMs(): Long
