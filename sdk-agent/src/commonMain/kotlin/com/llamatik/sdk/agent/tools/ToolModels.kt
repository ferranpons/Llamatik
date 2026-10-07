package com.llamatik.sdk.agent.tools

import kotlinx.serialization.json.JsonObject

enum class ToolCategory {
    CALENDAR,
    REMINDER,
    APPS,
    FILES,
    CLIPBOARD,
    BROWSER,
    MAPS,
    CONTACTS,
    MEDIA,
    NOTIFICATIONS,
    SETTINGS,
    VOICE,
    NETWORK,
    UTILITIES,
    AUTOMATION,
}

enum class ToolCapability {
    CALENDAR_READ,
    CALENDAR_WRITE,
    CONTACTS_READ,
    CONTACTS_WRITE,
    NOTIFICATIONS_POST,
    CLIPBOARD_READ,
    CLIPBOARD_WRITE,
    FILES_READ,
    FILES_WRITE,
    OPEN_APPS,
    OPEN_URL,
    LOCATION_READ,
    MEDIA_PLAY,
    SETTINGS_READ,
    SETTINGS_WRITE,
    REMINDER_CREATE,
    REMINDER_READ,
    SHARE,
    ACCESSIBILITY,
    NETWORK_READ,
}

enum class SupportedPlatform {
    ANDROID,
    IOS,
    JVM,
    WASM,
    ALL,
}

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null,
)

data class ToolSchema(
    val parameters: List<ToolParameter>,
)

data class ToolAvailability(
    val supported: Boolean,
    val reason: String? = null,
) {
    fun isAvailable(): Boolean = supported

    companion object {
        val Available = ToolAvailability(true)
        fun unavailable(reason: String) = ToolAvailability(false, reason)
    }
}

data class ToolMetadata(
    val category: ToolCategory,
    val capabilities: Set<ToolCapability>,
    val supportedPlatforms: Set<SupportedPlatform>,
    val requiresConfirmation: Boolean = false,
    val riskLevel: String = "LOW",
)

sealed class ToolResult {
    data class Success(val summary: String, val data: Map<String, String> = emptyMap()) : ToolResult()
    data class Failure(val message: String) : ToolResult()
    data object Unsupported : ToolResult()
    data object PermissionDenied : ToolResult()
}

interface ToolDefinition {
    val id: String
    val displayName: String
    val description: String
    val schema: ToolSchema
    val metadata: ToolMetadata
    val availability: ToolAvailability
    val requiredPermissions: Set<String>

    suspend fun execute(arguments: Map<String, String>): ToolResult
}
