package com.llamatik.app.feature.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface AgentTool {
    val id: String
    val displayName: String
    val description: String
    val schema: JsonObject
    fun isSupported(): Boolean
    suspend fun execute(input: JsonObject): AgentToolResult
}

sealed class AgentToolResult {
    data class Success(val outputSummary: String) : AgentToolResult()
    data class Failure(val errorMessage: String) : AgentToolResult()
    data object Unsupported : AgentToolResult()
    data object PermissionDenied : AgentToolResult()
}

@Serializable
data class ToolPermission(
    @SerialName("tool_id") val toolId: String,
    val granted: Boolean,
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean = true,
    val platform: String = "all",
)

@Serializable
data class AgentActionLog(
    val id: String,
    @SerialName("tool_id") val toolId: String,
    @SerialName("input_summary") val inputSummary: String,
    val status: AgentActionStatus,
    @SerialName("created_at") val createdAtEpochMs: Long,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
enum class AgentActionStatus { SUCCESS, FAILURE, DENIED, UNSUPPORTED, PENDING }
