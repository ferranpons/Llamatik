package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Handles safe system-level interactions: copy to clipboard, open URL, share text.
// Platform implementations provide the actual capability.
expect class SystemInteractionTool() : AgentTool

val systemInteractionSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("description", "Perform a safe system interaction (copy, open URL, share)")
}

internal fun systemInteractionExtractAction(input: JsonObject): String? =
    input["action"]?.jsonPrimitive?.content

internal fun systemInteractionExtractText(input: JsonObject): String? =
    input["text"]?.jsonPrimitive?.content

internal fun systemInteractionExtractUrl(input: JsonObject): String? =
    input["url"]?.jsonPrimitive?.content
