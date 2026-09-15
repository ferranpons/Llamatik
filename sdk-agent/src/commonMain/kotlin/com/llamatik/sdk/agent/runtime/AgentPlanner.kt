package com.llamatik.sdk.agent.runtime

import co.touchlab.kermit.Logger
import com.llamatik.sdk.agent.planner.PlannerRequest
import com.llamatik.sdk.agent.planner.PlannerResult
import com.llamatik.sdk.agent.registry.ToolRegistry
import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.chat.ChatRunner
import com.llamatik.sdk.chat.Gemma3
import kotlinx.serialization.json.Json

class AgentPlanner(
    private val toolRegistry: ToolRegistry,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param onConversationalDelta Called with each token when the model is producing a
     * conversational (non-JSON) response. Never called when the model outputs a JSON plan.
     * The callback is non-suspending so it can be called from ChatRunner's sync callbacks.
     */
    suspend fun plan(
        request: PlannerRequest,
        onConversationalDelta: (String) -> Unit = {},
    ): PlannerResult {
        val systemPrompt = buildSystemPrompt(request)

        val messages = request.conversationHistory.takeLast(10) +
            listOf(ChatMessage(ChatMessage.Role.User, request.userMessage))

        val accumulated = StringBuilder()
        var planResult: PlannerResult? = null

        // Determined on the first non-whitespace token: true = conversational, false = JSON plan
        var streamMode: Boolean? = null

        ChatRunner.stream(
            system = systemPrompt,
            messages = messages,
            template = Gemma3,
            maxTokens = 512,
            onDelta = { chunk ->
                accumulated.append(chunk)
                when (streamMode) {
                    null -> {
                        val trimmed = accumulated.toString().trimStart()
                        if (trimmed.isNotEmpty()) {
                            streamMode = !trimmed.startsWith("{")
                            if (streamMode == true) {
                                // Emit everything accumulated so far (leading whitespace stripped)
                                onConversationalDelta(trimmed)
                            }
                        }
                    }
                    true -> onConversationalDelta(chunk)
                    false -> { /* JSON plan — don't stream raw tokens to UI */ }
                }
            },
            onComplete = {
                planResult = parsePlan(accumulated.toString())
            },
            onError = { err ->
                Logger.e("AgentPlanner inference error: $err")
                planResult = PlannerResult.Failure("LLM error: $err")
            }
        )

        return planResult ?: PlannerResult.Failure("No plan generated")
    }

    private fun parsePlan(raw: String): PlannerResult {
        val jsonBlock = extractFirstJsonBlock(raw)
            ?: return PlannerResult.ConversationalResponse(raw.trim())

        return runCatching {
            val plan = json.decodeFromString(ExecutionPlan.serializer(), jsonBlock)
            // Validate all tool ids exist
            val unknown = plan.steps.mapNotNull { step ->
                step.toolId.takeUnless { toolRegistry.get(it) != null }
            }
            if (unknown.isNotEmpty()) {
                return PlannerResult.Failure("Unknown tools: ${unknown.joinToString()}")
            }
            PlannerResult.Plan(plan)
        }.getOrElse {
            Logger.w("AgentPlanner: JSON parse failed for block: $jsonBlock — ${it.message}")
            PlannerResult.ConversationalResponse(raw.trim())
        }
    }

    private fun buildSystemPrompt(request: PlannerRequest): String {
        val memoryBlock = if (request.memoryContext.isNotBlank()) {
            "\nUser memory:\n${request.memoryContext}"
        } else ""

        val dateContext = if (request.currentDateTime.isNotBlank()) {
            "\nToday: ${request.currentDateTime}"
        } else ""

        val toolsWithParams = toolRegistry.availableTools().joinToString("\n") { tool ->
            val params = tool.schema.parameters.joinToString(", ") { p ->
                if (p.required) p.name else "${p.name}(optional)"
            }
            "  ${tool.id}: ${tool.description} [params: $params]"
        }

        return """
You are a task-execution assistant. You decide: execute an action, ask for missing info, or reply conversationally.
$dateContext$memoryBlock

Available tools:
$toolsWithParams

RULES — apply in order:

RULE 1 — EXECUTE: User wants to DO something (create reminder, add calendar event, open app, copy text, share, send notification, etc.) AND you have all required info → output ONLY the JSON plan below. No other text before or after.

RULE 2 — CLARIFY: User wants to DO something but a required parameter is missing (e.g. "set a reminder" with no title, or "create event" with no title) → ask ONE short question only. No JSON.

RULE 3 — CONVERSE: User is chatting, asking a question, or the request does NOT involve a device action → reply with plain text.

JSON plan format (RULE 1 only — no text outside the JSON):
{
  "steps": [{"tool": "<tool_id>", "arguments": {"param": "value"}, "stepId": "step_1", "dependsOn": []}],
  "confidence": 0.9,
  "requiresConfirmation": false,
  "reasoningSummary": "one-line summary",
  "estimatedRisk": "LOW"
}

Rules for arguments:
- Use EXACT tool IDs from the list above
- estimatedRisk must be: LOW, MEDIUM, HIGH, or CRITICAL
- For dates: use YYYY-MM-DD format. Use the "Today" date above to convert relative days ("Thursday", "tomorrow") to an actual date
- For times: use HH:MM (24h) format
- NEVER describe what you would do — either execute (JSON) or ask for missing info
""".trimIndent()
    }

    private fun extractFirstJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
}
