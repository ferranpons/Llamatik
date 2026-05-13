package com.llamatik.app.feature.agent

import com.llamatik.app.feature.entitlement.EntitlementRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Orchestrates tool execution: entitlement check → permission check → confirmation → execute → log.
class AgentExecutor(
    private val toolRegistry: ToolRegistry,
    private val permissionRepository: ToolPermissionRepository,
    private val logRepository: AgentActionLogRepository,
    private val entitlementRepository: EntitlementRepository,
    // Called when a tool requires user confirmation before execution.
    // Returns true if the user confirmed, false to cancel.
    private val requestConfirmation: suspend (toolId: String, inputSummary: String) -> Boolean,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun execute(call: ParsedToolCall): AgentToolResult {
        val tool = toolRegistry.get(call.toolId)
            ?: return AgentToolResult.Failure("Unknown tool: ${call.toolId}")

        if (!permissionRepository.isAgentEnabled()) {
            return AgentToolResult.PermissionDenied
        }

        if (!entitlementRepository.canUseAgentTools()) {
            return AgentToolResult.PermissionDenied
        }

        if (!tool.isSupported()) {
            logAndReturn(call, AgentActionStatus.UNSUPPORTED, "Tool not supported on this platform")
            return AgentToolResult.Unsupported
        }

        if (!permissionRepository.isGranted(call.toolId)) {
            logAndReturn(call, AgentActionStatus.DENIED, "Tool not granted by user")
            return AgentToolResult.PermissionDenied
        }

        val inputSummary = call.input.toString().take(200)

        if (permissionRepository.requiresConfirmation(call.toolId)) {
            val confirmed = requestConfirmation(call.toolId, inputSummary)
            if (!confirmed) {
                logAndReturn(call, AgentActionStatus.DENIED, "User cancelled confirmation")
                return AgentToolResult.PermissionDenied
            }
        }

        return runCatching { tool.execute(call.input) }
            .onSuccess { result ->
                val status = when (result) {
                    is AgentToolResult.Success -> AgentActionStatus.SUCCESS
                    is AgentToolResult.Failure -> AgentActionStatus.FAILURE
                    AgentToolResult.Unsupported -> AgentActionStatus.UNSUPPORTED
                    AgentToolResult.PermissionDenied -> AgentActionStatus.DENIED
                }
                val errorMsg = (result as? AgentToolResult.Failure)?.errorMessage
                logAndReturn(call, status, errorMsg)
            }
            .onFailure { e ->
                logAndReturn(call, AgentActionStatus.FAILURE, e.message)
            }
            .getOrElse { AgentToolResult.Failure(it.message ?: "Unknown error") }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun logAndReturn(
        call: ParsedToolCall,
        status: AgentActionStatus,
        errorMessage: String? = null,
    ) {
        logRepository.append(
            AgentActionLog(
                id = "${call.toolId}_${Clock.System.now().toEpochMilliseconds()}",
                toolId = call.toolId,
                inputSummary = call.input.toString().take(200),
                status = status,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                errorMessage = errorMessage,
            )
        )
    }
}
