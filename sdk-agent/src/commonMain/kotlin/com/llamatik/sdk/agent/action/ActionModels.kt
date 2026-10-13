package com.llamatik.sdk.agent.action

data class ActionContext(
    val arguments: Map<String, String>,
    val sessionId: String,
    val callerToolId: String,
)

sealed class ActionResult {
    data class Success(val summary: String, val data: Map<String, String> = emptyMap()) : ActionResult()
    data class Failure(val message: String) : ActionResult()
    data object Unsupported : ActionResult()
    data object PermissionDenied : ActionResult()
}

data class ActionValidationResult(
    val valid: Boolean,
    val errorMessage: String? = null,
)

interface Action {
    val id: String
    fun validate(context: ActionContext): ActionValidationResult
    suspend fun execute(context: ActionContext): ActionResult
    fun requiredPermissions(): Set<String>
    fun isSupported(): Boolean
}
