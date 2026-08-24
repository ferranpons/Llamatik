package com.llamatik.sdk

import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.framework.MemoryStorage
import com.llamatik.sdk.framework.PersistentMemory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemoryStorage : MemoryStorage {
    private val map = mutableMapOf<String, ByteArray>()
    override suspend fun write(key: String, bytes: ByteArray) { map[key] = bytes }
    override suspend fun read(key: String): ByteArray? = map[key]
    override suspend fun delete(key: String) { map.remove(key) }
}

class PersistentMemoryTest {

    @Test
    fun persistAndRestoreRoundTrip() = runTest {
        val storage = InMemoryStorage()
        val memory = PersistentMemory(storage)
        memory.add(ChatMessage(ChatMessage.Role.System, "You are helpful."))
        memory.add(ChatMessage(ChatMessage.Role.User, "Hello"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "Hi there!"))
        memory.persist()

        val restored = PersistentMemory(storage)
        restored.restore()
        assertEquals(3, restored.size())
        assertEquals(ChatMessage.Role.System, restored.messages()[0].role)
        assertEquals("You are helpful.", restored.messages()[0].content)
        assertEquals("Hello", restored.messages()[1].content)
        assertEquals("Hi there!", restored.messages()[2].content)
    }

    @Test
    fun restoreOnEmptyStorageIsNoop() = runTest {
        val memory = PersistentMemory(InMemoryStorage())
        memory.restore()
        assertEquals(0, memory.size())
    }

    @Test
    fun clearAndPersistErasesStorage() = runTest {
        val storage = InMemoryStorage()
        val memory = PersistentMemory(storage)
        memory.add(ChatMessage(ChatMessage.Role.User, "test"))
        memory.persist()
        memory.clear()
        memory.persist()

        val restored = PersistentMemory(storage)
        restored.restore()
        assertEquals(0, restored.size())
    }

    @Test
    fun newlinesInContentSurviveRoundTrip() = runTest {
        val storage = InMemoryStorage()
        val memory = PersistentMemory(storage)
        memory.add(ChatMessage(ChatMessage.Role.User, "line1\nline2\nline3"))
        memory.persist()

        val restored = PersistentMemory(storage)
        restored.restore()
        assertEquals("line1\nline2\nline3", restored.messages()[0].content)
    }

    @Test
    fun maxEntriesCapIsEnforced() = runTest {
        val memory = PersistentMemory(InMemoryStorage(), maxEntries = 3)
        repeat(5) { memory.add(ChatMessage(ChatMessage.Role.User, "msg$it")) }
        assertEquals(3, memory.size())
    }
}
