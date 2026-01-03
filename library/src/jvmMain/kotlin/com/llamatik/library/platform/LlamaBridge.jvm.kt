@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.library.platform

import androidx.compose.runtime.Composable

actual object LlamaBridge {

    // ----- Native load flag -----

    private val nativeLoaded: Boolean = run {
        try {
            System.loadLibrary("llama_jni")
            println("🖥️ [JVM LlamaBridge] Loaded native library 'llama_jni'")
            true
        } catch (t: Throwable) {
            println("🖥️ [JVM LlamaBridge] Failed to load 'llama_jni': ${t.message}")
            false
        }
    }

    private fun log(msg: String) {
        println("🖥️ [JVM LlamaBridge] $msg")
    }

    // ----- Model path -----

    /**
     * On JVM / desktop we assume [modelFileName] is already an absolute path,
     * e.g. the value coming from LlamatikTempFile.absolutePath().
     */
    @Composable
    actual fun getModelPath(modelFileName: String): String {
        log("getModelPath('$modelFileName')")
        return modelFileName
    }

    // ----- Native declarations (only used when nativeLoaded = true) -----

    private external fun nativeInitModel(modelPath: String): Boolean
    private external fun nativeEmbed(input: String): FloatArray
    private external fun nativeInitGenerateModel(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String): String
    private external fun nativeGenerateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String

    private external fun nativeGenerateStream(prompt: String, callback: GenStream)
    private external fun nativeGenerateWithContextStream(
        system: String,
        context: String,
        user: String,
        callback: GenStream
    )

    private external fun nativeShutdown()
    private external fun nativeCancelGenerateImpl()

    // ----- Prompt helper (same as Android) -----

    private fun buildChatPrompt(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String {
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

    // ----- Public actuals (safe wrappers) -----

    actual fun initModel(modelPath: String): Boolean {
        if (!nativeLoaded) {
            log("initModel('$modelPath') – native library NOT loaded, returning false.")
            return false
        }
        return try {
            nativeInitModel(modelPath)
        } catch (e: UnsatisfiedLinkError) {
            log("initModel: UnsatisfiedLinkError: ${e.message}")
            false
        }
    }

    actual fun embed(input: String): FloatArray {
        if (!nativeLoaded) {
            log("embed(...) – native library NOT loaded, returning empty array.")
            return FloatArray(0)
        }
        return try {
            nativeEmbed(input)
        } catch (e: UnsatisfiedLinkError) {
            log("embed: UnsatisfiedLinkError: ${e.message}")
            FloatArray(0)
        }
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        if (!nativeLoaded) {
            log("initGenerateModel('$modelPath') – native library NOT loaded, returning false.")
            return false
        }
        return try {
            nativeInitGenerateModel(modelPath)
        } catch (e: UnsatisfiedLinkError) {
            log("initGenerateModel: UnsatisfiedLinkError: ${e.message}")
            false
        }
    }

    actual fun generate(prompt: String): String {
        if (!nativeLoaded) {
            log("generate(...) – native library NOT loaded, returning stub text.")
            return buildString {
                append("JVM stub backend – native 'llama_jni' not available.\n\n")
                append("Prompt was:\n")
                append(prompt)
            }
        }
        return try {
            nativeGenerate(prompt)
        } catch (e: UnsatisfiedLinkError) {
            log("generate: UnsatisfiedLinkError: ${e.message}")
            "JVM error: native backend not available."
        }
    }

    actual fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String {
        if (!nativeLoaded) {
            log("generateWithContext(...) – native NOT loaded, using prompt + stub generate().")
            val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
            return generate(prompt)
        }
        return try {
            nativeGenerateWithContext(systemPrompt, contextBlock, userPrompt)
        } catch (e: UnsatisfiedLinkError) {
            log("generateWithContext: UnsatisfiedLinkError: ${e.message}")
            val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
            generate(prompt)
        }
    }

    actual fun generateStream(prompt: String, callback: GenStream) {
        if (!nativeLoaded) {
            log("generateStream(...) – native NOT loaded, emitting stub stream.")
            try {
                callback.onDelta("JVM stub backend – streaming not available (no llama_jni).\n")
                callback.onDelta("Prompt was:\n$prompt")
                callback.onComplete()
            } catch (t: Throwable) {
                callback.onError("JVM stub error: ${t.message}")
            }
            return
        }
        try {
            nativeGenerateStream(prompt, callback)
        } catch (e: UnsatisfiedLinkError) {
            log("generateStream: UnsatisfiedLinkError: ${e.message}")
            callback.onError("JVM native error: ${e.message}")
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
        log("generateStreamWithContext(...) – delegating to generateStream() with built prompt.")
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
        if (!nativeLoaded) {
            log("generateWithContextStream(...) – native NOT loaded, emitting stub callbacks.")
            try {
                onDelta("JVM stub backend – generateWithContextStream not available (no llama_jni).\n")
                val prompt = buildChatPrompt(system, context, user)
                onDelta("Composed prompt was:\n$prompt")
                onDone()
            } catch (t: Throwable) {
                onError("JVM stub error: ${t.message}")
            }
            return
        }

        try {
            val cb = object : GenStream {
                override fun onDelta(text: String) = onDelta(text)
                override fun onComplete() = onDone()
                override fun onError(message: String) = onError(message)
            }
            nativeGenerateWithContextStream(system, context, user, cb)
        } catch (e: UnsatisfiedLinkError) {
            log("generateWithContextStream: UnsatisfiedLinkError: ${e.message}")
            onError("JVM native error: ${e.message}")
        }
    }

    actual fun shutdown() {
        if (!nativeLoaded) {
            log("shutdown() – native NOT loaded, nothing to do.")
            return
        }
        try {
            nativeShutdown()
        } catch (e: UnsatisfiedLinkError) {
            log("shutdown: UnsatisfiedLinkError: ${e.message}")
        }
    }

    actual fun nativeCancelGenerate() {
        if (!nativeLoaded) {
            log("nativeCancelGenerate() – native NOT loaded, nothing to cancel.")
            return
        }
        try {
            nativeCancelGenerateImpl()
        } catch (e: UnsatisfiedLinkError) {
            log("nativeCancelGenerate: UnsatisfiedLinkError: ${e.message}")
        }
    }

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    ) {
        // TODO: implement on iOS/desktop – currently ignored.
    }
}
