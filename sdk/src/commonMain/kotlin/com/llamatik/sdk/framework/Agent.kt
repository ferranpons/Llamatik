package com.llamatik.sdk.framework

import co.touchlab.kermit.Logger
import com.llamatik.sdk.agent.AgentToolResult
import com.llamatik.sdk.agent.ToolCallParser
import com.llamatik.sdk.chat.ChatMessage
import com.llamatik.sdk.chat.ChatRunner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow

class Agent internal constructor(val config: AgentConfig) {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    val memory: Memory get() = config.memory

    fun stream(
        userMessage: String,
        ragContexts: List<String> = emptyList(),
        languageHint: String? = null,
    ): Flow<AgentStreamChunk> = callbackFlow {
        _events.tryEmit(AgentEvent.TurnStarted(userMessage))

        var workingMessage = userMessage
        val systemPrompt = buildSystemPrompt(languageHint)
        var roundsLeft = config.maxToolRounds
        var finalOutput = ""

        fun currentMessages(): List<ChatMessage> {
            val msgs = config.pipeline.build(
                userMessage = workingMessage,
                memory = config.memory,
                ragContexts = ragContexts,
            )
            return if (msgs.firstOrNull()?.role == ChatMessage.Role.System) {
                msgs
            } else {
                listOf(ChatMessage(ChatMessage.Role.System, systemPrompt)) + msgs
            }
        }

        var keepGoing = true
        while (keepGoing && roundsLeft-- > 0) {
            val messages = currentMessages()
            _events.tryEmit(AgentEvent.Thinking)
            _events.tryEmit(AgentEvent.StreamStarted)

            val acc = StringBuilder()
            var streamDone = false
            var streamError: String? = null

            ChatRunner.stream(
                system = systemPrompt,
                messages = messages,
                template = config.template,
                maxTokens = config.maxTokens,
                contextTokens = config.contextLength,
                onDelta = { chunk ->
                    if (chunk.isEmpty()) return@stream
                    acc.append(chunk)
                    trySend(AgentStreamChunk.Delta(chunk))
                    _events.tryEmit(AgentEvent.StreamDelta(chunk))
                },
                onComplete = { final ->
                    streamDone = true
                    finalOutput = final
                    _events.tryEmit(AgentEvent.StreamCompleted(final))
                },
                onError = { err ->
                    streamError = err
                    _events.tryEmit(AgentEvent.StreamError(err))
                }
            )

            val err = streamError
            if (err != null) {
                trySend(AgentStreamChunk.Failure(err))
                _events.tryEmit(AgentEvent.TurnCompleted(finalOutput))
                keepGoing = false
                break
            }

            if (!streamDone) finalOutput = acc.toString()

            // Tool call detection
            val executor = config.executor
            val rawJson = ToolCallParser.extractFirstJsonBlock(finalOutput)
            val call = rawJson?.let { ToolCallParser.parse(it) }

            if (call != null && executor != null) {
                val inputSummary = call.input.toString().take(200)
                Logger.d("Agent [${config.name}] — tool call: ${call.toolId}")

                trySend(AgentStreamChunk.ToolCallStarted(call.toolId, inputSummary))
                _events.tryEmit(AgentEvent.ToolCallStarted(call.toolId, inputSummary))

                val result = runCatching { executor.execute(call) }.getOrElse {
                    AgentToolResult.Failure(it.message ?: "Unknown error")
                }

                _events.tryEmit(AgentEvent.ToolCallCompleted(call.toolId, result))

                val (summary, success) = when (result) {
                    is AgentToolResult.Success -> result.outputSummary to true
                    is AgentToolResult.Failure -> result.errorMessage to false
                    AgentToolResult.PermissionDenied -> "Permission denied" to false
                    AgentToolResult.Unsupported -> "Tool not supported" to false
                }

                trySend(AgentStreamChunk.ToolResult(call.toolId, summary, success))

                // Inject tool result back into memory and continue inference
                config.memory.add(ChatMessage(ChatMessage.Role.Assistant, finalOutput))
                config.memory.add(ChatMessage(ChatMessage.Role.User, "Tool '${call.toolId}' result: $summary"))
                _events.tryEmit(AgentEvent.MemoryUpdated(config.memory.size()))

                workingMessage = ""
            } else {
                // No tool call → final response
                config.memory.add(ChatMessage(ChatMessage.Role.User, userMessage))
                config.memory.add(ChatMessage(ChatMessage.Role.Assistant, finalOutput))
                _events.tryEmit(AgentEvent.MemoryUpdated(config.memory.size()))
                keepGoing = false
            }
        }

        trySend(AgentStreamChunk.Done(finalOutput))
        _events.tryEmit(AgentEvent.TurnCompleted(finalOutput))
        close()

        awaitClose()
    }

    suspend fun run(
        userMessage: String,
        ragContexts: List<String> = emptyList(),
        languageHint: String? = null,
    ): String {
        val sb = StringBuilder()
        stream(userMessage, ragContexts, languageHint).collect { chunk ->
            when (chunk) {
                is AgentStreamChunk.Delta -> sb.append(chunk.text)
                is AgentStreamChunk.Done -> return@collect
                else -> Unit
            }
        }
        return sb.toString().trim().ifEmpty { "No response generated." }
    }

    fun reset() {
        config.memory.clear()
    }

    private fun buildSystemPrompt(languageHint: String?): String {
        val base = config.systemPrompt.trim()
        val toolBlock = buildToolBlock()
        val langSuffix = if (languageHint != null) "\nYou MUST reply exclusively in $languageHint." else ""
        return buildString {
            append(base)
            if (toolBlock.isNotBlank()) {
                append("\n\n")
                append(toolBlock)
            }
            append(langSuffix)
        }
    }

    private fun buildToolBlock(): String {
        val tools = config.toolRegistry.supported()
        if (tools.isEmpty()) return ""
        return buildString {
            appendLine("You have access to the following tools. When you want to use a tool, output a JSON object in this exact format and nothing else:")
            appendLine("""{"tool": "<tool_id>", "input": {<parameters>}}""")
            appendLine()
            appendLine("Available tools:")
            for (tool in tools) {
                appendLine("- ${tool.id}: ${tool.description}")
            }
        }.trim()
    }
}
