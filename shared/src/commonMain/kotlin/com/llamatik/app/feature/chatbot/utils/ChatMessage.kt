package com.llamatik.app.feature.chatbot.utils

data class ChatMessage(
    val role: Role,
    val content: String
) {
    enum class Role { System, User, Assistant }
}