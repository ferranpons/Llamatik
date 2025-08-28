@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import com.llamatik.library.platform.llama.llama_embed
import com.llamatik.library.platform.llama.llama_embed_init
import com.llamatik.library.platform.llama.llama_embedding_size
import com.llamatik.library.platform.llama.llama_free_cstr
import com.llamatik.library.platform.llama.llama_free_embedding
import com.llamatik.library.platform.llama.llama_generate
import com.llamatik.library.platform.llama.llama_generate_init
import com.llamatik.library.platform.llama.llama_generate_with_context
import com.llamatik.library.platform.llama.llama_shutdown
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual object LlamaBridge {

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        return modelFileName
    }

    actual fun initModel(modelPath: String): Boolean {
        return llama_embed_init(modelPath)
    }

    actual fun embed(input: String): FloatArray {
        val raw = llama_embed(input) ?: return FloatArray(0)
        val size = llama_embedding_size()
        return try {
            FloatArray(size) { i -> raw[i] }
        } finally {
            llama_free_embedding(raw)
        }
    }

    actual fun initGenerateModel(modelPath: String) = llama_generate_init(modelPath)

    actual fun generate(prompt: String): String {
        val c = llama_generate(prompt) ?: return ""
        val out = c.toKString()
        llama_free_cstr(c)
        return out
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        val c = llama_generate_with_context(systemPrompt, contextBlock, userPrompt) ?: return ""
        val out = c.toKString()
        llama_free_cstr(c)
        return out
    }

    actual fun shutdown() {
        llama_shutdown()
    }
}