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
        val toolDescriptions = toolRegistry.availableTools().joinToString("\n") {
            "- ${it.id}: ${it.description}"
        }

        val systemPrompt = buildSystemPrompt(toolDescriptions, request)

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

    private fun buildSystemPrompt(toolDescriptions: String, request: PlannerRequest): String {
        val capabilityNames = request.availableCapabilities.map { it.id }.joinToString(", ")
        val memoryBlock = if (request.memoryContext.isNotBlank()) {
            "\n\n=== User memory ===\n${request.memoryContext}\n=== End user memory ==="
        } else ""

        val companionStyle = request.companionSystemPrompt

        return """
$companionStyle
$memoryBlock

You are a structured planning assistant. Your job is to decide whether the user's request requires tool execution, or if it can be answered conversationally.

Available platform capabilities: $capabilityNames

Available tools:
$toolDescriptions

RULES:
1. If the user asks something conversational (greeting, explanation, question you can answer), respond with plain text only — NO JSON.
2. If the user wants to DO something (create event, set reminder, open app, etc.), output ONLY a JSON ExecutionPlan in this exact format:
{
  "steps": [
    {
      "tool": "<tool_id>",
      "arguments": {"key": "value"},
      "stepId": "<unique_id>",
      "dependsOn": []
    }
  ],
  "confidence": 0.95,
  "requiresConfirmation": false,
  "reasoningSummary": "Brief explanation",
  "estimatedRisk": "LOW"
}
3. estimatedRisk must be one of: LOW, MEDIUM, HIGH, CRITICAL
4. For HIGH or CRITICAL risk, set requiresConfirmation to true
5. Do NOT include any text outside the JSON when producing a plan
6. Do NOT invent tool IDs — use only the tools listed above
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
