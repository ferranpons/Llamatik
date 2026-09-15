package com.llamatik.sdk.chat

import co.touchlab.kermit.Logger
import com.llamatik.core.platform.GenStream
import com.llamatik.core.platform.LlamaBridge
import com.llamatik.core.platform.LlamaSession
import kotlin.math.min

object ChatRunner {

    fun stream(
        session: LlamaSession? = null,
        system: String? = null,
        contexts: List<String> = emptyList(),
        messages: List<ChatMessage>,
        template: PromptTemplate = Gemma3,
        maxTokens: Int = 1024,
        contextTokens: Int = 4096,
        labels: PromptRenderer.Labels = PromptRenderer.Labels(),
        onDelta: (String) -> Unit,
        onComplete: (final: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val windowedMessages = windowHistory(messages, system, contextTokens, maxTokens)
        val nativePrompt = buildNativePrompt(system, contexts, windowedMessages)
        val prompt: String
        val stop: List<String>
        if (nativePrompt != null) {
            prompt = nativePrompt
            stop = emptyList()
        } else {
            prompt = PromptRenderer.render(system, contexts, windowedMessages, template, labels)
            stop = template.stopSequences
        }
        Logger.d { "ChatRunner: turns=${messages.size} promptLen=${prompt.length} usedNative=${nativePrompt != null}" }

        var acc = StringBuilder()
        var done = false
        var tokenCount = 0

        val guard = object : GenStream {
            override fun onDelta(text: String) {
                if (done) return
                val t = text.ifEmpty { return }
                acc.append(t)
                tokenCount++

                if (shouldStop(acc, stop)) {
                    done = true
                    onComplete(trimAtStop(acc.toString(), stop))
                    return
                }

                if (tokenCount >= maxTokens) {
                    done = true
                    onComplete(acc.toString())
                    return
                }

                onDelta(t)
            }

            override fun onComplete() {
                if (!done) {
                    done = true
                    onComplete(acc.toString())
                }
            }

            override fun onError(message: String) {
                if (!done) {
                    done = true
                    onError(message)
                }
            }
        }

        if (session != null) {
            session.stream(prompt, guard)
        } else {
            LlamaBridge.generateStream(prompt, guard)
        }
    }

    private fun buildNativePrompt(
        system: String?,
        contexts: List<String>,
        messages: List<ChatMessage>,
    ): String? {
        val pairs = mutableListOf<Pair<String, String>>()

        if (!system.isNullOrBlank()) {
            pairs += "system" to system.trim()
        }

        val ragBlock = if (contexts.isNotEmpty()) {
            val joined = contexts.joinToString("\n\n—\n") { it.trim() }
            "Relevant context:\n$joined\n\n"
        } else ""

        messages.forEachIndexed { index, msg ->
            val role = when (msg.role) {
                ChatMessage.Role.System -> "system"
                ChatMessage.Role.User -> "user"
                ChatMessage.Role.Assistant -> "assistant"
            }
            val isLastUserMessage = msg.role == ChatMessage.Role.User && index == messages.lastIndex
            val content = if (isLastUserMessage && ragBlock.isNotBlank()) {
                "$ragBlock${msg.content.trim()}"
            } else {
                msg.content.trim()
            }
            pairs += role to content
        }

        return LlamaBridge.applyChatTemplate(pairs, addAssistantPrefix = true)
    }

    private fun windowHistory(
        messages: List<ChatMessage>,
        system: String?,
        contextTokens: Int,
        maxTokens: Int,
    ): List<ChatMessage> {
        val systemMessages = messages.filter { it.role == ChatMessage.Role.System }
        val dialogue = messages.filter { it.role != ChatMessage.Role.System }

        val systemChars = (system?.length ?: 0) + systemMessages.sumOf { it.content.length }
        val reservedTokens = maxTokens + (systemChars / 4) + 256
        val historyBudgetChars = (contextTokens - reservedTokens).coerceAtLeast(512) * 4

        if (dialogue.sumOf { it.content.length } <= historyBudgetChars) return messages

        val pinned = dialogue.take(2)
        val middle = dialogue.drop(2).dropLast(1)
        val last = dialogue.last()

        var budget = historyBudgetChars -
                pinned.sumOf { it.content.length } -
                last.content.length
        budget = budget.coerceAtLeast(0)

        val keptMiddle = mutableListOf<ChatMessage>()
        for (msg in middle.asReversed()) {
            if (budget <= 0) break
            keptMiddle.add(0, msg)
            budget -= msg.content.length
        }

        return systemMessages + pinned + keptMiddle + last
    }

    private fun shouldStop(sb: StringBuilder, stops: List<String>): Boolean {
        if (stops.isEmpty()) return false
        val s = sb.toString()
        val tail = s.takeLast(min(64, s.length))
        return stops.any { tail.endsWith(it) }
    }

    private fun trimAtStop(text: String, stops: List<String>): String {
        var out = text
        for (s in stops) {
            val idx = out.lastIndexOf(s)
            if (idx >= 0) {
                out = out.substring(0, idx)
                break
            }
        }
        return out
    }
}
