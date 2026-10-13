package com.llamatik.sdk

import com.llamatik.sdk.framework.Agent
import com.llamatik.sdk.framework.ConversationMemory
import com.llamatik.sdk.framework.NoMemory
import com.llamatik.sdk.framework.SlidingWindowMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentBuilderTest {

    @Test
    fun agentBuildsWithDefaults() {
        val agent = Agent { }
        assertEquals("Agent", agent.config.name)
        assertEquals("You are a helpful AI assistant.", agent.config.systemPrompt)
        assertEquals(0.7f, agent.config.temperature)
        assertEquals(1024, agent.config.maxTokens)
    }

    @Test
    fun agentNameAndDescriptionAreSet() {
        val agent = Agent {
            name("Kotlin Assistant")
            description("Helps with Kotlin code")
        }
        assertEquals("Kotlin Assistant", agent.config.name)
        assertEquals("Helps with Kotlin code", agent.config.description)
    }

    @Test
    fun agentSystemPromptIsSet() {
        val agent = Agent {
            systemPrompt("You are a Kotlin expert.")
        }
        assertEquals("You are a Kotlin expert.", agent.config.systemPrompt)
    }

    @Test
    fun agentTemperatureAndMaxTokens() {
        val agent = Agent {
            temperature(0.3f)
            maxTokens(512)
        }
        assertEquals(0.3f, agent.config.temperature)
        assertEquals(512, agent.config.maxTokens)
    }

    @Test
    fun agentMemoryIsConversationMemoryByDefault() {
        val agent = Agent { }
        assertTrue(agent.config.memory is ConversationMemory)
    }

    @Test
    fun agentMemoryCanBeCustomised() {
        val agent = Agent {
            memory(SlidingWindowMemory(10))
        }
        assertTrue(agent.config.memory is SlidingWindowMemory)
    }

    @Test
    fun agentToolsBuiltInsRegisterFourTools() {
        val agent = Agent {
            tools { builtIns() }
        }
        val tools = agent.config.toolRegistry.all()
        assertTrue(tools.size >= 4)
        assertNotNull(agent.config.toolRegistry.get("calculator"))
        assertNotNull(agent.config.toolRegistry.get("datetime"))
        assertNotNull(agent.config.toolRegistry.get("uuid"))
        assertNotNull(agent.config.toolRegistry.get("random"))
    }

    @Test
    fun agentToolsIndividualRegistration() {
        val agent = Agent {
            tools {
                calculator()
                datetime()
            }
        }
        assertNotNull(agent.config.toolRegistry.get("calculator"))
        assertNotNull(agent.config.toolRegistry.get("datetime"))
        assertEquals(null, agent.config.toolRegistry.get("uuid"))
    }

    @Test
    fun agentResetClearsMemory() {
        val agent = Agent {
            memory(ConversationMemory())
        }
        agent.config.memory.add(com.llamatik.sdk.chat.ChatMessage(com.llamatik.sdk.chat.ChatMessage.Role.User, "hi"))
        assertEquals(1, agent.memory.size())
        agent.reset()
        assertEquals(0, agent.memory.size())
    }
}
