package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Triggers safe device control actions: open settings, launch system intents.
// Does not attempt restricted toggles.
expect class DeviceControlTool() : AgentTool {
    override val id: String
    override val displayName: String
    override val description: String
    override val schema: kotlinx.serialization.json.JsonObject
    override fun isSupported(): Boolean
    override suspend fun execute(input: kotlinx.serialization.json.JsonObject): com.llamatik.app.feature.agent.AgentToolResult
}

val deviceControlSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("description", "Perform a safe device control action (e.g. open settings)")
}

internal fun deviceControlExtractAction(input: JsonObject): String? =
    input["action"]?.jsonPrimitive?.content
