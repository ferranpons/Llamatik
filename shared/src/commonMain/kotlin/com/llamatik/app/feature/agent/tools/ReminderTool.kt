package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Creates a reminder on the device. Platform-specific implementation required.
expect class ReminderTool() : AgentTool

val reminderSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("description", "Create a reminder with a title and optional time")
}

internal fun reminderExtractTitle(input: JsonObject): String? =
    input["title"]?.jsonPrimitive?.content

internal fun reminderExtractTime(input: JsonObject): String? =
    input["time"]?.jsonPrimitive?.content
