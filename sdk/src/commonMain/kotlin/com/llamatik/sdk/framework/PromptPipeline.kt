package com.llamatik.sdk.framework

import com.llamatik.sdk.chat.ChatMessage

sealed interface PromptStage {
    data class SystemStage(val text: String) : PromptStage
    data object MemoryStage : PromptStage
    data class RagStage(val contexts: List<String>) : PromptStage
    data class UserMessageStage(val text: String) : PromptStage
}

class PromptPipeline(private val stages: List<PromptStage>) {

    fun build(
        userMessage: String,
        memory: Memory,
        ragContexts: List<String> = emptyList(),
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        for (stage in stages) {
            when (stage) {
                is PromptStage.SystemStage -> {
                    messages += ChatMessage(ChatMessage.Role.System, stage.text)
                }
                is PromptStage.MemoryStage -> {
                    messages += memory.messages().filter { it.role != ChatMessage.Role.System }
                }
                is PromptStage.RagStage -> {
                    val ctxList = stage.contexts.ifEmpty { ragContexts }
                    if (ctxList.isNotEmpty()) {
                        val ragText = ctxList.joinToString("\n\n---\n\n") { it.trim() }
                        messages += ChatMessage(ChatMessage.Role.System, "Relevant context:\n$ragText")
                    }
                }
                is PromptStage.UserMessageStage -> {
                    val text = stage.text.ifBlank { userMessage }
                    if (text.isNotBlank()) {
                        messages += ChatMessage(ChatMessage.Role.User, text)
                    }
                }
            }
        }

        // Always ensure the final user message is present when no UserMessageStage was added
        val hasUserMessage = messages.any { it.role == ChatMessage.Role.User }
        if (!hasUserMessage && userMessage.isNotBlank()) {
            messages += ChatMessage(ChatMessage.Role.User, userMessage)
        }

        return messages
    }

    companion object {
        val Default: PromptPipeline = PromptPipeline(
            listOf(
                PromptStage.SystemStage(""),
                PromptStage.MemoryStage,
                PromptStage.RagStage(emptyList()),
                PromptStage.UserMessageStage(""),
            )
        )
    }
}
