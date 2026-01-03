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

    // ✅ NEW: generation settings
    private external fun nativeUpdateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    )

    // ----- Prompt helpers -----
    //
    // IMPORTANT:
    // - On iOS your wrapper builds a plain prompt ("Context/Question/Answer") and sanitizes.
    // - On JVM, if your native desktop backend ALSO wraps prompts or uses its own template,
    //   injecting <start_of_turn> tags here can cause echo/garbage.
    //
    // So we default to a plain structure matching iOS.

    private fun buildPlainPrompt(
        contextBlock: String,
        userPrompt: String
    ): String {
        val ctx = contextBlock.trim()
        val usr = userPrompt.trim()
        return buildString {
            if (ctx.isNotEmpty()) {
                append("Context:\n")
                append(ctx)
                append("\n\n")
            }
            append("Question:\n")
            append(usr)
            append("\n\nAnswer:\n")
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
            // IMPORTANT: pass-through; don't add chat tags here
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
            log("generateWithContext(...) – native NOT loaded, using plain composed prompt + stub generate().")
            val prompt = buildPlainPrompt(contextBlock, userPrompt)
            return generate(prompt)
        }

        return try {
            // If your native desktop implementation truly supports (system, context, user),
            // keep using it. Otherwise it can ignore systemPrompt internally.
            nativeGenerateWithContext(systemPrompt, contextBlock, userPrompt)
        } catch (e: UnsatisfiedLinkError) {
            log("generateWithContext: UnsatisfiedLinkError: ${e.message}")
            val prompt = buildPlainPrompt(contextBlock, userPrompt)
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
            // IMPORTANT: pass-through; don't inject chat tags here
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
        if (!nativeLoaded) {
            log("generateStreamWithContext(...) – native NOT loaded, emitting stub stream.")
            val prompt = buildPlainPrompt(contextBlock, userPrompt)
            generateStream(prompt, callback)
            return
        }

        // Prefer native context-stream if available, otherwise fall back to composed prompt.
        try {
            nativeGenerateWithContextStream(systemPrompt, contextBlock, userPrompt, callback)
        } catch (e: UnsatisfiedLinkError) {
            log("generateStreamWithContext: UnsatisfiedLinkError: ${e.message} -> fallback to generateStream(prompt)")
            val prompt = buildPlainPrompt(contextBlock, userPrompt)
            generateStream(prompt, callback)
        }
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
                val prompt = buildPlainPrompt(context, user)
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
            // Fallback: compose a plain prompt and stream it
            log("generateWithContextStream: UnsatisfiedLinkError: ${e.message} -> fallback to generateStream(prompt)")
            val prompt = buildPlainPrompt(context, user)
            generateStream(prompt, object : GenStream {
                override fun onDelta(text: String) = onDelta(text)
                override fun onComplete() = onDone()
                override fun onError(message: String) = onError(message)
            })
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
        if (!nativeLoaded) {
            log("updateGenerateParams(...) – native NOT loaded, ignoring. " +
                    "t=$temperature max=$maxTokens topP=$topP topK=$topK rp=$repeatPenalty")
            return
        }
        try {
            nativeUpdateGenerateParams(temperature, maxTokens, topP, topK, repeatPenalty)
            log("updateGenerateParams -> native applied. t=$temperature max=$maxTokens topP=$topP topK=$topK rp=$repeatPenalty")
        } catch (e: UnsatisfiedLinkError) {
            log("updateGenerateParams: UnsatisfiedLinkError: ${e.message}")
        }
    }
}
