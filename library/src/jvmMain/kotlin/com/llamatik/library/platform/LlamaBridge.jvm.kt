@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.library.platform

import androidx.compose.runtime.Composable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object LlamaBridge {

    init {
        System.loadLibrary("llama_jni")
        println("🖥️ [JVM LlamaBridge] Loaded native library 'llama_jni'")
    }

    // ----------------------------
    // JNI: MUST match llama_jni.cpp
    // ----------------------------

    // These must exist as:
    // Java_com_llamatik_library_platform_LlamaBridge_initModel
    // Java_com_llamatik_library_platform_LlamaBridge_embed
    actual external fun initModel(modelPath: String): Boolean
    actual external fun embed(input: String): FloatArray

    /**
     * On JVM / desktop we assume [modelFileName] is already an absolute path,
     * e.g. the value coming from LlamatikTempFile.absolutePath().
     */
    @Composable
    actual fun getModelPath(modelFileName: String): String = modelFileName

    // These must exist as:
    // Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel
    // Java_com_llamatik_library_platform_LlamaBridge_generate
    // Java_com_llamatik_library_platform_LlamaBridge_generateWithContext
    actual external fun initGenerateModel(modelPath: String): Boolean
    actual external fun generate(prompt: String): String
    actual external fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String

    // Streaming JNI exports in your C++ are named with "native..." prefix:
    // Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateStream
    // Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateWithContextStream
    private external fun nativeGenerateStream(prompt: String, callback: GenStream)
    private external fun nativeGenerateWithContextStream(
        system: String,
        context: String,
        user: String,
        callback: GenStream
    )

    // IMPORTANT: match your C++ exactly:
    // Java_com_llamatik_library_platform_LlamaBridge_nativeUpdateGenerationParams
    // (note: GenerationParams, NOT GenerateParams)
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

    // Keep this consistent with the backend you want.
    // If your desktop C++ already wraps prompts, you can simplify.
    // For now we mirror your Android behavior.
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

    // These must exist as:
    // Java_com_llamatik_library_platform_LlamaBridge_shutdown
    // Java_com_llamatik_library_platform_LlamaBridge_nativeCancelGenerate
    actual external fun shutdown()
    actual external fun nativeCancelGenerate()
}