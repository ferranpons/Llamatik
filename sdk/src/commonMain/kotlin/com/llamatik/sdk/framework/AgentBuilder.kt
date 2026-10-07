package com.llamatik.sdk.framework

import com.llamatik.sdk.agent.AgentExecutor
import com.llamatik.sdk.agent.AgentTool
import com.llamatik.sdk.agent.ToolRegistry
import com.llamatik.sdk.agent.registerBuiltIns
import com.llamatik.sdk.chat.Gemma3
import com.llamatik.sdk.chat.PromptTemplate
import com.llamatik.sdk.rag.VectorStoreData

@DslMarker
annotation class AgentDsl

typealias Tool = AgentTool

@AgentDsl
class ToolsScope(private val registry: ToolRegistry) {
    fun register(tool: AgentTool) = registry.register(tool)
    fun builtIns() = registry.registerBuiltIns()
    fun calculator() = registry.register(com.llamatik.sdk.agent.CalculatorTool())
    fun datetime() = registry.register(com.llamatik.sdk.agent.DateTimeTool())
    fun uuid() = registry.register(com.llamatik.sdk.agent.UuidTool())
    fun random() = registry.register(com.llamatik.sdk.agent.RandomTool())
}

@AgentDsl
class AgentBuilder {
    private var name: String = "Agent"
    private var description: String = ""
    private var modelPath: String? = null
    private var systemPrompt: String = "You are a helpful AI assistant."
    private var temperature: Float = 0.7f
    private var maxTokens: Int = 1024
    private var contextLength: Int = 4096
    private var template: PromptTemplate = Gemma3
    private var memory: Memory = ConversationMemory()
    private var ragStore: VectorStoreData? = null
    private var pipeline: PromptPipeline = PromptPipeline.Default
    private var executor: AgentExecutor? = null
    private var maxToolRounds: Int = 5
    private val toolRegistry = ToolRegistry()

    fun name(value: String) { name = value }
    fun description(value: String) { description = value }
    fun model(path: String) { modelPath = path }
    fun systemPrompt(value: String) { systemPrompt = value }
    fun temperature(value: Float) { temperature = value }
    fun maxTokens(value: Int) { maxTokens = value }
    fun contextLength(value: Int) { contextLength = value }
    fun template(value: PromptTemplate) { template = value }
    fun memory(value: Memory) { memory = value }
    fun persistentMemory(storage: MemoryStorage, key: String = "memory/conversation.txt", maxEntries: Int = 200) {
        memory = PersistentMemory(storage, key, maxEntries)
    }
    fun summaryMemory(triggerSize: Int = 40, compressCount: Int = 20, summarizer: suspend (String) -> String) {
        memory = SummaryMemory(triggerSize, compressCount, summarizer)
    }
    fun ragStore(value: VectorStoreData) { ragStore = value }
    fun pipeline(value: PromptPipeline) { pipeline = value }
    fun executor(value: AgentExecutor) { executor = value }
    fun maxToolRounds(value: Int) { maxToolRounds = value }

    fun tools(block: ToolsScope.() -> Unit) {
        ToolsScope(toolRegistry).block()
    }

    fun tools(registry: ToolRegistry) {
        registry.all().forEach { toolRegistry.register(it) }
    }

    internal fun build(): AgentConfig = AgentConfig(
        name = name,
        description = description,
        modelPath = modelPath,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        contextLength = contextLength,
        template = template,
        memory = memory,
        toolRegistry = toolRegistry,
        ragStore = ragStore,
        pipeline = pipeline,
        executor = executor,
        maxToolRounds = maxToolRounds,
    )
}

fun Agent(block: AgentBuilder.() -> Unit): Agent {
    val config = AgentBuilder().apply(block).build()
    return Agent(config)
}

