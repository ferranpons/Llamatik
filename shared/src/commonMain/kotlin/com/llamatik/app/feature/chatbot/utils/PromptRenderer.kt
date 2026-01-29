package com.llamatik.app.feature.chatbot.utils

import com.llamatik.app.localization.getCurrentLocalization

/**
 * Renders (System + optional RAG context + messages + Assistant prefix) -> single prompt string.
 * No C++ templating — the JNI just gets a plain string to continue from.
 */
object PromptRenderer {
    val localization = getCurrentLocalization()

    /**
     * @param system Optional system message. If null, we provide a conservative default.
     * @param contexts Your retrieved RAG passages (already ranked). Kept short and prefixed.
     * @param messages The chat history in role/content pairs. The last user message should be present.
     * @param template Which surface syntax to use. Default is Gemma3 for your case.
     */
    fun render(
        system: String? = null,
        contexts: List<String> = emptyList(),
        messages: List<ChatMessage>,
        template: PromptTemplate = Gemma3
    ): String {
        val sys = (system ?: localization.defaultSystemPromptRendererMessage).trim()
        val ragBlock = buildRagBlock(contexts)

        return when (template) {
            is Gemma3 -> renderGemma3(sys, ragBlock, messages)
            is Llama3Instruct -> renderLlama3(sys, ragBlock, messages)
            is Plain -> renderPlain(sys, ragBlock, messages)
            QwenChat -> renderQwen(sys, ragBlock, messages)
        }
    }

    private fun buildRagBlock(contexts: List<String>): String {
        if (contexts.isEmpty()) return ""
        // Keep RAG stable and predictable; the label helps any model understand the section.
        val joined = contexts.joinToString(separator = "\n\n—\n") { it.trim() }
        return "${localization.relevantContext}:\n$joined"
    }

    private fun renderGemma3(
        system: String,
        rag: String,
        messages: List<ChatMessage>
    ): String {
        // Gemma 3 prefers <start_of_turn>/<end_of_turn> with explicit role labels.
        // We fold RAG into the first user turn for maximum portability across vendors.
        val sb = StringBuilder()

        // system
        sb.append("<start_of_turn>system\n")
        sb.append(system)
        sb.append("\n<end_of_turn>\n")

        // messages: we will inject RAG before the last user message content
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

        // last user message + RAG (if any)
        val finalUser = when (last?.role) {
            ChatMessage.Role.User -> last.content.trim()
            ChatMessage.Role.System, ChatMessage.Role.Assistant, null -> "" // fallback to empty
        }

        sb.append("<start_of_turn>user\n")
        if (rag.isNotBlank()) {
            sb.append(rag)
            sb.append("\n\n")
        }
        sb.append(finalUser)
        sb.append("\n<end_of_turn>\n")

        // Assistant prefix to cue continuations:
        sb.append("<start_of_turn>assistant\n")

        return sb.toString()
    }

    private fun renderLlama3(
        system: String,
        rag: String,
        messages: List<ChatMessage>
    ): String {
        // A compact instruct/chat hybrid. Similar to many OpenChat/Llama mixes.
        val sb = StringBuilder()
        sb.append("<<SYS>>\n")
        sb.append(system)
        sb.append("\n<</SYS>>\n\n")

        val (prefix, last) = messages.splitOffLast()
        prefix.forEach { msg ->
            when (msg.role) {
                ChatMessage.Role.System -> {
                    sb.append("### ${localization.system.capitalize()}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
                ChatMessage.Role.User -> {
                    sb.append("### ${localization.user.capitalize()}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
                ChatMessage.Role.Assistant -> {
                    sb.append("### ${localization.assistant.capitalize()}\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
            }
        }

        // Last user + RAG
        val lastUser = if (last?.role == ChatMessage.Role.User) last.content.trim() else ""
        sb.append("### ${localization.user.capitalize()}\n")
        if (rag.isNotBlank()) {
            sb.append(rag).append("\n\n")
        }
        sb.append(lastUser).append("\n\n")
        sb.append("### ${localization.assistant.capitalize()}\n") // prefix for assistant completion

        return sb.toString()
    }

    private fun renderQwen(
        system: String,
        rag: String,
        messages: List<ChatMessage>
    ): String {
        val sb = StringBuilder()

        // System
        sb.append("<|im_start|>system\n")
        sb.append(system)
        sb.append("\n<|im_end|>\n")

        val (prefix, last) = messages.splitOffLast()

        // History
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

        // Last user + RAG
        val lastUser = when (last?.role) {
            ChatMessage.Role.User -> last.content.trim()
            else -> ""
        }

        sb.append("<|im_start|>user\n")
        if (rag.isNotBlank()) {
            sb.append("${localization.relevantContext}:\n")
            sb.append(rag.trim())
            sb.append("\n\n")
        }
        sb.append(lastUser)
        sb.append("\n<|im_end|>\n")

        // Assistant prefix
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    private fun renderPlain(
        system: String,
        rag: String,
        messages: List<ChatMessage>
    ): String {
        val sb = StringBuilder()
        sb.append("${localization.system.capitalize()}:\n")
        sb.append(system)
        sb.append("\n\n")

        messages.forEach { msg ->
            val role = when (msg.role) {
                ChatMessage.Role.System -> "System"
                ChatMessage.Role.User -> "User"
                ChatMessage.Role.Assistant -> "Assistant"
            }
            sb.append("$role:\n")
            sb.append(msg.content.trim())
            sb.append("\n\n")
        }

        if (rag.isNotBlank()) {
            sb.append("${localization.relevantContext}:\n")
            sb.append(rag)
            sb.append("\n\n")
        }

        sb.append("${localization.assistant.capitalize()}:\n") // completion prefix
        return sb.toString()
    }

    private fun List<ChatMessage>.splitOffLast(): Pair<List<ChatMessage>, ChatMessage?> {
        if (isEmpty()) return emptyList<ChatMessage>() to null
        val prefix = this.subList(0, size - 1)
        return prefix to this.last()
    }
}