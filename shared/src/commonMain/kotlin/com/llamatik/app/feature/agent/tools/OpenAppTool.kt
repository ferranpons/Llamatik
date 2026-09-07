package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Opens an installed app by package name (Android) or URL scheme (iOS).
expect class OpenAppTool() : AgentTool {
    override val id: String
    override val displayName: String
    override val description: String
    override val schema: kotlinx.serialization.json.JsonObject
    override fun isSupported(): Boolean
    override suspend fun execute(input: kotlinx.serialization.json.JsonObject): com.llamatik.app.feature.agent.AgentToolResult
}

val openAppSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("description", "Open an installed app or URL scheme")
}

internal fun openAppExtractTarget(input: JsonObject): String? =
    input["target"]?.jsonPrimitive?.content
