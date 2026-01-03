package com.llamatik.app.feature.chatbot.utils

/**
 * Model-agnostic prompt templates in Kotlin.
 * No C++ templating; pick the template at runtime.
 *
 * You can add more templates (e.g., Mistral, Phi, Qwen) without touching JNI/C++.
 */
sealed interface PromptTemplate {
    val name: String

    /**
     * Stop strings that, if seen in the streamed text, should end generation client-side.
     * These are conservative defaults; llama.cpp may also stop on its own EOS.
     */
    val stopSequences: List<String> get() = emptyList()
}

/**
 * A very generic, readable prompt layout that most instruct-tuned small LLMs handle fine.
 * Good default when you truly don't want model-specific tags.
 */
object Plain : PromptTemplate {
    override val name: String = "plain"
    override val stopSequences: List<String> = listOf("\n\nUser:", "\nUser:", "<|eot_id|>")
}

/**
 * Gemma 3 chat template (publicly documented pattern).
 * Keeps the <start_of_turn>/<end_of_turn> tags that materially improve Gemma’s behavior.
 * Still lives purely in Kotlin so the engine/C++ stays template-free.
 */
object Gemma3 : PromptTemplate {
    override val name: String = "gemma3"
    override val stopSequences: List<String> = listOf(
        "<end_of_turn>", "<|eot_id|>", // end-of-turn markers
        "<start_of_turn>assistant"     // don't let it start a new assistant turn
    )
}

/**
 * Llama 3/3.1 Instruct-like template (compact, robust).
 * Works well for many llama.cpp models trained on chat-style data.
 */
object Llama3Instruct : PromptTemplate {
    override val name: String = "llama3_instruct"
    override val stopSequences: List<String> = listOf(
        "<|eot_id|>", "```", // common EOS and fenced block end to curb run-ons
    )
}

object QwenChat : PromptTemplate {
    override val name: String = "qwen_chat"
    override val stopSequences: List<String> = listOf(
        "<|im_end|>", // Qwen chat messages end marker
    )
}
