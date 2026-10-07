package com.llamatik.sdk.agent

class ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.id] = tool
    }

    fun get(id: String): AgentTool? = tools[id]

    fun all(): List<AgentTool> = tools.values.toList()

    fun supported(): List<AgentTool> = tools.values.filter { it.isSupported() }
}
