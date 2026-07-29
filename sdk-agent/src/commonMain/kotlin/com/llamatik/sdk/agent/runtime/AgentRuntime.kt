package com.llamatik.sdk.agent.runtime

import co.touchlab.kermit.Logger
import com.llamatik.sdk.agent.audit.AgentAuditRepository
import com.llamatik.sdk.agent.capability.Capability
import com.llamatik.sdk.agent.capability.PlatformCapabilityProvider
import com.llamatik.sdk.agent.companion.CompanionProfile
import com.llamatik.sdk.agent.companion.CompanionProfiles
import com.llamatik.sdk.agent.confirmation.ConfirmationHandler
import com.llamatik.sdk.agent.confirmation.ConfirmationPolicy
import com.llamatik.sdk.agent.memory.AgentMemory
import com.llamatik.sdk.agent.permissions.PermissionDecision
import com.llamatik.sdk.agent.permissions.PermissionManager
import com.llamatik.sdk.agent.permissions.PermissionRepository
import com.llamatik.sdk.agent.planner.PlannerRequest
import com.llamatik.sdk.agent.planner.PlannerResult
import com.llamatik.sdk.agent.registry.ActionRegistry
import com.llamatik.sdk.agent.registry.CapabilityRegistry
import com.llamatik.sdk.agent.registry.ToolRegistry
import com.llamatik.sdk.agent.registry.WorkflowRegistry
import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.chat.ChatRunner
import com.llamatik.sdk.chat.Gemma3
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AgentRuntimeEvent {
    data object Planning : AgentRuntimeEvent
    data class PlanReady(val plan: ExecutionPlan) : AgentRuntimeEvent
    data class Executing(val stepId: String, val toolId: String) : AgentRuntimeEvent
    data class StepCompleted(val result: ExecutionResult) : AgentRuntimeEvent
    data object GeneratingResponse : AgentRuntimeEvent
    data class ResponseDelta(val chunk: String) : AgentRuntimeEvent
    data class Completed(val response: String, val results: List<ExecutionResult>) : AgentRuntimeEvent
    data class Failed(val message: String) : AgentRuntimeEvent
    data class ConversationalResponse(val text: String) : AgentRuntimeEvent
}

