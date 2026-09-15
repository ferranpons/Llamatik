package com.llamatik.app.feature.chatbot.model

enum class ModelCategory(val key: String) {
    Generate("generate"),
    Stt("stt"),
    StableDiffusion("stable_diffusion"),
    Vlm("vlm"),
    Embed("embed");

    companion object {
        fun fromKey(key: String): ModelCategory? = entries.firstOrNull { it.key == key }
    }
}
