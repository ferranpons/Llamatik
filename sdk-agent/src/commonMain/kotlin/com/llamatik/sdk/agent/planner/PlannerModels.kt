package com.llamatik.sdk.agent.planner

import com.llamatik.sdk.agent.capability.Capability
import com.llamatik.sdk.agent.runtime.ExecutionPlan
import com.llamatik.sdk.chat.ChatMessage

data class PlannerRequest(
    val userMessage: String,
    val conversationHistory: List<ChatMessage>,
    val availableCapabilities: Set<Capability>,
    val memoryContext: String,
    val companionSystemPrompt: String,
)

sealed interface PlannerResult {
    data class Plan(val executionPlan: ExecutionPlan) : PlannerResult
    data class ConversationalResponse(val text: String) : PlannerResult
    data class Failure(val message: String) : PlannerResult
}
