package com.llamatik.sdk.chat

sealed interface PromptTemplate {
    val name: String
    val stopSequences: List<String> get() = emptyList()
}

object Plain : PromptTemplate {
    override val name: String = "plain"
    override val stopSequences: List<String> = listOf("\n\nUser:", "\nUser:", "<|eot_id|>")
}

object Gemma3 : PromptTemplate {
    override val name: String = "gemma3"
    override val stopSequences: List<String> = listOf(
        "<end_of_turn>", "<|eot_id|>",
        "<start_of_turn>assistant"
    )
}

object Llama3Instruct : PromptTemplate {
    override val name: String = "llama3_instruct"
    override val stopSequences: List<String> = listOf(
        "<|eot_id|>", "```",
    )
}

object QwenChat : PromptTemplate {
    override val name: String = "qwen_chat"
    override val stopSequences: List<String> = listOf(
        "<|im_end|>",
    )
}
