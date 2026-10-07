package com.llamatik.sdk.agent

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.pow
import kotlin.random.Random

// region — Calculator

class CalculatorTool : AgentTool {
    override val id = "calculator"
    override val displayName = "Calculator"
    override val description = "Evaluates a math expression. Supports +, -, *, /, %, ^ and parentheses."
    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "Math expression to evaluate, e.g. '(2+3)*4' or '2^10'")
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("expression")) }
    }

    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val expr = input["expression"]?.jsonPrimitive?.contentOrNull
            ?: return AgentToolResult.Failure("Missing 'expression' parameter")
        return runCatching {
            val result = ExprParser(expr).evaluate()
            val formatted = if (result == kotlin.math.floor(result) && !result.isInfinite()) {
                result.toLong().toString()
            } else {
                result.toString()
            }
            AgentToolResult.Success(formatted)
        }.getOrElse { AgentToolResult.Failure("Invalid expression: ${it.message}") }
    }
}

private class ExprParser(input: String) {
    private val expr = input.replace(" ", "")
    private var pos = 0

    fun evaluate(): Double {
        val result = parseAddSub()
        if (pos < expr.length) throw ArithmeticException("Unexpected character '${expr[pos]}'")
        return result
    }

    private fun parseAddSub(): Double {
        var result = parseMulDiv()
        while (pos < expr.length) {
            when (expr[pos]) {
                '+' -> { pos++; result += parseMulDiv() }
                '-' -> { pos++; result -= parseMulDiv() }
                else -> break
            }
        }
        return result
    }

    private fun parseMulDiv(): Double {
        var result = parsePow()
        while (pos < expr.length) {
            when (expr[pos]) {
                '*' -> { pos++; result *= parsePow() }
                '/' -> { pos++; result /= parsePow() }
                '%' -> { pos++; result %= parsePow() }
                else -> break
            }
        }
        return result
    }

    private fun parsePow(): Double {
        val base = parseUnary()
        if (pos < expr.length && expr[pos] == '^') {
            pos++
            return base.pow(parsePow())
        }
        return base
    }

    private fun parseUnary(): Double {
        if (pos < expr.length && expr[pos] == '-') { pos++; return -parsePrimary() }
        if (pos < expr.length && expr[pos] == '+') { pos++; return parsePrimary() }
        return parsePrimary()
    }

    private fun parsePrimary(): Double {
        if (pos < expr.length && expr[pos] == '(') {
            pos++
            val v = parseAddSub()
            require(pos < expr.length && expr[pos] == ')') { "Missing ')'" }
            pos++
            return v
        }
        val start = pos
        if (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
            while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) pos++
            return expr.substring(start, pos).toDoubleOrNull()
                ?: throw ArithmeticException("Invalid number at position $start")
        }
        throw ArithmeticException("Expected number at position $pos")
    }
}

// endregion

// region — Date & Time

class DateTimeTool : AgentTool {
    override val id = "datetime"
    override val displayName = "Date & Time"
    override val description = "Returns the current date and/or time."
    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("format") {
                put("type", "string")
                put("description", "What to return: 'date', 'time', or 'datetime' (default)")
                putJsonArray("enum") {
                    add(kotlinx.serialization.json.JsonPrimitive("date"))
                    add(kotlinx.serialization.json.JsonPrimitive("time"))
                    add(kotlinx.serialization.json.JsonPrimitive("datetime"))
                }
            }
        }
    }

    override fun isSupported() = true

    @OptIn(ExperimentalTime::class)
    override suspend fun execute(input: JsonObject): AgentToolResult {
        val format = input["format"]?.jsonPrimitive?.contentOrNull ?: "datetime"
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        val result = when (format) {
            "date" -> "${now.date}"
            "time" -> "$hh:$mm"
            else -> "${now.date} $hh:$mm"
        }
        return AgentToolResult.Success(result)
    }
}

// endregion

// region — UUID

class UuidTool : AgentTool {
    override val id = "uuid"
    override val displayName = "UUID Generator"
    override val description = "Generates a random UUID v4."
    override val schema: JsonObject = buildJsonObject { put("type", "object") }

    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val bytes = Random.Default.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val uuid = buildString {
            bytes.forEachIndexed { i, b ->
                if (i in intArrayOf(4, 6, 8, 10)) append('-')
                append(b.toInt().and(0xff).toString(16).padStart(2, '0'))
            }
        }
        return AgentToolResult.Success(uuid)
    }
}

// endregion

// region — Random Number

class RandomTool : AgentTool {
    override val id = "random"
    override val displayName = "Random Number"
    override val description = "Generates a random integer between min (inclusive) and max (inclusive)."
    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("min") { put("type", "integer"); put("description", "Lower bound (default 0)") }
            putJsonObject("max") { put("type", "integer"); put("description", "Upper bound (default 100)") }
        }
    }

    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val min = input["min"]?.jsonPrimitive?.intOrNull ?: 0
        val max = input["max"]?.jsonPrimitive?.intOrNull ?: 100
        if (min > max) return AgentToolResult.Failure("min ($min) must be ≤ max ($max)")
        return AgentToolResult.Success(Random.Default.nextInt(min, max + 1).toString())
    }
}

// endregion

// region — Registry extension

fun ToolRegistry.registerBuiltIns() {
    register(CalculatorTool())
    register(DateTimeTool())
    register(UuidTool())
    register(RandomTool())
}

// endregion
