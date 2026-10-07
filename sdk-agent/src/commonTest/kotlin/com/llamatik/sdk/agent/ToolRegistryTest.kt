package com.llamatik.sdk.agent

import com.llamatik.sdk.agent.action.ActionContext
import com.llamatik.sdk.agent.action.ActionResult
import com.llamatik.sdk.agent.registry.ToolRegistry
import com.llamatik.sdk.agent.tools.SupportedPlatform
import com.llamatik.sdk.agent.tools.ToolAvailability
import com.llamatik.sdk.agent.tools.ToolCapability
import com.llamatik.sdk.agent.tools.ToolCategory
import com.llamatik.sdk.agent.tools.ToolDefinition
import com.llamatik.sdk.agent.tools.ToolMetadata
import com.llamatik.sdk.agent.tools.ToolParameter
import com.llamatik.sdk.agent.tools.ToolResult
import com.llamatik.sdk.agent.tools.ToolSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun fakeTool(id: String, available: Boolean = true): ToolDefinition = object : ToolDefinition {
    override val id = id
    override val displayName = id
    override val description = "Test tool $id"
    override val schema = ToolSchema(emptyList())
    override val metadata = ToolMetadata(ToolCategory.UTILITIES, emptySet(), setOf(SupportedPlatform.ALL))
    override val availability = if (available) ToolAvailability.Available else ToolAvailability.unavailable("test")
    override val requiredPermissions = emptySet<String>()
    override suspend fun execute(arguments: Map<String, String>): ToolResult = ToolResult.Success("ok")
}

class ToolRegistryTest {

    @Test
    fun registerAndGetTool() {
        val registry = ToolRegistry()
        val tool = fakeTool("calc")
        registry.registerTool(tool)
        assertNotNull(registry.get("calc"))
    }

    @Test
    fun unknownToolReturnsNull() {
        val registry = ToolRegistry()
        assertNull(registry.get("nonexistent"))
    }

    @Test
    fun availableToolsFiltersUnavailable() {
        val registry = ToolRegistry()
        registry.registerTool(fakeTool("a", available = true))
        registry.registerTool(fakeTool("b", available = false))
        val available = registry.availableTools()
        assertEquals(1, available.size)
        assertEquals("a", available[0].id)
    }

    @Test
    fun unregisterRemovesTool() {
        val registry = ToolRegistry()
        registry.registerTool(fakeTool("x"))
        registry.unregisterTool("x")
        assertNull(registry.get("x"))
    }

    @Test
    fun allToolsIncludesUnavailable() {
        val registry = ToolRegistry()
        registry.registerTool(fakeTool("a", true))
        registry.registerTool(fakeTool("b", false))
        assertEquals(2, registry.allTools().size)
    }
}
