package com.llamatik.sdk.framework

import com.llamatik.sdk.chat.ChatMessage

interface Memory {
    fun messages(): List<ChatMessage>
    fun add(message: ChatMessage)
    fun clear()
    fun size(): Int

    /** Optional: persist current messages to durable storage. No-op by default. */
    suspend fun persist() {}

    /** Optional: restore messages from durable storage. No-op by default. */
    suspend fun restore() {}
}

class ConversationMemory : Memory {
    private val store = mutableListOf<ChatMessage>()

    override fun messages(): List<ChatMessage> = store.toList()
    override fun add(message: ChatMessage) { store += message }
    override fun clear() { store.clear() }
    override fun size(): Int = store.size
}

class SlidingWindowMemory(private val windowSize: Int) : Memory {
    private val store = mutableListOf<ChatMessage>()

    override fun messages(): List<ChatMessage> = store.toList()

    override fun add(message: ChatMessage) {
        store += message
        if (store.size > windowSize) {
            // Always keep the first (system) message if present
            val hasSystem = store.firstOrNull()?.role == ChatMessage.Role.System
            if (hasSystem && store.size > 1) {
                store.removeAt(1)
            } else {
                store.removeAt(0)
            }
        }
    }

    override fun clear() { store.clear() }
    override fun size(): Int = store.size
}

object NoMemory : Memory {
    override fun messages(): List<ChatMessage> = emptyList()
    override fun add(message: ChatMessage) = Unit
    override fun clear() = Unit
    override fun size(): Int = 0
}

/**
 * A [ConversationMemory] that serializes/deserializes its messages to [MemoryStorage].
 *
 * Messages are stored as newline-delimited "<role>\t<content>" entries under [storageKey].
 * Call [restore] once after creation to load a previously saved session.
 * Call [persist] after any turn you want to survive a restart.
 */
class PersistentMemory(
    private val storage: MemoryStorage,
    private val storageKey: String = "memory/conversation.txt",
    private val maxEntries: Int = 200,
) : Memory {
    private val store = mutableListOf<ChatMessage>()

    override fun messages(): List<ChatMessage> = store.toList()

    override fun add(message: ChatMessage) {
        store += message
        if (store.size > maxEntries) {
            val hasSystem = store.firstOrNull()?.role == ChatMessage.Role.System
            if (hasSystem && store.size > 1) store.removeAt(1) else store.removeAt(0)
        }
    }

    override fun clear() { store.clear() }
    override fun size(): Int = store.size

    override suspend fun persist() {
        val bytes = store.joinToString("\n") { "${it.role.name}\t${it.content.replace("\n", "\\n")}" }
            .encodeToByteArray()
        storage.write(storageKey, bytes)
    }

    override suspend fun restore() {
        val bytes = storage.read(storageKey) ?: return
        store.clear()
        bytes.decodeToString().lines().filter { it.isNotBlank() }.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) return@forEach
            val role = runCatching { ChatMessage.Role.valueOf(line.substring(0, tab)) }.getOrNull() ?: return@forEach
            val content = line.substring(tab + 1).replace("\\n", "\n")
            store += ChatMessage(role, content)
        }
    }
}

/**
 * A memory that compresses old messages into a rolling summary once the window exceeds [triggerSize].
 *
 * When the message count hits [triggerSize], the oldest [compressCount] messages (excluding a pinned
 * system message) are passed to [summarizer] which returns a compact summary string. That summary
 * replaces the compressed messages as a single [ChatMessage.Role.User] message prefixed with
 * "[Summary] ". The system message (if first) is always retained.
 *
 * @param triggerSize  compress when store reaches this many messages (default 40)
 * @param compressCount  how many messages to compress in one pass (default 20)
 * @param summarizer  suspend function that receives a prompt asking for a summary and returns the summary text
 */
class SummaryMemory(
    private val triggerSize: Int = 40,
    private val compressCount: Int = 20,
    private val summarizer: suspend (prompt: String) -> String,
) : Memory {
    private val store = mutableListOf<ChatMessage>()

    override fun messages(): List<ChatMessage> = store.toList()

    override fun add(message: ChatMessage) {
        store += message
    }

    override fun clear() { store.clear() }
    override fun size(): Int = store.size

    /**
     * Compress the oldest [compressCount] messages into a summary if the store has grown past
     * [triggerSize]. Safe to call after every turn (cheap when store is small).
     */
    suspend fun maybeCompress() {
        if (store.size < triggerSize) return

        val hasSystem = store.firstOrNull()?.role == ChatMessage.Role.System
        val offset = if (hasSystem) 1 else 0
        val available = store.size - offset
        if (available <= 0) return

        val toCompress = store.subList(offset, offset + minOf(compressCount, available)).toList()
        val candidateText = toCompress.joinToString("\n") { "[${it.role.name}] ${it.content}" }
        val prompt = "Summarize the following conversation excerpt concisely (2-4 sentences), " +
            "preserving key facts and decisions:\n\n$candidateText"

        val summary = runCatching { summarizer(prompt) }.getOrElse { return }
        if (summary.isBlank()) return

        store.subList(offset, offset + toCompress.size).clear()
        store.add(offset, ChatMessage(ChatMessage.Role.User, "[Summary] $summary"))
    }
}
