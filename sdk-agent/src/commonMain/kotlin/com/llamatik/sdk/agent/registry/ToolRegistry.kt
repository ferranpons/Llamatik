package com.llamatik.sdk.agent.registry

import com.llamatik.sdk.agent.tools.ToolDefinition

class ToolRegistry {
    private val tools = mutableMapOf<String, ToolDefinition>()

    fun registerTool(tool: ToolDefinition) {
        tools[tool.id] = tool
    }

    fun unregisterTool(id: String) {
        tools.remove(id)
    }

    fun get(id: String): ToolDefinition? = tools[id]

    fun availableTools(): List<ToolDefinition> = tools.values.filter { it.availability.isAvailable() }

    fun allTools(): List<ToolDefinition> = tools.values.toList()
}
