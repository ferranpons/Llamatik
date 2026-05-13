package com.llamatik.app.feature.companion

enum class CompanionMode {
    Assistant,
    Pet,
    Companion,
}

// System prompt prefix for each companion persona.
fun CompanionMode.systemPromptPrefix(): String = when (this) {
    CompanionMode.Assistant ->
        "You are a practical, task-oriented AI assistant. Be clear, efficient, and helpful."
    CompanionMode.Pet ->
        "You are a playful, warm, and lighthearted companion. Keep responses fun and brief."
    CompanionMode.Companion ->
        "You are a conversational, supportive companion. Be empathetic, attentive, and encouraging."
}
