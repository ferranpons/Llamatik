package com.llamatik.sdk.framework

import com.llamatik.sdk.chat.ChatMessage

interface Memory {
    fun messages(): List<ChatMessage>
    fun add(message: ChatMessage)
    fun clear()
    fun size(): Int
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
