@file:OptIn(ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.llama.llama_embed
import com.llamatik.library.platform.llama.llama_embed_init
import com.llamatik.library.platform.llama.llama_embedding_size
import com.llamatik.library.platform.llama.llama_free_embedding
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual object LlamaBridge {
    @androidx.compose.runtime.Composable
    actual fun getModelPath(modelFileName: String): String {
        return modelFileName
    }

    actual fun initModel(modelPath: String): Boolean {
        return llama_embed_init(modelPath)
    }

    actual fun embed(input: String): FloatArray {
        val raw = llama_embed(input)
        val size = llama_embedding_size()
        val result = FloatArray(size) { i -> raw!![i] }
        llama_free_embedding(raw)
        return result
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        return false
    }

    actual fun generate(prompt: String): String {
        return ""
    }
}