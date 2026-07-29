package com.llamatik.sdk.agent.audit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentAuditEntry(
    val id: String,
    @SerialName("tool_id") val toolId: String,
    @SerialName("tool_display_name") val toolDisplayName: String,
    @SerialName("arguments_summary") val argumentsSummary: String,
    val durationMs: Long,
    val success: Boolean,
    val failure: String? = null,
    val platform: String,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("session_id") val sessionId: String,
)
