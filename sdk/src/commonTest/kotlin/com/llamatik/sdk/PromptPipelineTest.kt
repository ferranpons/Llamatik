package com.llamatik.sdk

import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.framework.ConversationMemory
import com.llamatik.sdk.framework.NoMemory
import com.llamatik.sdk.framework.PromptPipeline
import com.llamatik.sdk.framework.PromptStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptPipelineTest {

    @Test
    fun defaultPipelineProducesSystemAndUserMessage() {
        val pipeline = PromptPipeline(
            listOf(
                PromptStage.SystemStage("You are helpful."),
                PromptStage.MemoryStage,
                PromptStage.UserMessageStage(""),
            )
        )
        val messages = pipeline.build("Hello", NoMemory)
        val system = messages.filter { it.role == ChatMessage.Role.System }
        val user = messages.filter { it.role == ChatMessage.Role.User }
        assertEquals(1, system.size)
        assertEquals("You are helpful.", system[0].content)
        assertEquals(1, user.size)
        assertEquals("Hello", user[0].content)
    }

    @Test
    fun ragContextsInjectedFromRagStage() {
        val pipeline = PromptPipeline(
            listOf(
                PromptStage.SystemStage("System"),
                PromptStage.RagStage(emptyList()),
                PromptStage.UserMessageStage(""),
            )
        )
        val messages = pipeline.build("question", NoMemory, ragContexts = listOf("context chunk"))
        val systems = messages.filter { it.role == ChatMessage.Role.System }
        assertTrue(systems.any { it.content.contains("context chunk") })
    }

    @Test
    fun memoryMessagesAreInjectedInOrder() {
        val memory = ConversationMemory()
        memory.add(ChatMessage(ChatMessage.Role.User, "first"))
        memory.add(ChatMessage(ChatMessage.Role.Assistant, "reply"))

        val pipeline = PromptPipeline(
            listOf(
                PromptStage.SystemStage("sys"),
                PromptStage.MemoryStage,
                PromptStage.UserMessageStage(""),
            )
        )
        val messages = pipeline.build("new msg", memory)
        val userMessages = messages.filter { it.role == ChatMessage.Role.User }
        assertEquals("first", userMessages[0].content)
        assertEquals("new msg", userMessages[1].content)
    }

    @Test
    fun emptyRagContextsProduceNoRagBlock() {
        val pipeline = PromptPipeline(
            listOf(
                PromptStage.SystemStage("sys"),
                PromptStage.RagStage(emptyList()),
                PromptStage.UserMessageStage(""),
            )
        )
        val messages = pipeline.build("q", NoMemory, ragContexts = emptyList())
        val systems = messages.filter { it.role == ChatMessage.Role.System }
        assertEquals(1, systems.size)
        assertEquals("sys", systems[0].content)
    }
}
