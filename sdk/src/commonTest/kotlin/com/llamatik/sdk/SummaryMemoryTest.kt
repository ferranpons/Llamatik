package com.llamatik.sdk

import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.framework.SummaryMemory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryMemoryTest {

    private fun fakeSummarizer(summary: String): suspend (String) -> String = { summary }

    @Test
    fun noCompressionBelowTrigger() = runTest {
        val memory = SummaryMemory(triggerSize = 10, compressCount = 4, summarizer = fakeSummarizer("summary"))
        repeat(5) { memory.add(ChatMessage(ChatMessage.Role.User, "msg$it")) }
        memory.maybeCompress()
        assertEquals(5, memory.size())
    }

    @Test
    fun compressesOldMessagesAtTrigger() = runTest {
        val memory = SummaryMemory(triggerSize = 4, compressCount = 2, summarizer = fakeSummarizer("compressed"))
        repeat(4) { memory.add(ChatMessage(ChatMessage.Role.User, "msg$it")) }
        assertEquals(4, memory.size())
        memory.maybeCompress()
        // 4 messages → 2 compressed into 1 summary, 2 remain → total 3
        assertEquals(3, memory.size())
        assertTrue(memory.messages()[0].content.startsWith("[Summary]"))
    }

    @Test
    fun systemMessageIsPinnedDuringCompression() = runTest {
        val memory = SummaryMemory(triggerSize = 4, compressCount = 2, summarizer = fakeSummarizer("compressed"))
        memory.add(ChatMessage(ChatMessage.Role.System, "sys"))
        repeat(3) { memory.add(ChatMessage(ChatMessage.Role.User, "msg$it")) }
        memory.maybeCompress()
        // System message stays at index 0
        assertEquals(ChatMessage.Role.System, memory.messages()[0].role)
        assertEquals("sys", memory.messages()[0].content)
    }

    @Test
    fun summaryContentIncludesSummarizerOutput() = runTest {
        val memory = SummaryMemory(triggerSize = 2, compressCount = 2, summarizer = fakeSummarizer("key fact"))
        memory.add(ChatMessage(ChatMessage.Role.User, "hello"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "hi"))
        memory.maybeCompress()
        assertTrue(memory.messages().any { "[Summary] key fact" in it.content })
    }

    @Test
    fun clearResets() = runTest {
        val memory = SummaryMemory(triggerSize = 2, compressCount = 2, summarizer = fakeSummarizer("x"))
        memory.add(ChatMessage(ChatMessage.Role.User, "msg"))
        memory.clear()
        assertEquals(0, memory.size())
    }
}
