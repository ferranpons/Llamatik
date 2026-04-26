package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object LlamaBridge {
    fun getModelPath(modelFileName: String): String

    fun initEmbedModel(modelPath: String): Boolean

    fun embed(input: String): FloatArray

    fun initGenerateModel(modelPath: String): Boolean

    /** One-shot generation: starts a fresh KV state (like today). */
    fun generate(prompt: String): String

    fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String

    fun generateJson(prompt: String, jsonSchema: String? = null): String

    fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null
    ): String

    fun generateStream(prompt: String, callback: GenStream)

    fun generateStreamWithContext(systemPrompt: String, contextBlock: String, userPrompt: String, callback: GenStream)

    fun generateJsonStream(prompt: String, jsonSchema: String? = null, callback: GenStream)

    fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String? = null,
        callback: GenStream
    )

    fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )

    // ===================== KV session (state) support =====================
    /** Clears KV/session state but keeps the model/context loaded. */
    fun sessionReset(): Boolean

    /** Saves KV/session state to a file path. */
    fun sessionSave(path: String): Boolean

    /** Loads KV/session state from a file path. */
    fun sessionLoad(path: String): Boolean

    /**
     * Continues generation using the existing KV cache.
     * If no session exists yet, behavior is equivalent to a fresh generate().
     */
    fun generateContinue(prompt: String): String

    /**
     * Returns the chat template string embedded in the loaded GGUF model, or null if unavailable.
     */
    fun getModelChatTemplate(): String?

    /**
     * Renders [messages] (list of role to content pairs) into a prompt string using the model's
     * own embedded chat template. Pass [addAssistantPrefix] = true when starting a new generation.
     * Returns null if the model is not loaded or the template is unavailable.
     */
    fun applyChatTemplate(messages: List<Pair<String, String>>, addAssistantPrefix: Boolean): String?

    fun shutdown()

    fun nativeCancelGenerate()

    fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        contextLength: Int,
        numThreads: Int,
        useMmap: Boolean,
        flashAttention: Boolean,
        batchSize: Int,
    )
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}
