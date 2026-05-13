package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Opens an installed app by package name (Android) or URL scheme (iOS).
expect class OpenAppTool() : AgentTool

val openAppSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("description", "Open an installed app or URL scheme")
}

internal fun openAppExtractTarget(input: JsonObject): String? =
    input["target"]?.jsonPrimitive?.content
