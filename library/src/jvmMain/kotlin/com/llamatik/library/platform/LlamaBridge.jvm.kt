@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.library.platform

import androidx.compose.runtime.Composable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object LlamaBridge {

    init {
        System.loadLibrary("llama_jni")
        println("🖥️ [JVM LlamaBridge] Loaded native library 'llama_jni'")
    }

    actual external fun initModel(modelPath: String): Boolean
    actual external fun embed(input: String): FloatArray

    /**
     * On JVM / desktop we assume [modelFileName] is already an absolute path,
     * e.g. the value coming from LlamatikTempFile.absolutePath().
     */
    @Composable
    actual fun getModelPath(modelFileName: String): String = modelFileName

    actual external fun initGenerateModel(modelPath: String): Boolean
    actual external fun generate(prompt: String): String
    actual external fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String

    actual external fun generateJson(prompt: String, jsonSchema: String?): String
    actual external fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?
    ): String

    private external fun nativeGenerateStream(prompt: String, callback: GenStream)
    private external fun nativeGenerateWithContextStream(
        system: String,
        context: String,
        user: String,
        callback: GenStream
    )

    private external fun nativeGenerateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream)
    private external fun nativeGenerateJsonWithContextStream(
        system: String,
        context: String,
        user: String,
        jsonSchema: String?,
        callback: GenStream
    )

    private external fun nativeUpdateGenerationParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    )

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    ) {
        nativeUpdateGenerationParams(temperature, maxTokens, topP, topK, repeatPenalty)
    }

    actual fun generateStream(prompt: String, callback: GenStream) {
        nativeGenerateStream(prompt, callback)
    }

    private fun buildChatPrompt(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        return buildString {
            append("<start_of_turn>system\n")
            append(systemPrompt.trim())
            append("\n<end_of_turn>\n")
            append("<start_of_turn>user\n")
            append("CONTEXT:\n")
            append(contextBlock.trim())
            append("\n\nQUESTION:\n")
            append(userPrompt.trim())
            append("\n<end_of_turn>\n")
            append("<start_of_turn>assistant\n")
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
        generateStream(prompt, callback)
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) {
        nativeGenerateJsonStream(prompt, jsonSchema, callback)
    }

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) {
        nativeGenerateJsonWithContextStream(systemPrompt, contextBlock, userPrompt, jsonSchema, callback)
    }

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cb = object : GenStream {
            override fun onDelta(text: String) = onDelta(text)
            override fun onComplete() = onDone()
            override fun onError(message: String) = onError(message)
        }
        nativeGenerateWithContextStream(system, context, user, cb)
    }

    actual external fun shutdown()
    actual external fun nativeCancelGenerate()
}
