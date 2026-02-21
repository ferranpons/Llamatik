package com.llamatik.library.platform

import androidx.compose.runtime.Composable

/**
 * Kotlin/Wasm build compiles, but the actual WASM engine interop is not wired yet.
 * The CMake/Emscripten build can still produce llmatk_wasm.mjs + llmatk_wasm.wasm,
 * but Kotlin/Wasm needs a JS glue layer (typed interop) to call into it.
 */
actual object LlamaBridge {

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // You will host gguf files under /models in the web dev server.
        return "/models/$modelFileName"
    }

    actual fun initEmbedModel(modelPath: String): Boolean = false
    actual fun embed(input: String): FloatArray = floatArrayOf()

    actual fun initGenerateModel(modelPath: String): Boolean = false

    actual fun generate(prompt: String): String {
        return "Web/Wasm: llama.cpp engine interop not wired yet."
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String =
        generate("$systemPrompt\n\n$contextBlock\n\n$userPrompt")

    actual fun generateJson(prompt: String, jsonSchema: String?): String = generate(prompt)

    actual fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?
    ): String = generateWithContext(systemPrompt, contextBlock, userPrompt)

    actual fun generateStream(prompt: String, callback: GenStream) {
        callback.onError("Web/Wasm: streaming not wired yet.")
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        generateStream("$systemPrompt\n\n$contextBlock\n\n$userPrompt", callback)
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) =
        generateStream(prompt, callback)

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) = generateStreamWithContext(systemPrompt, contextBlock, userPrompt, callback)

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("Web/Wasm: streaming not wired yet.")
    }

    actual fun shutdown() {}
    actual fun nativeCancelGenerate() {}

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ) {}
}
