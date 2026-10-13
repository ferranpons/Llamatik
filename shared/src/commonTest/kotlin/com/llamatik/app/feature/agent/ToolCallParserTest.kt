package com.llamatik.app.feature.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolCallParserTest {

    @Test
    fun parse_validToolCall() {
        val raw = """{"tool": "reminder", "input": {"text": "Buy milk", "time": "18:00"}}"""
        val result = ToolCallParser.parse(raw)
        assertNotNull(result)
        assertEquals("reminder", result.toolId)
        assertEquals("Buy milk", result.input["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun parse_emptyInput() {
        val raw = """{"tool": "open_app", "input": {}}"""
        val result = ToolCallParser.parse(raw)
        assertNotNull(result)
        assertEquals("open_app", result.toolId)
    }

    @Test
    fun parse_missingTool_returnsNull() {
        val raw = """{"input": {"foo": "bar"}}"""
        assertNull(ToolCallParser.parse(raw))
    }

    @Test
    fun parse_notJson_returnsNull() {
        assertNull(ToolCallParser.parse("This is plain text"))
    }

    @Test
    fun parse_emptyString_returnsNull() {
        assertNull(ToolCallParser.parse(""))
    }

    @Test
    fun extractFirstJsonBlock_findsBlock() {
        val text = "Here is the tool call: {\"tool\": \"x\", \"input\": {}} and some trailing text"
        val block = ToolCallParser.extractFirstJsonBlock(text)
        assertNotNull(block)
        val parsed = ToolCallParser.parse(block)
        assertNotNull(parsed)
        assertEquals("x", parsed.toolId)
    }

    @Test
    fun extractFirstJsonBlock_noJson_returnsNull() {
        assertNull(ToolCallParser.extractFirstJsonBlock("no json here"))
    }
}
