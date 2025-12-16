@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.library.platform

import androidx.compose.runtime.Composable

actual object LlamaBridge {
    actual fun initModel(modelPath: String): Boolean {
        return false
    }

    actual fun embed(input: String): FloatArray {
        return floatArrayOf()
    }

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        return ""
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        return false
    }

    actual fun generate(prompt: String): String {
        return ""
    }

    actual fun generateWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String
    ): String {
        TODO("Not yet implemented")
    }

    actual fun shutdown() {
    }

    actual fun generateStream(prompt: String, callback: GenStream) {
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
    }

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
    }

    actual fun nativeCancelGenerate() {
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