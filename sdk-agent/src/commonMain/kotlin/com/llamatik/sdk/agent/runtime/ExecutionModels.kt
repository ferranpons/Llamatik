package com.llamatik.sdk.agent.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ExecutionStatus {
    PENDING,
    VALIDATING,
    AWAITING_PERMISSION,
    AWAITING_CONFIRMATION,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DENIED,
    CANCELLED,
    UNSUPPORTED,
}

@Serializable
data class ExecutionStep(
    @SerialName("tool") val toolId: String,
    val arguments: Map<String, String> = emptyMap(),
    val dependsOn: List<String> = emptyList(),
    val stepId: String = toolId,
)

@Serializable
data class ExecutionPlan(
    val steps: List<ExecutionStep>,
    val confidence: Float = 1.0f,
    val requiresConfirmation: Boolean = false,
    val reasoningSummary: String = "",
    val estimatedRisk: String = "LOW",
)

data class ExecutionResult(
    val stepId: String,
    val toolId: String,
    val status: ExecutionStatus,
    val outputSummary: String,
    val errorMessage: String? = null,
    val durationMs: Long = 0,
)

class ExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)
