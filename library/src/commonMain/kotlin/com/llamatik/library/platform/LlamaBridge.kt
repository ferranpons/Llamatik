package com.llamatik.library.platform

import androidx.compose.runtime.Composable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object LlamaBridge {
    @Composable
    fun getModelPath(modelFileName: String): String

    fun initModel(modelPath: String): Boolean
    fun embed(input: String): FloatArray
    fun initGenerateModel(modelPath: String): Boolean
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
    fun shutdown()
    fun nativeCancelGenerate()
    fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    )
}

interface GenStream {
    fun onDelta(text: String)
    fun onComplete()
    fun onError(message: String)
}
