package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import com.llamatik.app.feature.agent.AgentToolResult
import kotlinx.serialization.json.JsonObject

actual class ReminderTool actual constructor() : AgentTool {
    actual override val id = "reminder"
    actual override val displayName = "Reminder"
    actual override val description = "Create a reminder"
    actual override val schema = reminderSchema
    actual override fun isSupported() = false
    actual override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class OpenAppTool actual constructor() : AgentTool {
    actual override val id = "open_app"
    actual override val displayName = "Open App"
    actual override val description = "Open an installed app"
    actual override val schema = openAppSchema
    actual override fun isSupported() = false
    actual override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class DeviceControlTool actual constructor() : AgentTool {
    actual override val id = "device_control"
    actual override val displayName = "Device Control"
    actual override val description = "Perform safe device control actions"
    actual override val schema = deviceControlSchema
    actual override fun isSupported() = false
    actual override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class SystemInteractionTool actual constructor() : AgentTool {
    actual override val id = "system_interaction"
    actual override val displayName = "System Interaction"
    actual override val description = "Perform safe system interactions"
    actual override val schema = systemInteractionSchema
    actual override fun isSupported() = false
    actual override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}
