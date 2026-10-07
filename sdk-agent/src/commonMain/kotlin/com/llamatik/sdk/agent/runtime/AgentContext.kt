package com.llamatik.sdk.agent.runtime

import com.llamatik.sdk.agent.capability.Capability
import com.llamatik.sdk.agent.companion.CompanionProfile
import com.llamatik.sdk.agent.memory.AgentMemory
import com.llamatik.sdk.agent.permissions.PermissionDecision
import com.llamatik.sdk.chat.ChatMessage

data class AgentContext(
    val conversationHistory: List<ChatMessage>,
    val availableCapabilities: Set<Capability>,
    val permissionDecisions: Map<String, PermissionDecision>,
    val companionProfile: CompanionProfile,
    val persistentMemory: AgentMemory,
    val sessionId: String,
    val platformId: String,
)
