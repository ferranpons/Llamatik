package com.llamatik.sdk.agent.workflow

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class WorkflowStepStatus { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED }

data class WorkflowStep(
    val id: String,
    val name: String,
    val toolId: String,
    val argumentsBuilder: (Map<String, String>) -> Map<String, String>,
    val condition: ((Map<String, String>) -> Boolean)? = null,
    val retryPolicy: RetryPolicy = RetryPolicy(),
)

data class RetryPolicy(
    val maxAttempts: Int = 1,
    val delayMs: Long = 0,
)

data class WorkflowStepResult(
    val stepId: String,
    val status: WorkflowStepStatus,
    val output: String = "",
    val error: String? = null,
)

data class WorkflowResult(
    val id: String,
    val stepResults: List<WorkflowStepResult>,
    val completed: Boolean,
    val outputs: Map<String, String> = emptyMap(),
)

data class AgentWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val stopOnError: Boolean = true,
)

interface WorkflowExecutor {
    fun execute(workflow: AgentWorkflow): Flow<WorkflowStepResult>
}

class AgentWorkflowEngine(
    private val toolExecutor: suspend (toolId: String, arguments: Map<String, String>) -> String,
) : WorkflowExecutor {

    override fun execute(workflow: AgentWorkflow): Flow<WorkflowStepResult> = flow {
        val outputs = mutableMapOf<String, String>()

        for (step in workflow.steps) {
            val condition = step.condition
            if (condition != null && !condition(outputs)) {
                Logger.d("Workflow '${workflow.name}': step '${step.id}' skipped (condition false)")
                emit(WorkflowStepResult(step.id, WorkflowStepStatus.SKIPPED))
                continue
            }

            emit(WorkflowStepResult(step.id, WorkflowStepStatus.RUNNING))

            var lastError: String? = null
            var succeeded = false

            for (attempt in 1..step.retryPolicy.maxAttempts) {
                val result = runCatching {
                    val arguments = step.argumentsBuilder(outputs)
                    toolExecutor(step.toolId, arguments)
                }
                if (result.isSuccess) {
                    val output = result.getOrThrow()
                    outputs[step.id] = output
                    emit(WorkflowStepResult(step.id, WorkflowStepStatus.SUCCEEDED, output))
                    succeeded = true
                    break
                } else {
                    lastError = result.exceptionOrNull()?.message ?: "Unknown error"
                    Logger.w("Workflow step '${step.id}' attempt $attempt failed: $lastError")
                }
            }

            if (!succeeded) {
                emit(WorkflowStepResult(step.id, WorkflowStepStatus.FAILED, error = lastError))
                if (workflow.stopOnError) return@flow
            }
        }
    }
}
