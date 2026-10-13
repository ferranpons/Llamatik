package com.llamatik.sdk.agent.runtime

import co.touchlab.kermit.Logger
import com.llamatik.sdk.agent.audit.AgentAuditEntry
import com.llamatik.sdk.agent.audit.AgentAuditRepository
import com.llamatik.sdk.agent.confirmation.ConfirmationHandler
import com.llamatik.sdk.agent.confirmation.ConfirmationPolicy
import com.llamatik.sdk.agent.confirmation.ConfirmationRequest
import com.llamatik.sdk.agent.confirmation.RiskLevel
import com.llamatik.sdk.agent.permissions.PermissionManager
import com.llamatik.sdk.agent.permissions.PermissionState
import com.llamatik.sdk.agent.registry.ActionRegistry
import com.llamatik.sdk.agent.registry.ToolRegistry
import com.llamatik.sdk.agent.action.ActionContext
import com.llamatik.sdk.agent.action.ActionResult
import com.llamatik.sdk.agent.tools.ToolResult
import kotlin.time.ExperimentalTime
import kotlin.time.Clock

class AgentExecutionEngine(
    private val toolRegistry: ToolRegistry,
    private val actionRegistry: ActionRegistry,
    private val permissionManager: PermissionManager,
    private val confirmationPolicy: ConfirmationPolicy,
    private val confirmationHandler: ConfirmationHandler,
    private val auditRepository: AgentAuditRepository,
    private val platformId: String = "unknown",
) {
    @OptIn(ExperimentalTime::class)
    suspend fun execute(
        step: ExecutionStep,
        sessionId: String,
    ): ExecutionResult {
        val startMs = Clock.System.now().toEpochMilliseconds()

        val tool = toolRegistry.get(step.toolId)
            ?: return ExecutionResult(
                stepId = step.stepId,
                toolId = step.toolId,
                status = ExecutionStatus.FAILED,
                outputSummary = "",
                errorMessage = "Unknown tool: ${step.toolId}",
            )

        // Check platform availability
        if (!tool.availability.isAvailable()) {
            return audit(step, sessionId, ExecutionStatus.UNSUPPORTED,
                "Tool not available: ${tool.availability.reason}", startMs)
        }

        // Permission validation
        val permissionIds = tool.requiredPermissions
        val decisions = permissionManager.checkAll(permissionIds)
        val denied = decisions.entries.firstOrNull { it.value.state == PermissionState.DENIED }
        if (denied != null) {
            return audit(step, sessionId, ExecutionStatus.DENIED,
                "Permission denied: ${denied.key}", startMs)
        }

        // Confirmation gate
        val riskLevel = runCatching { RiskLevel.valueOf(tool.metadata.riskLevel) }.getOrElse { RiskLevel.LOW }
        if (confirmationPolicy.requiresConfirmation(step.toolId, riskLevel)) {
            val request = ConfirmationRequest(
                toolId = step.toolId,
                toolDisplayName = tool.displayName,
                humanReadableDescription = tool.description,
                argumentsSummary = step.arguments,
                riskLevel = riskLevel,
                sessionId = sessionId,
            )
            val confirmed = runCatching { confirmationHandler.requestConfirmation(request) }.getOrElse { false }
            if (!confirmed) {
                return audit(step, sessionId, ExecutionStatus.CANCELLED,
                    "User cancelled confirmation", startMs)
            }
        }

        // Resolve and validate action
        val action = actionRegistry.get(step.toolId)
        val actionCtx = ActionContext(step.arguments, sessionId, step.toolId)

        if (action != null) {
            val validation = action.validate(actionCtx)
            if (!validation.valid) {
                return audit(step, sessionId, ExecutionStatus.FAILED,
                    validation.errorMessage ?: "Validation failed", startMs)
            }
        }

        // Execute via action if available, otherwise via tool directly
        val toolResult: ToolResult = if (action != null) {
            when (val ar = runCatching { action.execute(actionCtx) }.getOrElse { ActionResult.Failure(it.message ?: "Action error") }) {
                is ActionResult.Success -> ToolResult.Success(ar.summary, ar.data)
                is ActionResult.Failure -> ToolResult.Failure(ar.message)
                ActionResult.Unsupported -> ToolResult.Unsupported
                ActionResult.PermissionDenied -> ToolResult.PermissionDenied
            }
        } else {
            runCatching { tool.execute(step.arguments) }
                .getOrElse { ToolResult.Failure(it.message ?: "Tool execution error") }
        }

        val (status, summary, error) = when (toolResult) {
            is ToolResult.Success -> Triple(ExecutionStatus.SUCCEEDED, toolResult.summary, null)
            is ToolResult.Failure -> Triple(ExecutionStatus.FAILED, "", toolResult.message)
            ToolResult.Unsupported -> Triple(ExecutionStatus.UNSUPPORTED, "", "Unsupported on this platform")
            ToolResult.PermissionDenied -> Triple(ExecutionStatus.DENIED, "", "Permission denied")
        }

        return audit(step, sessionId, status, error, startMs, summary)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun audit(
        step: ExecutionStep,
        sessionId: String,
        status: ExecutionStatus,
        error: String?,
        startMs: Long,
        outputSummary: String = "",
    ): ExecutionResult {
        val endMs = Clock.System.now().toEpochMilliseconds()
        val tool = toolRegistry.get(step.toolId)
        runCatching {
            auditRepository.append(
                AgentAuditEntry(
                    id = "${step.toolId}_$endMs",
                    toolId = step.toolId,
                    toolDisplayName = tool?.displayName ?: step.toolId,
                    argumentsSummary = step.arguments.entries.joinToString(", ") { "${it.key}=${it.value.take(50)}" },
                    durationMs = endMs - startMs,
                    success = status == ExecutionStatus.SUCCEEDED,
                    failure = error,
                    platform = platformId,
                    riskLevel = tool?.metadata?.riskLevel ?: "LOW",
                    createdAtMs = endMs,
                    sessionId = sessionId,
                )
            )
        }
        Logger.d("ExecutionEngine: step='${step.stepId}' status=$status durationMs=${endMs - startMs}")
        return ExecutionResult(step.stepId, step.toolId, status, outputSummary, error, endMs - startMs)
    }
}