class AgentRuntime private constructor(
    val toolRegistry: ToolRegistry,
    val actionRegistry: ActionRegistry,
    val capabilityRegistry: CapabilityRegistry,
    val workflowRegistry: WorkflowRegistry,
    val permissionManager: PermissionManager,
    val agentMemory: AgentMemory,
    val auditRepository: AgentAuditRepository,
    private val planner: AgentPlanner,
    private val executionEngine: AgentExecutionEngine,
    private val activeProfile: CompanionProfile,
    private val platformId: String,
) {
    fun processMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        sessionId: String,
    ): Flow<AgentRuntimeEvent> = flow {
        val capabilities = capabilityRegistry.available()
        val permissionDecisions = mutableMapOf<String, PermissionDecision>()
        val memoryContext = agentMemory.buildContextBlock()

        val ctx = AgentContext(
            conversationHistory = conversationHistory,
            availableCapabilities = capabilities,
            permissionDecisions = permissionDecisions,
            companionProfile = activeProfile,
            persistentMemory = agentMemory,
            sessionId = sessionId,
            platformId = platformId,
        )

        Logger.d("AgentRuntime: processing message for session $sessionId")
        emit(AgentRuntimeEvent.Planning)

        val plannerRequest = PlannerRequest(
            userMessage = userMessage,
            conversationHistory = ctx.conversationHistory,
            availableCapabilities = ctx.availableCapabilities,
            memoryContext = memoryContext,
            companionSystemPrompt = activeProfile.systemPrompt,
        )

        val plannerResult = planner.plan(plannerRequest)

        when (plannerResult) {
            is PlannerResult.ConversationalResponse -> {
                emit(AgentRuntimeEvent.ConversationalResponse(plannerResult.text))
                return@flow
            }
            is PlannerResult.Failure -> {
                emit(AgentRuntimeEvent.Failed(plannerResult.message))
                return@flow
            }
            is PlannerResult.Plan -> {
                val plan = plannerResult.executionPlan
                emit(AgentRuntimeEvent.PlanReady(plan))

                val executionResults = mutableListOf<ExecutionResult>()
                val stepOutputs = mutableMapOf<String, String>()

                for (step in plan.steps) {
                    // Check dependency outputs
                    val depsReady = step.dependsOn.all { dep ->
                        stepOutputs[dep] != null
                    }
                    if (!depsReady) {
                        Logger.w("AgentRuntime: step '${step.stepId}' skipped — missing dependency outputs")
                        continue
                    }

                    emit(AgentRuntimeEvent.Executing(step.stepId, step.toolId))
                    val result = executionEngine.execute(step, sessionId)
                    executionResults += result
                    if (result.status == ExecutionStatus.SUCCEEDED) {
                        stepOutputs[step.stepId] = result.outputSummary
                    }
                    emit(AgentRuntimeEvent.StepCompleted(result))
                }

                // Ask LLM to generate natural-language response
                emit(AgentRuntimeEvent.GeneratingResponse as AgentRuntimeEvent)
                val finalResponse = generateFinalResponse(
                    userMessage = userMessage,
                    conversationHistory = ctx.conversationHistory,
                    executionResults = executionResults,
                    companionProfile = activeProfile,
                )
                emit(AgentRuntimeEvent.Completed(finalResponse, executionResults))
            }
        }
    }

    private suspend fun generateFinalResponse(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        executionResults: List<ExecutionResult>,
        companionProfile: CompanionProfile,
    ): String {
        val toolSummary = executionResults.joinToString("\n") { result ->
            val statusStr = if (result.status == ExecutionStatus.SUCCEEDED) "✓" else "✗"
            "$statusStr ${result.toolId}: ${result.outputSummary.ifBlank { result.errorMessage ?: result.status.name }}"
        }

        val systemPrompt = """${companionProfile.systemPrompt}

The following tool actions were just executed on behalf of the user. Generate a natural, ${companionProfile.responseStyle} response confirming what was done:

Tool results:
$toolSummary

User asked: "$userMessage"
"""

        val acc = StringBuilder()
        var done = false

        ChatRunner.stream(
            system = systemPrompt,
            messages = conversationHistory.takeLast(6),
            template = Gemma3,
            maxTokens = 256,
            onDelta = { acc.append(it) },
            onComplete = { done = true },
            onError = { done = true },
        )

        return if (acc.isNotBlank()) acc.toString().trim()
        else "Done! I've completed the requested action."
    }

    class Builder {
        private var toolRegistry = ToolRegistry()
        private var actionRegistry = ActionRegistry()
        private var capabilityRegistry = CapabilityRegistry()
        private var workflowRegistry = WorkflowRegistry()
        private var permissionManager: PermissionManager? = null
        private var agentMemory: AgentMemory? = null
        private var auditRepository: AgentAuditRepository? = null
        private var confirmationHandler: ConfirmationHandler = ConfirmationHandler { _ -> false }
        private var confirmationPolicy = ConfirmationPolicy()
        private var companionProfile: CompanionProfile = CompanionProfiles.Assistant
        private var platformId: String = "unknown"
        private var capabilityProvider: PlatformCapabilityProvider? = null

        fun toolRegistry(registry: ToolRegistry) = apply { toolRegistry = registry }
        fun actionRegistry(registry: ActionRegistry) = apply { actionRegistry = registry }
        fun capabilityRegistry(registry: CapabilityRegistry) = apply { capabilityRegistry = registry }
        fun workflowRegistry(registry: WorkflowRegistry) = apply { workflowRegistry = registry }
        fun permissionManager(manager: PermissionManager) = apply { permissionManager = manager }
        fun agentMemory(memory: AgentMemory) = apply { agentMemory = memory }
        fun auditRepository(repo: AgentAuditRepository) = apply { auditRepository = repo }
        fun confirmationHandler(handler: ConfirmationHandler) = apply { confirmationHandler = handler }
        fun confirmationPolicy(policy: ConfirmationPolicy) = apply { confirmationPolicy = policy }
        fun companionProfile(profile: CompanionProfile) = apply { companionProfile = profile }
        fun platformId(id: String) = apply { platformId = id }
        fun capabilityProvider(provider: PlatformCapabilityProvider) = apply { capabilityProvider = provider }

        fun build(): AgentRuntime {
            val pm = permissionManager ?: throw IllegalStateException("PermissionManager is required")
            val memory = agentMemory ?: throw IllegalStateException("AgentMemory is required")
            val audit = auditRepository ?: throw IllegalStateException("AgentAuditRepository is required")

            capabilityProvider?.discoverCapabilities()?.forEach { capabilityRegistry.registerCapability(it) }

            val engine = AgentExecutionEngine(
                toolRegistry = toolRegistry,
                actionRegistry = actionRegistry,
                permissionManager = pm,
                confirmationPolicy = confirmationPolicy,
                confirmationHandler = confirmationHandler,
                auditRepository = audit,
                platformId = platformId,
            )
            val planner = AgentPlanner(toolRegistry)

            return AgentRuntime(
                toolRegistry = toolRegistry,
                actionRegistry = actionRegistry,
                capabilityRegistry = capabilityRegistry,
                workflowRegistry = workflowRegistry,
                permissionManager = pm,
                agentMemory = memory,
                auditRepository = audit,
                planner = planner,
                executionEngine = engine,
                activeProfile = companionProfile,
                platformId = platformId,
            )
        }
    }
}
