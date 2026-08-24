package com.llamatik.sdk.chat

data class ChatMessage(
    val role: Role,
    val content: String
) {
    enum class Role { System, User, Assistant }
}
