package com.llamatik.sdk

import com.llamatik.sdk.agent.ToolCallParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolCallParserTest {

    @Test
    fun parsesValidToolCall() {
        val json = """{"tool": "open_app", "input": {"package": "com.example.app"}}"""
        val result = ToolCallParser.parse(json)
        assertEquals("open_app", result?.toolId)
        assertEquals("com.example.app", result?.input?.get("package").toString().trim('"'))
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(ToolCallParser.parse("not json"))
    }

    @Test
    fun returnsNullForMissingToolField() {
        assertNull(ToolCallParser.parse("""{"input": {}}"""))
    }

    @Test
    fun extractsFirstJsonBlock() {
        val text = "Some prose before. {\"tool\": \"reminder\", \"input\": {}} And after."
        val block = ToolCallParser.extractFirstJsonBlock(text)
        assertEquals("""{"tool": "reminder", "input": {}}""", block)
    }
}
