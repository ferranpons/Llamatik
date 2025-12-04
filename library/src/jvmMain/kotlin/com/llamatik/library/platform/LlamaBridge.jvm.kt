@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.library.platform

import androidx.compose.runtime.Composable

actual object LlamaBridge {

    // ---------- Small logging helper ----------

    private fun log(msg: String) {
        println("🖥️ [JVM LlamaBridge] $msg")
    }

    // ---------- Model path ----------

    /**
     * On JVM/desktop we assume [modelFileName] is already a usable path,
     * or the caller knows where the model lives.
     *
     * If you want, you can later change this to resolve something like
     * `~/Llamatik/models/<modelFileName>` or a configurable directory.
     */
    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // For now, just return it as-is.
        log("getModelPath('$modelFileName') → returning unchanged on JVM")
        return modelFileName
    }

    // ---------- Init / embedding ----------

    actual fun initModel(modelPath: String): Boolean {
        log("initModel('$modelPath') called on JVM stub – no native backend wired.")
        // Stub: pretend failure so you can detect that desktop backend isn't enabled.
        return false
    }

    actual fun embed(input: String): FloatArray {
        log("embed(...) called on JVM stub – returning empty embedding.")
        return FloatArray(0)
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        log("initGenerateModel('$modelPath') called on JVM stub – no native backend wired.")
        return false
    }

    // ---------- Prompt helpers (same format as Android) ----------

    /**
     * Mirrors the Android buildChatPrompt so behavior is consistent across platforms.
     */
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

    // ---------- Synchronous generation ----------

    actual fun generate(prompt: String): String {
        log("generate(...) called on JVM stub – returning diagnostic placeholder.")
        return buildString {
            append("JVM stub backend – no local llama.cpp wired.\n\n")
            append("Prompt was:\n")
            append(prompt)
        }
    }

    actual fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String {
        log("generateWithContext(...) called on JVM stub – delegating to generate() with chat prompt.")
        val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
        return generate(prompt)
    }

    // ---------- Streaming generation ----------

    actual fun generateStream(prompt: String, callback: GenStream) {
        log("generateStream(...) called on JVM stub – emitting placeholder and completing.")
        try {
            callback.onDelta("JVM stub backend – streaming is not implemented.\n")
            callback.onDelta("Prompt was:\n$prompt")
            callback.onComplete()
        } catch (t: Throwable) {
            callback.onError("JVM stub error: ${t.message}")
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        log("generateStreamWithContext(...) called on JVM stub – delegating to generateStream().")
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
        log("generateWithContextStream(...) called on JVM stub – emitting placeholder callbacks.")
        try {
            onDelta("JVM stub backend – generateWithContextStream is not implemented.\n")
            val prompt = buildChatPrompt(system, context, user)
            onDelta("Composed prompt was:\n$prompt")
            onDone()
        } catch (t: Throwable) {
            onError("JVM stub error: ${t.message}")
        }
    }

    // ---------- Lifecycle ----------

    actual fun shutdown() {
        log("shutdown() called on JVM stub – nothing to clean up.")
    }

    actual fun nativeCancelGenerate() {
        log("nativeCancelGenerate() called on JVM stub – nothing to cancel.")
    }
}
