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
 * - Streams tokens from LlamaBridge.generateStream.
 * - Enforces client-side stop sequences when the fallback renderer is used.
 */
object ChatRunner {

    /**
     * Stream a chat turn.
     *
     * @param session When non-null, generation runs in this isolated session (its own KV cache),
     *   allowing multiple agents to run concurrently. When null, falls back to the legacy global
     *   [LlamaBridge.generateStream].
     * @param system Optional system prompt (defaults to a safe helper system).
     * @param contexts RAG passages (already ranked/shortened).
     * @param messages Chat history (last one should be the user turn we’re answering).
     * @param template Fallback template used when the model has no embedded chat template.
     * @param maxTokens Hard guard if your engine doesn’t supply one.
     */
    fun stream(
        session: LlamaSession? = null,
        system: String? = null,
        contexts: List<String> = emptyList(),
        messages: List<ChatMessage>,
        template: PromptTemplate = Gemma3,
        maxTokens: Int = 1024,
        onDelta: (String) -> Unit,
        onComplete: (final: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val nativePrompt = buildNativePrompt(system, contexts, messages)
        val prompt: String
        val stop: List<String>
        if (nativePrompt != null) {
            prompt = nativePrompt
            stop = emptyList() // model’s EOS token handles termination natively
        } else {
            prompt = PromptRenderer.render(system, contexts, messages, template)
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
        if (session != null) {
            session.stream(prompt, guard)
        } else {
            LlamaBridge.generateStream(prompt, guard)
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