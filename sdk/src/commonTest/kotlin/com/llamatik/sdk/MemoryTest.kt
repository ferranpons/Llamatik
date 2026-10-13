package com.llamatik.sdk

import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.framework.ConversationMemory
import com.llamatik.sdk.framework.SlidingWindowMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryTest {

    @Test
    fun conversationMemoryAddsAndReturnsMessages() {
        val memory = ConversationMemory()
        memory.add(ChatMessage(ChatMessage.Role.User, "hello"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "hi"))
        assertEquals(2, memory.size())
        assertEquals("hello", memory.messages()[0].content)
        assertEquals("hi", memory.messages()[1].content)
    }

    @Test
    fun conversationMemoryClear() {
        val memory = ConversationMemory()
        memory.add(ChatMessage(ChatMessage.Role.User, "hello"))
        memory.clear()
        assertEquals(0, memory.size())
        assertTrue(memory.messages().isEmpty())
    }

    @Test
    fun slidingWindowEvidesOldestAfterWindow() {
        val memory = SlidingWindowMemory(3)
        memory.add(ChatMessage(ChatMessage.Role.User, "msg1"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "resp1"))
        memory.add(ChatMessage(ChatMessage.Role.User, "msg2"))
        assertEquals(3, memory.size())
        // Adding a 4th should evict the oldest non-system message
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "resp2"))
        assertEquals(3, memory.size())
        assertEquals("resp1", memory.messages()[0].content)
    }

    @Test
    fun slidingWindowPinsSystemMessage() {
        val memory = SlidingWindowMemory(3)
        memory.add(ChatMessage(ChatMessage.Role.System, "system"))
        memory.add(ChatMessage(ChatMessage.Role.User, "msg1"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "resp1"))
        // Adding a 4th should evict msg1, keeping system pinned
        memory.add(ChatMessage(ChatMessage.Role.User, "msg2"))
        assertEquals(3, memory.size())
        assertEquals(ChatMessage.Role.System, memory.messages()[0].role)
    }
}
