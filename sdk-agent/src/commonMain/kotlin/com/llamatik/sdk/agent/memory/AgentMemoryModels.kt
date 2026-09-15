package com.llamatik.sdk.agent.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MemoryType {
    PREFERENCE,
    HABIT,
    FAVORITE_APP,
    FREQUENT_TOOL,
    RECURRING_REMINDER,
    FACT,
    CUSTOM,
}

@Serializable
data class MemoryEntry(
    val id: String,
    val content: String,
    val type: MemoryType,
    val tags: List<String> = emptyList(),
    @SerialName("created_at_ms") val createdAtMs: Long,
    val enabled: Boolean = true,
)
