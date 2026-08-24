package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import com.llamatik.app.feature.agent.AgentToolResult
import kotlinx.serialization.json.JsonObject

actual class ReminderTool actual constructor() : AgentTool {
    override val id = "reminder"
    override val displayName = "Reminder"
    override val description = "Create a reminder"
    override val schema = reminderSchema
    override fun isSupported() = false
    override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class OpenAppTool actual constructor() : AgentTool {
    override val id = "open_app"
    override val displayName = "Open App"
    override val description = "Open an installed app"
    override val schema = openAppSchema
    override fun isSupported() = false
    override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class DeviceControlTool actual constructor() : AgentTool {
    override val id = "device_control"
    override val displayName = "Device Control"
    override val description = "Perform safe device control actions"
    override val schema = deviceControlSchema
    override fun isSupported() = false
    override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}

actual class SystemInteractionTool actual constructor() : AgentTool {
    override val id = "system_interaction"
    override val displayName = "System Interaction"
    override val description = "Perform safe system interactions"
    override val schema = systemInteractionSchema
    override fun isSupported() = false
    override suspend fun execute(input: JsonObject): AgentToolResult = AgentToolResult.Unsupported
}
