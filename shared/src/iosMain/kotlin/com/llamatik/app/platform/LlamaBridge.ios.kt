@file:OptIn(ExperimentalNativeApi::class)

package com.llamatik.app.platform

import androidx.compose.runtime.Composable
import com.llamatik.app.platform.llama.llama_embed
import com.llamatik.app.platform.llama.llama_embed_init
import com.llamatik.app.platform.llama.llama_embedding_size
import com.llamatik.app.platform.llama.llama_free_embedding
import kotlinx.cinterop.get
import kotlin.experimental.ExperimentalNativeApi


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

actual object LlamaBridge {
    //private var model: CPointer<llama_model>? = null
    //private var context: CPointer<llama_context>? = null
    private var embeddingSize = 0

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
    //@CName("llama_embed_init")
    //actual external fun initModel(modelPath: String): Boolean

    /*
    actual fun initModel(modelPath: String): Boolean {

        llama_backend_init()

        val modelParams = llama_model_default_params()
        model = llama_model_load_from_file(modelPath, modelParams)
        if (model == null) return false

        val ctxParams = llama_context_default_params()
        ctxParams.embedings = true
        context = llama_new_context_with_model(model, ctxParams)
        if (context == null) return false

        embeddingSize = llama_n_embd(model) // usually 768 for nomic models

        return true
    }

    actual fun embed(input: String): FloatArray {
        val ctx = context ?: error("Model not initialized")
        val maxTokens = 512
        val tokens = UShortArray(maxTokens)
        //val nTokens = llama_tokenize(model, text, tokens.refTo(0), maxTokens, true)
        val nTokens = llama_tokenize(
            llama_model_get_vocab(model),
            input.cstr.toString(),
            input.length,
            tokens,
            maxTokens,
            true, // add_bos
            false  // allow special tokens = false
        )
        if (nTokens <= 0 || nTokens > llama_n_ctx(ctx)) error("Failed to tokenize")

        if (llama_decode(ctx, tokens.refTo(0), nTokens, 0, 1) != 0) {
            error("llama_eval failed")
        }

        val embeddingPtr = llama_get_embeddings(ctx)
        return List(embeddingSize) { i -> embeddingPtr[i] }
        return floatArrayOf()
    }
*/
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
}
