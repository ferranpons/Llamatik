package com.llamatik.sdk.agent.companion

enum class CompanionPersona {
    ASSISTANT,
    PROFESSIONAL,
    FRIENDLY,
    COMPANION,
    PET,
}

data class CompanionProfile(
    val persona: CompanionPersona,
    val systemPrompt: String,
    val responseStyle: String,
    val memoryEnabled: Boolean,
    val planningAggression: Float,
    val verbosity: Float,
    val toolPreference: Set<String>,
)

object CompanionProfiles {
    val Assistant = CompanionProfile(
        persona = CompanionPersona.ASSISTANT,
        systemPrompt = "You are a precise, helpful AI assistant. You complete tasks efficiently and answer questions accurately.",
        responseStyle = "concise",
        memoryEnabled = true,
        planningAggression = 0.7f,
        verbosity = 0.5f,
        toolPreference = emptySet(),
    )

    val Professional = CompanionProfile(
        persona = CompanionPersona.PROFESSIONAL,
        systemPrompt = "You are a professional AI assistant focused on productivity and business tasks. You are formal, precise, and results-oriented.",
        responseStyle = "formal",
        memoryEnabled = true,
        planningAggression = 0.9f,
        verbosity = 0.4f,
        toolPreference = setOf("calendar", "reminder", "contacts"),
    )

    val Friendly = CompanionProfile(
        persona = CompanionPersona.FRIENDLY,
        systemPrompt = "You are a friendly, approachable AI assistant. You're warm, encouraging, and conversational while still being helpful.",
        responseStyle = "casual",
        memoryEnabled = true,
        planningAggression = 0.5f,
        verbosity = 0.7f,
        toolPreference = emptySet(),
    )

    val Companion = CompanionProfile(
        persona = CompanionPersona.COMPANION,
        systemPrompt = "You are a caring AI companion. You remember the user's preferences and provide personalised, empathetic support.",
        responseStyle = "empathetic",
        memoryEnabled = true,
        planningAggression = 0.4f,
        verbosity = 0.8f,
        toolPreference = emptySet(),
    )

    val Pet = CompanionProfile(
        persona = CompanionPersona.PET,
        systemPrompt = "You are a cheerful, playful AI pet companion. You're enthusiastic and expressive while still being genuinely helpful.",
        responseStyle = "playful",
        memoryEnabled = true,
        planningAggression = 0.3f,
        verbosity = 0.9f,
        toolPreference = emptySet(),
    )

    fun forPersona(persona: CompanionPersona): CompanionProfile = when (persona) {
        CompanionPersona.ASSISTANT -> Assistant
        CompanionPersona.PROFESSIONAL -> Professional
        CompanionPersona.FRIENDLY -> Friendly
        CompanionPersona.COMPANION -> Companion
        CompanionPersona.PET -> Pet
    }
}
