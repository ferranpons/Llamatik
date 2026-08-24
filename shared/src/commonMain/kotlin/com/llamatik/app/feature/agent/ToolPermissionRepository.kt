package com.llamatik.app.feature.agent

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

private const val TOOL_PERMISSIONS_KEY = "llamatik_tool_permissions_v1"
private const val AGENT_ENABLED_KEY = "agent_enabled"

class ToolPermissionRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun isAgentEnabled(): Boolean = settings.getBoolean(AGENT_ENABLED_KEY, false)

    fun setAgentEnabled(enabled: Boolean) {
        settings.putBoolean(AGENT_ENABLED_KEY, enabled)
    }

    fun getPermission(toolId: String): ToolPermission? {
        val raw = settings.getString("$TOOL_PERMISSIONS_KEY.$toolId", "")
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(ToolPermission.serializer(), raw) }.getOrNull()
    }

    fun setPermission(permission: ToolPermission) {
        settings.putString(
            "$TOOL_PERMISSIONS_KEY.${permission.toolId}",
            json.encodeToString(ToolPermission.serializer(), permission)
        )
    }

    fun isGranted(toolId: String): Boolean = getPermission(toolId)?.granted ?: false

    fun requiresConfirmation(toolId: String): Boolean =
        getPermission(toolId)?.requiresConfirmation ?: true
}
