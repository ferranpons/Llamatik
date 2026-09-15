package com.llamatik.sdk

import com.llamatik.sdk.agent.AgentToolResult
import com.llamatik.sdk.agent.CalculatorTool
import com.llamatik.sdk.agent.DateTimeTool
import com.llamatik.sdk.agent.RandomTool
import com.llamatik.sdk.agent.UuidTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuiltInToolsTest {

    // region Calculator

    @Test
    fun calculatorAddition() = runTest {
        val result = CalculatorTool().execute(buildJsonObject { put("expression", "2+3") })
        assertIs<AgentToolResult.Success>(result)
        assertEquals("5", result.outputSummary)
    }

    @Test
    fun calculatorMultiplication() = runTest {
        val result = CalculatorTool().execute(buildJsonObject { put("expression", "(2+3)*4") })
        assertIs<AgentToolResult.Success>(result)
        assertEquals("20", result.outputSummary)
    }

    @Test
    fun calculatorPower() = runTest {
        val result = CalculatorTool().execute(buildJsonObject { put("expression", "2^10") })
        assertIs<AgentToolResult.Success>(result)
        assertEquals("1024", result.outputSummary)
    }

    @Test
    fun calculatorInvalidExpression() = runTest {
        val result = CalculatorTool().execute(buildJsonObject { put("expression", "abc") })
        assertIs<AgentToolResult.Failure>(result)
    }

    @Test
    fun calculatorMissingExpression() = runTest {
        val result = CalculatorTool().execute(buildJsonObject { })
        assertIs<AgentToolResult.Failure>(result)
    }

    // endregion

    // region DateTime

    @Test
    fun dateTimeReturnsNonEmpty() = runTest {
        val result = DateTimeTool().execute(buildJsonObject { put("format", "datetime") })
        assertIs<AgentToolResult.Success>(result)
        assertTrue(result.outputSummary.isNotBlank())
    }

    @Test
    fun dateTimeOnlyDate() = runTest {
        val result = DateTimeTool().execute(buildJsonObject { put("format", "date") })
        assertIs<AgentToolResult.Success>(result)
        assertTrue(result.outputSummary.contains("-"))
    }

    // endregion

    // region UUID

    @Test
    fun uuidNonEmpty() = runTest {
        val result = UuidTool().execute(buildJsonObject { })
        assertIs<AgentToolResult.Success>(result)
        assertTrue(result.outputSummary.isNotBlank())
        assertEquals(36, result.outputSummary.length)
        assertTrue(result.outputSummary.contains("-"))
    }

    @Test
    fun uuidTwoCallsAreDifferent() = runTest {
        val a = (UuidTool().execute(buildJsonObject { }) as AgentToolResult.Success).outputSummary
        val b = (UuidTool().execute(buildJsonObject { }) as AgentToolResult.Success).outputSummary
        assertTrue(a != b)
    }

    // endregion

    // region Random

    @Test
    fun randomWithinRange() = runTest {
        val result = RandomTool().execute(buildJsonObject { put("min", 1); put("max", 10) })
        assertIs<AgentToolResult.Success>(result)
        val n = result.outputSummary.toInt()
        assertTrue(n in 1..10)
    }

    @Test
    fun randomMinGreaterThanMaxFails() = runTest {
        val result = RandomTool().execute(buildJsonObject { put("min", 10); put("max", 5) })
        assertIs<AgentToolResult.Failure>(result)
    }

    // endregion
}

private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
