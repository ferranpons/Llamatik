package com.llamatik.library.platform

import com.llamatik.library.platform.LlamaBridge.initGenerateModel


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
     * Returns the value of the "general.finetune" GGUF metadata key, or null if absent.
     * Typical values: "instruct", "chat". A value of "base" (or null) indicates a base model
     * that is not instruction-tuned and may produce poor results in chat/tool-call pipelines.
     */
    fun getModelFinetuneType(): String?

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

    /**
     * Create a new independent inference session backed by the already-loaded generate model.
     * Returns null if the model is not yet loaded.
     * Each session has its own KV cache, so multiple sessions may run concurrently.
     * [name] is a human-readable label stored on the session for display purposes.
     */
    fun createSession(name: String = ""): LlamaSession?

    /**
     * Load the same GGUF as the trunk model a second time as an MTP (Multi-Token Prediction)
     * head context.  Must be called after [initGenerateModel].
     *
     * The MTP head enables speculative drafting: on each trunk decode step the MTP head predicts
     * up to [draftLen] extra tokens which are verified by the trunk in a single batch, giving a
     * throughput boost with no quality loss on supporting models (e.g. Qwen3.5, GLM-4).
     *
     * @param modelPath Path to the same .gguf used for generation (MTP layers are embedded in it).
     * @param draftLen  Maximum draft tokens per step (1–8 recommended).  Pass 0 for default (3).
     * @return true on success, false if the model has no MTP layers or loading fails.
     */
    fun initMtp(modelPath: String, draftLen: Int = 3): Boolean

    /**
     * Release MTP resources.  Generation continues normally using the trunk model only.
     */
    fun shutdownMtp()

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
        gpuLayers: Int = 0,
    )
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}
