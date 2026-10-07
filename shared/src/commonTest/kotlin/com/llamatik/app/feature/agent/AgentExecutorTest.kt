package com.llamatik.app.feature.agent

import com.llamatik.app.feature.entitlement.MobileEntitlementRepository
import com.llamatik.app.feature.entitlement.UnlockedEntitlementRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertIs

private class FakeTool(
    override val id: String = "fake_tool",
    private val result: AgentToolResult = AgentToolResult.Success("ok"),
    private val supported: Boolean = true,
) : AgentTool {
    override val displayName = "Fake Tool"
    override val description = "A test tool"
    override val schema = JsonObject(emptyMap())
    override fun isSupported() = supported
    override suspend fun execute(input: JsonObject) = result
}

class AgentExecutorTest {

    private fun makeExecutor(
        confirmResult: Boolean = true,
        tool: AgentTool = FakeTool(),
        agentEnabled: Boolean = true,
        toolGranted: Boolean = true,
        requiresConfirmation: Boolean = false,
        entitlementUnlocked: Boolean = true,
    ): Pair<AgentExecutor, ParsedToolCall> {
        val settings = MapSettings()
        val registry = ToolRegistry().also { it.register(tool) }
        val permRepo = ToolPermissionRepository(settings).also { repo ->
            repo.setAgentEnabled(agentEnabled)
            repo.setPermission(
                ToolPermission(
                    toolId = tool.id,
                    granted = toolGranted,
                    requiresConfirmation = requiresConfirmation,
                )
            )
        }
        val logRepo = AgentActionLogRepository(settings)
        val entitlement = if (entitlementUnlocked) UnlockedEntitlementRepository()
        else MobileEntitlementRepository(settings)

        val executor = AgentExecutor(
            toolRegistry = registry,
            permissionRepository = permRepo,
            logRepository = logRepo,
            entitlementRepository = entitlement,
            requestConfirmation = { _, _ -> confirmResult },
        )
        val call = ParsedToolCall(toolId = tool.id, input = JsonObject(mapOf("key" to JsonPrimitive("val"))))
        return executor to call
    }

    @Test
    fun execute_success() = runTest {
        val (executor, call) = makeExecutor()
        assertIs<AgentToolResult.Success>(executor.execute(call))
    }

    @Test
    fun execute_agentDisabled_denied() = runTest {
        val (executor, call) = makeExecutor(agentEnabled = false)
        assertIs<AgentToolResult.PermissionDenied>(executor.execute(call))
    }

    @Test
    fun execute_entitlementLocked_denied() = runTest {
        val (executor, call) = makeExecutor(entitlementUnlocked = false)
        assertIs<AgentToolResult.PermissionDenied>(executor.execute(call))
    }

    @Test
    fun execute_toolNotGranted_denied() = runTest {
        val (executor, call) = makeExecutor(toolGranted = false)
        assertIs<AgentToolResult.PermissionDenied>(executor.execute(call))
    }

    @Test
    fun execute_unsupportedPlatform_unsupported() = runTest {
        val (executor, call) = makeExecutor(tool = FakeTool(supported = false))
        assertIs<AgentToolResult.Unsupported>(executor.execute(call))
    }

    @Test
    fun execute_confirmationRequired_userCancels_denied() = runTest {
        val (executor, call) = makeExecutor(confirmResult = false, requiresConfirmation = true)
        assertIs<AgentToolResult.PermissionDenied>(executor.execute(call))
    }

    @Test
    fun execute_confirmationRequired_userConfirms_success() = runTest {
        val (executor, call) = makeExecutor(confirmResult = true, requiresConfirmation = true)
        assertIs<AgentToolResult.Success>(executor.execute(call))
    }

    @Test
    fun execute_unknownTool_failure() = runTest {
        val settings = MapSettings()
        val registry = ToolRegistry()
        val permRepo = ToolPermissionRepository(settings).also { it.setAgentEnabled(true) }
        val logRepo = AgentActionLogRepository(settings)
        val executor = AgentExecutor(registry, permRepo, logRepo, UnlockedEntitlementRepository()) { _, _ -> true }
        val call = ParsedToolCall("nonexistent", JsonObject(emptyMap()))
        assertIs<AgentToolResult.Failure>(executor.execute(call))
    }
}
