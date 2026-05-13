package com.llamatik.app.feature.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedToolCall(
    val toolId: String,
    val input: JsonObject,
)

// Parses a JSON tool-call from model output.
// Expected format:
// {"tool": "<toolId>", "input": { ... }}
object ToolCallParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): ParsedToolCall? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val obj = json.parseToJsonElement(trimmed).jsonObject
            val toolId = obj["tool"]?.jsonPrimitive?.content ?: return null
            val input = obj["input"]?.jsonObject ?: JsonObject(emptyMap())
            ParsedToolCall(toolId = toolId, input = input)
        }.getOrNull()
    }

    // Extracts the first JSON block from a larger text (e.g. model reply with extra prose).
    fun extractFirstJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
