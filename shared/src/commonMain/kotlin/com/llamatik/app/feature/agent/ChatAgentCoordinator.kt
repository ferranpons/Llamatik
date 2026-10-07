package com.llamatik.app.feature.agent

import com.llamatik.app.feature.chatbot.utils.ChatMessage
import com.llamatik.sdk.agent.runtime.AgentRuntime
import com.llamatik.sdk.agent.runtime.AgentRuntimeEvent
import kotlinx.coroutines.flow.Flow

/**
 * Thin adapter between ChatBotViewModel and AgentRuntime.
 * Converts app-layer conversation models to SDK types, forwards to AgentRuntime,
 * and exposes the resulting event stream. Contains NO planning, tool parsing,
 * or action execution logic — those belong to sdk-agent.
 */
class ChatAgentCoordinator(
    private val agentRuntime: AgentRuntime,
    private val agentFeatureFlags: AgentFeatureFlags,
) {
    fun isAgentEnabled(): Boolean = agentFeatureFlags.isAgentEnabled()

    fun processMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        sessionId: String,
    ): Flow<AgentRuntimeEvent> {
        val sdkHistory = conversationHistory.map { it.toSdkChatMessage() }
        return agentRuntime.processMessage(userMessage, sdkHistory, sessionId)
    }

    private fun ChatMessage.toSdkChatMessage(): com.llamatik.sdk.chat.ChatMessage =
        com.llamatik.sdk.chat.ChatMessage(
            role = when (this.role) {
                ChatMessage.Role.User -> com.llamatik.sdk.chat.ChatMessage.Role.User
                ChatMessage.Role.Assistant -> com.llamatik.sdk.chat.ChatMessage.Role.Assistant
                ChatMessage.Role.System -> com.llamatik.sdk.chat.ChatMessage.Role.System
            },
            content = this.content,
        )
}
