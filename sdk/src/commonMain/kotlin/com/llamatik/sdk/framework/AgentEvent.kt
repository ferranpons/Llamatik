package com.llamatik.sdk.framework

import com.llamatik.sdk.agent.AgentToolResult

sealed interface AgentEvent {
    data class TurnStarted(val userMessage: String) : AgentEvent
    data object Thinking : AgentEvent
    data class ToolCallStarted(val toolId: String, val inputSummary: String) : AgentEvent
    data class ToolCallCompleted(val toolId: String, val result: AgentToolResult) : AgentEvent
    data class ToolCallFailed(val toolId: String, val error: String) : AgentEvent
    data object StreamStarted : AgentEvent
    data class StreamDelta(val chunk: String) : AgentEvent
    data class StreamCompleted(val final: String) : AgentEvent
    data class StreamError(val message: String) : AgentEvent
    data class MemoryUpdated(val messageCount: Int) : AgentEvent
    data class TurnCompleted(val final: String) : AgentEvent
}

sealed interface AgentStreamChunk {
    data class Delta(val text: String) : AgentStreamChunk
    data class ToolCallStarted(val toolId: String, val inputSummary: String) : AgentStreamChunk
    data class ToolResult(val toolId: String, val summary: String, val success: Boolean) : AgentStreamChunk
    data class Done(val final: String) : AgentStreamChunk
    data class Failure(val message: String) : AgentStreamChunk
}
