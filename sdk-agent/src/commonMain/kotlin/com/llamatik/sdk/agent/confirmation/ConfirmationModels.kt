package com.llamatik.sdk.agent.confirmation

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class ConfirmationRequest(
    val toolId: String,
    val toolDisplayName: String,
    val humanReadableDescription: String,
    val argumentsSummary: Map<String, String>,
    val riskLevel: RiskLevel,
    val sessionId: String,
)

data class ConfirmationPolicy(
    val alwaysConfirmHigh: Boolean = true,
    val alwaysConfirmCritical: Boolean = true,
    val alwaysConfirmMedium: Boolean = false,
    val toolOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun requiresConfirmation(toolId: String, riskLevel: RiskLevel): Boolean {
        toolOverrides[toolId]?.let { return it }
        return when (riskLevel) {
            RiskLevel.LOW -> false
            RiskLevel.MEDIUM -> alwaysConfirmMedium
            RiskLevel.HIGH -> alwaysConfirmHigh
            RiskLevel.CRITICAL -> alwaysConfirmCritical
        }
    }
}

fun interface ConfirmationHandler {
    suspend fun requestConfirmation(request: ConfirmationRequest): Boolean
}
