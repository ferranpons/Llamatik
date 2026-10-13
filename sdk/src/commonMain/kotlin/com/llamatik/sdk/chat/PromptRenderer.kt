package com.llamatik.sdk.chat

object PromptRenderer {

    data class Labels(
        val system: String = "System",
        val user: String = "User",
        val assistant: String = "Assistant",
        val relevantContext: String = "Relevant Context",
        val defaultSystemPrompt: String = "You are a helpful assistant. Use the provided context if it is relevant. " +
                "If the context is insufficient, say so briefly before answering.",
    )

    fun render(
        system: String? = null,
        contexts: List<String> = emptyList(),
        messages: List<ChatMessage>,
        template: PromptTemplate = Gemma3,
        labels: Labels = Labels(),
    ): String {
        val sys = (system ?: labels.defaultSystemPrompt).trim()
        val ragBlock = buildRagBlock(contexts, labels)

        return when (template) {
            is Gemma3 -> renderGemma3(sys, ragBlock, messages)
            is Llama3Instruct -> renderLlama3(sys, ragBlock, messages, labels)
            is Plain -> renderPlain(sys, ragBlock, messages, labels)
            QwenChat -> renderQwen(sys, ragBlock, messages, labels)
        }
    }

    private fun buildRagBlock(contexts: List<String>, labels: Labels): String {
        if (contexts.isEmpty()) return ""
        val joined = contexts.joinToString(separator = "\n\n—\n") { it.trim() }
        return "${labels.relevantContext}:\n$joined"
    }

    private fun renderGemma3(system: String, rag: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>system\n")
        sb.append(system)
        sb.append("\n<end_of_turn>\n")

        val (prefix, last) = messages.splitOffLast()
        prefix.forEach { msg ->
            when (msg.role) {
                ChatMessage.Role.System -> {
                    sb.append("<start_of_turn>system\n")
                    sb.append(msg.content.trim())
                    sb.append("\n<end_of_turn>\n")
                }
                ChatMessage.Role.User -> {
                    sb.append("<start_of_turn>user\n")
                    sb.append(msg.content.trim())
                    sb.append("\n<end_of_turn>\n")
                }
                ChatMessage.Role.Assistant -> {
                    sb.append("<start_of_turn>assistant\n")
                    sb.append(msg.content.trim())
                    sb.append("\n<end_of_turn>\n")
                }
            }
        }

        val finalUser = when (last?.role) {
            ChatMessage.Role.User -> last.content.trim()
            else -> ""
        }

        sb.append("<start_of_turn>user\n")
        if (rag.isNotBlank()) {
            sb.append(rag)
            sb.append("\n\n")
        }
        sb.append(finalUser)
        sb.append("\n<end_of_turn>\n")
        sb.append("<start_of_turn>assistant\n")

        return sb.toString()
    }

    private fun renderLlama3(
        system: String,
        rag: String,
        messages: List<ChatMessage>,
        labels: Labels,
    ): String {
        val sb = StringBuilder()
        sb.append("<<SYS>>\n")
        sb.append(system)
        sb.append("\n<</SYS>>\n\n")

        val (prefix, last) = messages.splitOffLast()
        prefix.forEach { msg ->
            when (msg.role) {
                ChatMessage.Role.System -> {
                    sb.append("### ${labels.system.replaceFirstChar { it.uppercase() }}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
                ChatMessage.Role.User -> {
                    sb.append("### ${labels.user.replaceFirstChar { it.uppercase() }}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
                ChatMessage.Role.Assistant -> {
                    sb.append("### ${labels.assistant.replaceFirstChar { it.uppercase() }}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
            }
        }

        val lastUser = if (last?.role == ChatMessage.Role.User) last.content.trim() else ""
        sb.append("### ${labels.user.replaceFirstChar { it.uppercase() }}\n")
        if (rag.isNotBlank()) {
            sb.append(rag).append("\n\n")
        }
        sb.append(lastUser).append("\n\n")
        sb.append("### ${labels.assistant.replaceFirstChar { it.uppercase() }}\n")

        return sb.toString()
    }

    private fun renderQwen(
        system: String,
        rag: String,
        messages: List<ChatMessage>,
        labels: Labels,
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(system)
        sb.append("\n<|im_end|>\n")

        val (prefix, last) = messages.splitOffLast()
        prefix.forEach { msg ->
            val role = when (msg.role) {
                ChatMessage.Role.System -> "system"
                ChatMessage.Role.User -> "user"
                ChatMessage.Role.Assistant -> "assistant"
            }
            sb.append("<|im_start|>$role\n")
            sb.append(msg.content.trim())
            sb.append("\n<|im_end|>\n")
        }

        val lastUser = when (last?.role) {
            ChatMessage.Role.User -> last.content.trim()
            else -> ""
        }

        sb.append("<|im_start|>user\n")
        if (rag.isNotBlank()) {
            sb.append("${labels.relevantContext}:\n")
            sb.append(rag.trim())
            sb.append("\n\n")
        }
        sb.append(lastUser)
        sb.append("\n<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    private fun renderPlain(
        system: String,
        rag: String,
        messages: List<ChatMessage>,
        labels: Labels,
    ): String {
        val sb = StringBuilder()
        sb.append("${labels.system.replaceFirstChar { it.uppercase() }}:\n")
        sb.append(system)
        sb.append("\n\n")

        messages.forEach { msg ->
            val role = when (msg.role) {
                ChatMessage.Role.System -> labels.system
                ChatMessage.Role.User -> labels.user
                ChatMessage.Role.Assistant -> labels.assistant
            }
            sb.append("$role:\n")
            sb.append(msg.content.trim())
            sb.append("\n\n")
        }

        if (rag.isNotBlank()) {
            sb.append("${labels.relevantContext}:\n")
            sb.append(rag)
            sb.append("\n\n")
        }

        sb.append("${labels.assistant.replaceFirstChar { it.uppercase() }}:\n")
        return sb.toString()
    }

    private fun List<ChatMessage>.splitOffLast(): Pair<List<ChatMessage>, ChatMessage?> {
        if (isEmpty()) return emptyList<ChatMessage>() to null
        return subList(0, size - 1) to last()
    }
}
