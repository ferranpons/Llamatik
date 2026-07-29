package com.llamatik.sdk.framework

import com.llamatik.sdk.agent.AgentExecutor
import com.llamatik.sdk.agent.ToolRegistry
import com.llamatik.sdk.chat.Gemma3
import com.llamatik.sdk.chat.PromptTemplate
import com.llamatik.sdk.rag.VectorStoreData

data class AgentConfig(
    val name: String = "Agent",
    val description: String = "",
    val modelPath: String? = null,
    val systemPrompt: String = "You are a helpful AI assistant.",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val contextLength: Int = 4096,
    val template: PromptTemplate = Gemma3,
    val memory: Memory = ConversationMemory(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val ragStore: VectorStoreData? = null,
    val pipeline: PromptPipeline = PromptPipeline.Default,
    val executor: AgentExecutor? = null,
    val maxToolRounds: Int = 5,
)
