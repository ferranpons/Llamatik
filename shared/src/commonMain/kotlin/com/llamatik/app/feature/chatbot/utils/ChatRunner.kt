package com.llamatik.app.feature.chatbot.utils

import co.touchlab.kermit.Logger
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.LlamaSession
import kotlin.math.min

/**
 * High-level chat orchestration:
 * - Renders the prompt using the model’s own embedded chat template when available,
 *   falling back to PromptRenderer for models without one.
 * - Streams tokens via LlamaBridge.generateContinueStream (KV-cache reuse) or generateStream (fresh).
 * - Enforces client-side stop sequences when the fallback renderer is used.
 */
object ChatRunner {

    /**
     * Stream a chat turn.
     *
     * @param session When non-null, generation runs in this isolated session (its own KV cache),
     *   allowing multiple agents to run concurrently. When null, falls back to the global bridge.
     * @param continueKvCache When true and [session] is null, calls [LlamaBridge.generateContinueStream]
     *   so the global KV cache is reused across turns. The C++ layer finds the longest common token
     *   prefix between the new prompt and the cached context, discards only the diverging suffix from
     *   the KV cache, and decodes just the new tokens — skipping re-encoding of shared history.
     *   Set to false (the default) when starting a fresh conversation.
     * @param system Optional system prompt (defaults to a safe helper system).
     * @param contexts RAG passages (already ranked/shortened).
     * @param messages Chat history (last one should be the user turn we’re answering).
     * @param template Fallback template used when the model has no embedded chat template.
     * @param maxTokens Hard guard if your engine doesn’t supply one.
     * @param contextTokens The model’s configured context window in tokens. Used to size the
     *   history window so the prompt never crowds out the generation budget.
     */
    fun stream(
        session: LlamaSession? = null,
        continueKvCache: Boolean = false,
        system: String? = null,
        contexts: List<String> = emptyList(),
        messages: List<ChatMessage>,
        template: PromptTemplate = Gemma3,
        maxTokens: Int = 1024,
        contextTokens: Int = 4096,
        onDelta: (String) -> Unit,
        onComplete: (final: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val windowedMessages = windowHistory(messages, system, contextTokens, maxTokens)
        val nativePrompt = buildNativePrompt(system, contexts, windowedMessages)
        val prompt: String
        val stop: List<String>
        if (nativePrompt != null) {
            prompt = nativePrompt
            stop = emptyList() // model’s EOS token handles termination natively
        } else {
            prompt = PromptRenderer.render(system, contexts, windowedMessages, template)
            stop = template.stopSequences
        }
        Logger.d { "ChatRunner: turns=${messages.size} promptLen=${prompt.length} usedNative=${nativePrompt != null}" }
        Logger.d { "ChatRunner: prompt tail=...${prompt.takeLast(200)}" }

        var acc = StringBuilder()
        var done = false
        var tokenCount = 0

        // Thin wrapper that enforces consistent stop logic above the JNI layer.
        val guard = object : GenStream {
            override fun onDelta(text: String) {
                if (done) return

                val t = text.ifEmpty { return }
                acc.append(t)
                tokenCount++

                // Client-side stop: cheap suffix check
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

        // Let the engine do its thing; we keep the semantics above it.
        when {
            session != null -> session.stream(prompt, guard)
            continueKvCache -> LlamaBridge.generateContinueStream(prompt, guard)
            else -> LlamaBridge.generateStream(prompt, guard)
        }
    }

    /**
     * Tries to render the prompt using the model's embedded Jinja chat template via
     * [LlamaBridge.applyChatTemplate]. Returns null when the model has no embedded template,
     * so the caller can fall back to [PromptRenderer].
     *
     * RAG context is injected as a prefixed block into the last user message content so it
     * appears in the correct position regardless of the model's template structure.
     */
    private fun buildNativePrompt(
        system: String?,
        contexts: List<String>,
        messages: List<ChatMessage>
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

    // Trims history so the prompt fits within the model's context window.
    // Uses a character budget (4 chars ≈ 1 token) to leave room for generation.
    // Always keeps system messages, the first user+assistant pair (topic anchor), and the
    // last user message. Middle turns are dropped oldest-first when the budget is exceeded.
    private fun windowHistory(
        messages: List<ChatMessage>,
        system: String?,
        contextTokens: Int,
        maxTokens: Int
    ): List<ChatMessage> {
        val systemMessages = messages.filter { it.role == ChatMessage.Role.System }
        val dialogue = messages.filter { it.role != ChatMessage.Role.System }

        // Reserve tokens for system prompt, RAG/contexts, generation output, and template overhead
        val systemChars = (system?.length ?: 0) + systemMessages.sumOf { it.content.length }
        val reservedTokens = maxTokens + (systemChars / 4) + 256  // 256 for template tags
        val historyBudgetChars = (contextTokens - reservedTokens).coerceAtLeast(512) * 4

        if (dialogue.sumOf { it.content.length } <= historyBudgetChars) return messages

        // Pinned: first user+assistant pair preserves the topic anchor
        val pinned = dialogue.take(2)
        val middle = dialogue.drop(2).dropLast(1)
        val last = dialogue.last()

        var budget = historyBudgetChars -
                pinned.sumOf { it.content.length } -
                last.content.length
        budget = budget.coerceAtLeast(0)

        // Fill remaining budget from the most recent middle turns backwards
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
        // Only check the last ~64 chars for performance
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