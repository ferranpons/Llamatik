package com.llamatik.sdk.agent

import com.llamatik.sdk.agent.action.Action
import com.llamatik.sdk.agent.action.ActionContext
import com.llamatik.sdk.agent.action.ActionResult
import com.llamatik.sdk.agent.action.ActionValidationResult
import com.llamatik.sdk.agent.audit.AgentAuditRepository
import com.llamatik.sdk.agent.confirmation.ConfirmationHandler
import com.llamatik.sdk.agent.confirmation.ConfirmationPolicy
import com.llamatik.sdk.agent.confirmation.RiskLevel
import com.llamatik.sdk.agent.permissions.KnownPermissions
import com.llamatik.sdk.agent.permissions.PermissionManager
import com.llamatik.sdk.agent.permissions.PermissionPolicy
import com.llamatik.sdk.agent.permissions.PermissionRepository
import com.llamatik.sdk.agent.permissions.PermissionState
import com.llamatik.sdk.agent.registry.ActionRegistry
import com.llamatik.sdk.agent.registry.ToolRegistry
import com.llamatik.sdk.agent.runtime.AgentExecutionEngine
import com.llamatik.sdk.agent.runtime.ExecutionStatus
import com.llamatik.sdk.agent.runtime.ExecutionStep
import com.llamatik.sdk.agent.tools.SupportedPlatform
import com.llamatik.sdk.agent.tools.ToolAvailability
import com.llamatik.sdk.agent.tools.ToolCapability
import com.llamatik.sdk.agent.tools.ToolCategory
import com.llamatik.sdk.agent.tools.ToolDefinition
import com.llamatik.sdk.agent.tools.ToolMetadata
import com.llamatik.sdk.agent.tools.ToolResult
import com.llamatik.sdk.agent.tools.ToolSchema
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun testTool(id: String, result: ToolResult = ToolResult.Success("done"), available: Boolean = true): ToolDefinition =
    object : ToolDefinition {
        override val id = id
        override val displayName = id
        override val description = "Test $id"
        override val schema = ToolSchema(emptyList())
        override val metadata = ToolMetadata(ToolCategory.UTILITIES, emptySet(), setOf(SupportedPlatform.ALL), riskLevel = "LOW")
        override val availability = if (available) ToolAvailability.Available else ToolAvailability.unavailable("test")
        override val requiredPermissions = emptySet<String>()
        override suspend fun execute(arguments: Map<String, String>): ToolResult = result
    }

private fun testAction(toolId: String, result: ActionResult = ActionResult.Success("done")): Action =
    object : Action {
        override val id = toolId
        override fun validate(context: ActionContext) = ActionValidationResult(true)
        override suspend fun execute(context: ActionContext): ActionResult = result
        override fun requiredPermissions() = emptySet<String>()
        override fun isSupported() = true
    }

private fun engine(
    toolRegistry: ToolRegistry = ToolRegistry(),
    actionRegistry: ActionRegistry = ActionRegistry(),
    permissionState: PermissionState = PermissionState.GRANTED,
    confirmationResult: Boolean = true,
    confirmAll: Boolean = false,
): AgentExecutionEngine {
    val repo = PermissionRepository(MapSettings())
    val pm = PermissionManager(
        repository = repo,
        policy = PermissionPolicy(
            defaultState = permissionState,
        ),
        platformPermissionCheck = { true },
    )
    val audit = AgentAuditRepository(MapSettings())
    val policy = ConfirmationPolicy(
        alwaysConfirmMedium = confirmAll,
        alwaysConfirmHigh = true,
        alwaysConfirmCritical = true,
        toolOverrides = if (confirmAll) mapOf("risky" to true) else emptyMap(),
    )
    return AgentExecutionEngine(
        toolRegistry = toolRegistry,
        actionRegistry = actionRegistry,
        permissionManager = pm,
        confirmationPolicy = policy,
        confirmationHandler = ConfirmationHandler { _ -> confirmationResult },
        auditRepository = audit,
    )
}

class ExecutionEngineTest {

    @Test
    fun unknownToolReturnsFailed() = runTest {
        val e = engine()
        val result = e.execute(ExecutionStep("no.tool", emptyMap()), "session1")
        assertEquals(ExecutionStatus.FAILED, result.status)
    }

    @Test
    fun unavailableToolReturnsUnsupported() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(testTool("x", available = false))
        val e = engine(toolRegistry = tr)
        val result = e.execute(ExecutionStep("x", emptyMap()), "session1")
        assertEquals(ExecutionStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun successfulToolExecution() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(testTool("calc", ToolResult.Success("42")))
        val e = engine(toolRegistry = tr)
        val result = e.execute(ExecutionStep("calc", emptyMap()), "session1")
        assertEquals(ExecutionStatus.SUCCEEDED, result.status)
        assertEquals("42", result.outputSummary)
    }

    @Test
    fun failedToolReturnsFailedStatus() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(testTool("bad", ToolResult.Failure("oops")))
        val e = engine(toolRegistry = tr)
        val result = e.execute(ExecutionStep("bad", emptyMap()), "session1")
        assertEquals(ExecutionStatus.FAILED, result.status)
    }

    @Test
    fun deniedPermissionBlocksExecution() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(object : ToolDefinition by testTool("cal") {
            override val requiredPermissions = setOf(KnownPermissions.CALENDAR)
        })
        val e = engine(toolRegistry = tr, permissionState = PermissionState.DENIED)
        val result = e.execute(ExecutionStep("cal", emptyMap()), "session1")
        assertEquals(ExecutionStatus.DENIED, result.status)
    }

    @Test
    fun cancelledConfirmationBlocksExecution() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(testTool("risky", available = true))
        val e = engine(toolRegistry = tr, confirmationResult = false, confirmAll = true)
        val result = e.execute(ExecutionStep("risky", emptyMap()), "session1")
        assertEquals(ExecutionStatus.CANCELLED, result.status)
    }

    @Test
    fun actionDelegatesOverToolDirect() = runTest {
        val tr = ToolRegistry()
        tr.registerTool(testTool("t", ToolResult.Failure("should not reach")))
        val ar = ActionRegistry()
        ar.registerAction(testAction("t", ActionResult.Success("from_action")))
        val e = engine(toolRegistry = tr, actionRegistry = ar)
        val result = e.execute(ExecutionStep("t", emptyMap()), "session1")
        assertEquals(ExecutionStatus.SUCCEEDED, result.status)
        assertEquals("from_action", result.outputSummary)
    }
}
